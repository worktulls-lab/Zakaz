package ru.tarkmull.kitmod;

import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Пицца-меч, коса вампира, левиафан. */
public final class MechanicsListener implements Listener {

    private final KitModPlugin plugin;

    public MechanicsListener(KitModPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Удары: пицца-меч и коса вампира
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = plugin.items().idOf(hand);
        if (id == null) return;

        switch (id) {
            case "pizza_sword" -> pizzaSword(player);
            case "vampire_scythe" -> vampireScythe(player, event.getEntity() instanceof LivingEntity le ? le : null);
            default -> { /* остальные предметы механик не имеют */ }
        }
    }

    private void pizzaSword(Player player) {
        double cd = plugin.items().cfgDouble("pizza_sword", "cooldown", 2.0);
        if (!plugin.cooldowns().tryUse(player, "pizza_sword", cd)) {
            plugin.cooldownMessage(player, "pizza_sword");
            return;
        }
        int gain = plugin.items().cfgInt("pizza_sword", "hunger-per-hit", 2);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + gain));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.4f);
    }

    private void vampireScythe(Player player, LivingEntity victim) {
        double cd = plugin.items().cfgDouble("vampire_scythe", "cooldown", 4.0);
        if (!plugin.cooldowns().tryUse(player, "vampire_scythe", cd)) {
            plugin.cooldownMessage(player, "vampire_scythe");
            return;
        }

        // хил владельцу
        double heal = plugin.items().cfgDouble("vampire_scythe", "heal", 5.0);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20.0 : max.getValue();
        player.setHealth(Math.min(cap, player.getHealth() + heal));
        player.setFoodLevel(Math.min(20, player.getFoodLevel()
                + plugin.items().cfgInt("vampire_scythe", "hunger", 1)));
        player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.7f, 0.7f);

        if (victim == null || victim.equals(player)) return;

        // шанс навсегда отнять здоровье
        double chance = plugin.items().cfgDouble("vampire_scythe", "drain-chance", 1.0);
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble() * 100.0 >= chance) return;

        double amount = plugin.items().cfgDouble("vampire_scythe", "drain-amount", 2.0);
        double minHealth = plugin.items().cfgDouble("vampire_scythe", "drain-min-health", 6.0);
        AttributeInstance vmax = victim.getAttribute(Attribute.MAX_HEALTH);
        if (vmax == null || vmax.getValue() - amount < minHealth) return;

        if (victim instanceof Player target) {
            plugin.health().steal(target, amount);
            plugin.send(target, "hp-stolen", "%hp%", trim(amount));
        } else {
            plugin.health().stealFromMob(victim, amount);
        }
        plugin.send(player, "hp-drained",
                "%hp%", trim(amount), "%target%", victim.getName());
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------
    //  Левиафан: ПКМ -> трезубцы с Верностью
    // ------------------------------------------------------------------
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!plugin.items().is(event.getItem(), "leviathan")) return;

        event.setCancelled(true);

        double cd = plugin.items().cfgDouble("leviathan", "cooldown", 2.0);
        if (!plugin.cooldowns().tryUse(player, "leviathan", cd)) {
            plugin.cooldownMessage(player, "leviathan");
            return;
        }

        int count = Math.max(1, plugin.items().cfgInt("leviathan", "tridents", 1));
        int loyalty = Math.max(0, plugin.items().cfgInt("leviathan", "loyalty-level", 3));
        double speed = plugin.items().cfgDouble("leviathan", "speed", 2.5);
        double spread = plugin.items().cfgDouble("leviathan", "spread", 8.0);
        boolean pickup = plugin.items().cfgBool("leviathan", "pickup", false);

        ItemStack tridentItem = new ItemStack(org.bukkit.Material.TRIDENT);
        if (loyalty > 0) {
            ItemMeta meta = tridentItem.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.LOYALTY, loyalty, true);
                tridentItem.setItemMeta(meta);
            }
        }

        for (int i = 0; i < count; i++) {
            Vector dir = player.getLocation().getDirection().clone().normalize();
            if (count > 1 && spread > 0) {
                double offset = Math.toRadians((i - (count - 1) / 2.0) * spread);
                double cos = Math.cos(offset), sin = Math.sin(offset);
                double x = dir.getX() * cos - dir.getZ() * sin;
                double z = dir.getX() * sin + dir.getZ() * cos;
                dir = new Vector(x, dir.getY(), z).normalize();
            }
            Trident trident = player.launchProjectile(Trident.class, dir.multiply(speed));
            trident.setShooter(player);
            trident.setItem(tridentItem.clone());
            trident.setPickupStatus(pickup
                    ? AbstractArrow.PickupStatus.ALLOWED
                    : AbstractArrow.PickupStatus.CREATIVE_ONLY);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.health().apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().clear(event.getPlayer().getUniqueId());
    }

    static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
