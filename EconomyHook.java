package vn.astrax.quest.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import vn.astrax.quest.AstraxQuestPlugin;

import java.util.UUID;

/** Tich hop he thong kinh te qua Vault (EssentialsX, CMI, XConomy, ...). */
public final class EconomyHook {
    private final AstraxQuestPlugin plugin;
    private Economy economy;

    public EconomyHook(AstraxQuestPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        economy = null;
        if (!plugin.settings().economyEnabled) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            plugin.getLogger().warning("Khong co Vault: phan thuong tien se dung 'fallback-command' neu duoc cau hinh.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("Vault co mat nhung chua co plugin kinh te nao dang ky.");
            return;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Da ket noi kinh te: " + economy.getName());
    }

    public boolean available() {
        return economy != null;
    }

    /** Phai goi tren luong chinh. */
    public boolean deposit(UUID uuid, String name, double amount) {
        if (amount <= 0) return true;
        try {
            if (economy != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                return economy.depositPlayer(op, amount).transactionSuccess();
            }
            String cmd = plugin.settings().economyFallbackCommand;
            if (cmd != null && !cmd.isBlank() && name != null) {
                String c = cmd.replace("{player}", name).replace("{amount}", String.valueOf(amount));
                if (c.startsWith("/")) c = c.substring(1);
                return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Loi khi cong tien cho " + name + ": " + e);
        }
        return false;
    }
}
