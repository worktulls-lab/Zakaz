package ru.tarkmull.kitmod;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Хранит навсегда отнятое здоровье игроков (health.yml) и применяет его
 * как модификатор атрибута max_health.
 */
public final class HealthStore {

    private final KitModPlugin plugin;
    private final File file;
    private final NamespacedKey modifierKey;
    private final Map<UUID, Double> stolen = new HashMap<>();
    private YamlConfiguration yaml;

    public HealthStore(KitModPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "health.yml");
        this.modifierKey = new NamespacedKey(plugin, "stolen_health");
        load();
    }

    private void load() {
        stolen.clear();
        if (!file.exists()) {
            yaml = new YamlConfiguration();
            return;
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                double v = yaml.getDouble(key, 0);
                if (v > 0) stolen.put(UUID.fromString(key), v);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("health.yml: некорректный UUID '" + key + "'");
            }
        }
    }

    public void save() {
        YamlConfiguration out = new YamlConfiguration();
        stolen.forEach((uuid, v) -> out.set(uuid.toString(), v));
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина.");
            }
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить health.yml: " + e.getMessage());
        }
        yaml = out;
    }

    public double getStolen(UUID uuid) {
        return stolen.getOrDefault(uuid, 0.0);
    }

    /** Отнять hp навсегда. */
    public void steal(Player player, double hp) {
        UUID id = player.getUniqueId();
        stolen.merge(id, hp, Double::sum);
        apply(player);
        save();
    }

    /** Вернуть hp. @return сколько реально вернулось. */
    public double give(UUID uuid, double hp) {
        double cur = getStolen(uuid);
        double back = Math.min(cur, hp);
        double left = cur - back;
        if (left <= 0.0001) {
            stolen.remove(uuid);
        } else {
            stolen.put(uuid, left);
        }
        save();
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) apply(online);
        return back;
    }

    /** Применить модификатор к игроку (вызывается на входе и после изменений). */
    public void apply(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        for (AttributeModifier mod : new java.util.ArrayList<>(inst.getModifiers())) {
            if (modifierKey.equals(mod.getKey())) {
                inst.removeModifier(mod);
            }
        }
        double s = getStolen(player.getUniqueId());
        if (s > 0) {
            inst.addModifier(new AttributeModifier(modifierKey, -s,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        clamp(player);
    }

    private void clamp(LivingEntity entity) {
        AttributeInstance inst = entity.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        if (entity.getHealth() > inst.getValue()) {
            entity.setHealth(Math.max(1.0, inst.getValue()));
        }
    }

    /** Отнять здоровье у не-игрока (действует до его смерти). */
    public void stealFromMob(LivingEntity entity, double hp) {
        AttributeInstance inst = entity.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return;
        double base = inst.getBaseValue();
        inst.setBaseValue(Math.max(1.0, base - hp));
        clamp(entity);
    }

    public void reload() {
        load();
        plugin.getServer().getOnlinePlayers().forEach(this::apply);
    }
}
