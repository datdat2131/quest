package vn.astrax.quest;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import vn.astrax.quest.command.QuestCommand;
import vn.astrax.quest.config.Messages;
import vn.astrax.quest.config.QuestLoader;
import vn.astrax.quest.config.Settings;
import vn.astrax.quest.config.TitleManager;
import vn.astrax.quest.gui.MenuManager;
import vn.astrax.quest.hook.AQExpansion;
import vn.astrax.quest.hook.BridgeManager;
import vn.astrax.quest.hook.EconomyHook;
import vn.astrax.quest.hook.GuildHook;
import vn.astrax.quest.listener.QuestListener;
import vn.astrax.quest.service.ActivityTracker;
import vn.astrax.quest.service.AntiCheat;
import vn.astrax.quest.service.ClaimService;
import vn.astrax.quest.service.ProfileService;
import vn.astrax.quest.service.ProgressService;
import vn.astrax.quest.service.QuestManager;
import vn.astrax.quest.storage.Database;
import vn.astrax.quest.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class AstraxQuestPlugin extends JavaPlugin {
    private static AstraxQuestPlugin instance;

    /** {tai nguyen trong jar (khong dung thu muc con), duong dan trong plugins/AstraxQuest/}. */
    private static final String[][] RESOURCES = {
            {"config.yml", "config.yml"}, {"messages.yml", "messages.yml"}, {"gui.yml", "gui.yml"},
            {"titles.yml", "titles.yml"}, {"events.yml", "events.yml"}, {"integrations.yml", "integrations.yml"},
            {"quest-daily.yml", "quests/daily.yml"}, {"quest-weekly.yml", "quests/weekly.yml"},
            {"quest-story.yml", "quests/story.yml"}, {"quest-guild.yml", "quests/guild.yml"},
            {"quest-pvp.yml", "quests/pvp.yml"}, {"quest-event.yml", "quests/event.yml"}};

    private volatile Settings settings;
    private Messages messages;
    private TitleManager titles;
    private Database database;
    private Storage storage;
    private QuestManager quests;
    private ProgressService progress;
    private ClaimService claims;
    private ProfileService profiles;
    private AntiCheat antiCheat;
    private EconomyHook economy;
    private GuildHook guilds;
    private MenuManager menus;
    private BridgeManager bridges;
    private ActivityTracker activity;
    private PlaceholderExpansion expansion;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public static AstraxQuestPlugin instance() {
        return instance;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public TitleManager titles() {
        return titles;
    }

    public Storage storage() {
        return storage;
    }

    public QuestManager quests() {
        return quests;
    }

    public ProgressService progress() {
        return progress;
    }

    public ClaimService claims() {
        return claims;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public AntiCheat antiCheat() {
        return antiCheat;
    }

    public EconomyHook economy() {
        return economy;
    }

    public GuildHook guilds() {
        return guilds;
    }

    public MenuManager menus() {
        return menus;
    }

    /** Chep file mau tu jar ra thu muc plugin; thieu file trong jar chi canh bao chu khong lam sap plugin. */
    private void saveDefault(String resource, String target) {
        File out = new File(getDataFolder(), target);
        if (out.exists()) return;
        try (InputStream in = getResource(resource)) {
            if (in == null) {
                getLogger().severe("File JAR thieu tai nguyen '" + resource + "'. Hay build lai bang Maven (mvn clean package) "
                        + "va dung file target/AstraxQuest-*.jar (khong dung file 'original-').");
                return;
            }
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.copy(in, out.toPath());
        } catch (IOException e) {
            getLogger().severe("Khong tao duoc " + out + ": " + e);
        }
    }

    /** Chay tren luong chinh (an toan khi plugin dang tat). */
    public void sync(Runnable r) {
        if (!isEnabled()) return;
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(this, r);
    }

    @Override
    public void onEnable() {
        instance = this;
        for (String[] res : RESOURCES) saveDefault(res[0], res[1]);
        reloadConfig();
        settings = new Settings(getConfig(), getDataFolder(), getLogger());

        messages = new Messages(this);
        titles = new TitleManager(this);
        menus = new MenuManager(this);
        economy = new EconomyHook(this);
        guilds = new GuildHook(this);
        antiCheat = new AntiCheat(this);
        bridges = new BridgeManager(this);
        quests = new QuestManager(this);
        progress = new ProgressService(this);
        claims = new ClaimService(this);
        profiles = new ProfileService(this);
        activity = new ActivityTracker(this);

        try {
            database = new Database(settings.db, getLogger());
            storage = new Storage(database, () -> settings.levels);
            storage.init();
        } catch (Exception e) {
            getLogger().severe("Khong the khoi tao co so du lieu: " + e);
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Co so du lieu: " + (settings.db.mysql() ? "MySQL" : "SQLite") + " san sang.");

        reloadAll();

        Bukkit.getPluginManager().registerEvents(new QuestListener(this), this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(activity, this);

        PluginCommand cmd = getCommand("quest");
        if (cmd != null) {
            QuestCommand qc = new QuestCommand(this);
            cmd.setExecutor(qc);
            cmd.setTabCompleter(qc);
        }

        if (settings.papiEnabled && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new AQExpansion(this);
            expansion.register();
            getLogger().info("Da dang ky PlaceholderAPI expansion %astraxquest_*%");
        }

        // Bridge can cac plugin khac da bat xong -> dang ky o tick dau tien
        Bukkit.getScheduler().runTask(this, () -> bridges.reload());
        for (Player p : Bukkit.getOnlinePlayers()) progress.load(p);
        getLogger().info("AstraxQuest " + getPluginMeta().getVersion() + " da bat: " + quests.all().size() + " nhiem vu.");
    }

    /** Nap lai cau hinh (khong doi duoc cau hinh database khi dang chay). */
    public void reloadAll() {
        reloadConfig();
        settings = new Settings(getConfig(), getDataFolder(), getLogger());
        messages.reload();
        titles.reload();
        menus.reload();
        economy.reload();
        antiCheat.reload();
        QuestLoader.Result r = QuestLoader.load(getDataFolder(), settings.zone, getLogger());
        quests.reload(r, settings.periods);
        if (r.quests().isEmpty()) {
            getLogger().warning("Khong co nhiem vu nao duoc nap! Kiem tra thu muc plugins/AstraxQuest/quests/ (xoa thu muc de plugin tao lai file mau).");
        }
        getLogger().info("Da nap " + r.quests().size() + " nhiem vu, " + r.events().size() + " su kien, "
                + titles.all().size() + " danh hieu.");
        if (bridges != null && isEnabled() && progress != null && !tasks.isEmpty()) bridges.reload();
        scheduleTasks();
    }

    private void scheduleTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> progress.flushAll(false),
                settings.flushIntervalTicks, settings.flushIntervalTicks));
        long g = settings.guildRefreshSeconds * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) progress.refreshGuild(p);
        }, g, g));
        long a = ActivityTracker.SAMPLE_SECONDS * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, activity::tick, a, a));
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        if (bridges != null) bridges.unregister();
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Exception ignored) {
                // bo qua
            }
        }
        if (progress != null) progress.flushAll(true);
        if (database != null) database.close();
        instance = null;
    }
}
