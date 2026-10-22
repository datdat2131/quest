package vn.astrax.quest.config;

import org.bukkit.configuration.file.FileConfiguration;
import vn.astrax.quest.storage.DbConfig;
import vn.astrax.quest.time.PeriodCalculator;
import vn.astrax.quest.util.LevelCalculator;

import java.io.File;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Gia tri trong config.yml da duoc doc/validate. */
public final class Settings {
    public final DbConfig db;
    public final ZoneId zone;
    public final PeriodCalculator periods;
    public final LevelCalculator levels;

    public final long flushIntervalTicks;
    public final Set<String> ignoredModes = new HashSet<>();
    public final Set<String> disabledWorlds = new HashSet<>();
    public final boolean actionBar;

    public final boolean fullInventoryPending;
    public final long claimCooldownMs;
    public final String soundComplete;
    public final String soundClaim;
    public final String soundLevelUp;

    public final boolean trackPlaced;
    public final int placedMax;
    public final long placedTtlMs;
    public final long placeCooldownMs;
    public final Set<String> ignoredSpawnReasons = new HashSet<>();
    public final long pvpCooldownMs;
    public final int pvpMaxPerVictim;
    public final long pvpWindowMs;
    public final boolean pvpBlockSameIp;
    public final int pvpMinVictimPlayMinutes;
    public final int afkSeconds;
    public final boolean walkAllowVehicle;
    public final int walkMaxPerSample;

    public final boolean economyEnabled;
    public final String economyFallbackCommand;
    public final String guildPlaceholder;
    public final Set<String> guildNoneValues = new HashSet<>();
    public final int guildRefreshSeconds;
    public final boolean papiEnabled;

    public Settings(FileConfiguration c, File dataFolder, Logger log) {
        boolean mysql = "MYSQL".equalsIgnoreCase(c.getString("database.type", "SQLITE"));
        this.db = new DbConfig(mysql,
                new File(dataFolder, c.getString("database.sqlite-file", "data.db")).toPath(),
                c.getString("database.mysql.host", "localhost"),
                c.getInt("database.mysql.port", 3306),
                c.getString("database.mysql.database", "astraxquest"),
                c.getString("database.mysql.username", "root"),
                c.getString("database.mysql.password", ""),
                c.getInt("database.mysql.pool-size", 4),
                c.getString("database.mysql.properties", "useSSL=false&characterEncoding=utf8&autoReconnect=true"));

        ZoneId z;
        try {
            z = ZoneId.of(c.getString("timezone", "Asia/Ho_Chi_Minh"));
        } catch (DateTimeException e) {
            log.warning("timezone khong hop le, dung Asia/Ho_Chi_Minh");
            z = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        this.zone = z;
        DayOfWeek dow;
        try {
            dow = DayOfWeek.valueOf(c.getString("reset.weekly-day", "MONDAY").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("reset.weekly-day khong hop le, dung MONDAY");
            dow = DayOfWeek.MONDAY;
        }
        this.periods = new PeriodCalculator(z, c.getInt("reset.daily-hour", 0), dow,
                c.getInt("reset.weekly-hour", 0), c.getInt("reset.monthly-day", 1));
        this.levels = new LevelCalculator(c.getLong("levels.base", 100), c.getLong("levels.growth", 50),
                c.getInt("levels.max-level", 100));

        this.flushIntervalTicks = Math.max(5, c.getInt("progress.flush-interval-seconds", 30)) * 20L;
        upper(c.getStringList("progress.ignored-gamemodes"), ignoredModes);
        disabledWorlds.addAll(c.getStringList("progress.disabled-worlds"));
        this.actionBar = c.getBoolean("progress.action-bar", true);

        this.fullInventoryPending = "PENDING".equalsIgnoreCase(c.getString("rewards.full-inventory", "DROP"));
        this.claimCooldownMs = Math.max(0, c.getLong("rewards.claim-cooldown-ms", 400));
        this.soundComplete = c.getString("rewards.sounds.complete", "entity.player.levelup");
        this.soundClaim = c.getString("rewards.sounds.claim", "entity.experience_orb.pickup");
        this.soundLevelUp = c.getString("rewards.sounds.level-up", "ui.toast.challenge_complete");

        this.trackPlaced = c.getBoolean("anti-cheat.placed-blocks.enabled", true);
        this.placedMax = c.getInt("anti-cheat.placed-blocks.max-tracked", 200000);
        this.placedTtlMs = c.getLong("anti-cheat.placed-blocks.ttl-minutes", 1440) * 60_000L;
        this.placeCooldownMs = c.getLong("anti-cheat.placed-blocks.place-cooldown-seconds", 3) * 1000L;
        upper(c.getStringList("anti-cheat.ignored-spawn-reasons"), ignoredSpawnReasons);
        this.pvpCooldownMs = c.getLong("anti-cheat.pvp.victim-cooldown-seconds", 300) * 1000L;
        this.pvpMaxPerVictim = c.getInt("anti-cheat.pvp.max-kills-per-victim", 3);
        this.pvpWindowMs = c.getLong("anti-cheat.pvp.window-minutes", 60) * 60_000L;
        this.pvpBlockSameIp = c.getBoolean("anti-cheat.pvp.block-same-ip", true);
        this.pvpMinVictimPlayMinutes = c.getInt("anti-cheat.pvp.min-victim-playtime-minutes", 30);
        this.afkSeconds = Math.max(10, c.getInt("anti-cheat.activity.afk-seconds", 120));
        this.walkAllowVehicle = c.getBoolean("anti-cheat.activity.walk-allow-vehicle", false);
        this.walkMaxPerSample = Math.max(5, c.getInt("anti-cheat.activity.walk-max-blocks-per-sample", 60));

        this.economyEnabled = c.getBoolean("integrations.economy.enabled", true);
        this.economyFallbackCommand = c.getString("integrations.economy.fallback-command", "");
        this.guildPlaceholder = c.getString("integrations.guild.placeholder", "%guild_name%");
        for (String s : c.getStringList("integrations.guild.none-values")) guildNoneValues.add(s.toLowerCase(Locale.ROOT));
        this.guildRefreshSeconds = Math.max(5, c.getInt("integrations.guild.refresh-seconds", 30));
        this.papiEnabled = c.getBoolean("integrations.placeholderapi", true);
    }

    private static void upper(List<String> in, Set<String> out) {
        for (String s : in) out.add(s.toUpperCase(Locale.ROOT));
    }
}
