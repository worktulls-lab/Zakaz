package ru.tarkmull.kitmod;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class KitModPlugin extends JavaPlugin {

    private ItemRegistry items;
    private Cooldowns cooldowns;
    private HealthStore health;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.items = new ItemRegistry(this);
        this.cooldowns = new Cooldowns();
        this.health = new HealthStore(this);

        getServer().getPluginManager().registerEvents(new MechanicsListener(this), this);
        getServer().getPluginManager().registerEvents(new LavaMoldListener(this), this);

        Commands executor = new Commands(this);
        for (String name : new String[]{"kmgive", "hpgive", "kitmod"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd == null) {
                getLogger().severe("Команда " + name + " не объявлена в plugin.yml!");
                continue;
            }
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getOnlinePlayers().forEach(health::apply);
        getLogger().info("KitMod включён. Предметов: " + ItemRegistry.IDS.size());
    }

    @Override
    public void onDisable() {
        if (health != null) health.save();
    }

    public ItemRegistry items() {
        return items;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public HealthStore health() {
        return health;
    }

    public void reloadAll() {
        reloadConfig();
        health.reload();
    }

    // ------------------------------------------------------------------
    //  Сообщения
    // ------------------------------------------------------------------
    public void send(CommandSender to, String key, String... replacements) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) return;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        to.sendMessage(Msg.c(prefix + raw));
    }

    public void cooldownMessage(Player player, String itemId) {
        double left = cooldowns.left(player, itemId);
        if (left <= 0) return;
        String raw = getConfig().getString("messages.cooldown", "");
        if (raw.isEmpty()) return;
        player.sendActionBar(Msg.c(raw.replace("%time%", String.valueOf(left))));
    }
}
