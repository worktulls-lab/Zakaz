package ru.tarkmull.kitmod;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /kmgive, /hpgive, /kitmod */
public final class Commands implements CommandExecutor, TabCompleter {

    private final KitModPlugin plugin;

    public Commands(KitModPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "kmgive" -> kmgive(sender, args);
            case "hpgive" -> hpgive(sender, args);
            case "kitmod" -> kitmod(sender, args);
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    private boolean kmgive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kitmod.give")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.c("&7Использование: &f/kmgive <ник> <предмет> [кол-во]"));
            sender.sendMessage(Msg.c("&7Предметы: &f" + String.join(", ", ItemRegistry.IDS)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Msg.c("&cИгрок &f" + args[0] + " &cне в сети."));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!plugin.items().exists(id)) {
            sender.sendMessage(Msg.c("&cНеизвестный предмет: &f" + id));
            sender.sendMessage(Msg.c("&7Доступно: &f" + String.join(", ", ItemRegistry.IDS)));
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Msg.c("&cКоличество должно быть числом."));
                return true;
            }
        }

        int given = 0;
        int max = plugin.items().create(id, 1).getMaxStackSize();
        while (given < amount) {
            int part = Math.min(max, amount - given);
            ItemStack stack = plugin.items().create(id, part);
            target.getInventory().addItem(stack).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            given += part;
        }
        sender.sendMessage(Msg.c("&aВыдано &f" + amount + "x " + id + " &aигроку &f" + target.getName()));
        return true;
    }

    // ------------------------------------------------------------------
    private boolean hpgive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kitmod.hpgive")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Msg.c("&7Использование: &f/hpgive <ник> <хп>"));
            return true;
        }
        UUID uuid;
        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
            if (off == null) {
                sender.sendMessage(Msg.c("&cИгрок &f" + name + " &cне найден (не заходил на сервер)."));
                return true;
            }
            uuid = off.getUniqueId();
        }
        double hp;
        try {
            hp = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Msg.c("&cКоличество HP должно быть числом."));
            return true;
        }
        if (hp <= 0) {
            sender.sendMessage(Msg.c("&cКоличество HP должно быть больше нуля."));
            return true;
        }
        double stolen = plugin.health().getStolen(uuid);
        if (stolen <= 0) {
            sender.sendMessage(Msg.c("&7У игрока &f" + name + " &7ничего не отнято."));
            return true;
        }
        double back = plugin.health().give(uuid, hp);
        plugin.send(sender, "hp-restored",
                "%hp%", MechanicsListener.trim(back), "%player%", name);
        double left = plugin.health().getStolen(uuid);
        sender.sendMessage(Msg.c("&7Осталось отнято: &f" + MechanicsListener.trim(left) + " HP"));
        return true;
    }

    // ------------------------------------------------------------------
    private boolean kitmod(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kitmod.admin")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(Msg.c("&aКонфиг перезагружен."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(Msg.c("&7Предметы: &f" + String.join(", ", ItemRegistry.IDS)));
            return true;
        }
        sender.sendMessage(Msg.c("&8--- &cKitMod &8---"));
        sender.sendMessage(Msg.c("&f/kmgive <ник> <предмет> [кол-во]"));
        sender.sendMessage(Msg.c("&f/hpgive <ник> <хп> &7— вернуть отнятое здоровье"));
        sender.sendMessage(Msg.c("&f/kitmod reload|list"));
        return true;
    }

    // ------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("kitmod")) {
            if (args.length == 1) {
                for (String s : List.of("reload", "list")) {
                    if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
                }
            }
            return out;
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) out.add(pl.getName());
            }
            return out;
        }
        if (args.length == 2 && cmd.equals("kmgive")) {
            return plugin.items().idsStartingWith(args[1]);
        }
        if (args.length == 2 && cmd.equals("hpgive")) {
            return List.of("2", "4", "10", "20");
        }
        return out;
    }
}
