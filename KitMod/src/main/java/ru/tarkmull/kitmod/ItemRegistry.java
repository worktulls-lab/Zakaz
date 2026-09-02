package ru.tarkmull.kitmod;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Реестр кастомных предметов.
 * Модель берётся из ресурспака через компонент minecraft:item_model (kitmod:&lt;id&gt;).
 */
public final class ItemRegistry {

    /** Пространство имён ресурспака — должно совпадать с папкой assets/&lt;NS&gt;. */
    public static final String NAMESPACE = "kitmod";

    /** Все id предметов плагина. */
    public static final List<String> IDS = List.of(
            // с механиками
            "pizza", "pizza_sword", "leviathan", "vampire_scythe",
            "lava_mold", "lava_mold_filled",
            // декоративные
            "legendary_sword", "lava_crystal", "reinforced_string", "dragon_ingot",
            "ender_ingot", "blood_ingot", "lava_ingot", "ruby_diamond",
            "rare_glove", "steel", "master_redstone"
    );

    private final KitModPlugin plugin;
    private final NamespacedKey idKey;
    private static Method setItemModel;
    private static boolean modelWarned = false;

    static {
        try {
            setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
        } catch (NoSuchMethodException ignored) {
            setItemModel = null;
        }
    }

    public ItemRegistry(KitModPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    public NamespacedKey idKey() {
        return idKey;
    }

    public boolean exists(String id) {
        return IDS.contains(id.toLowerCase(Locale.ROOT));
    }

    /** id кастомного предмета, или null если это обычный предмет. */
    public String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean is(ItemStack stack, String id) {
        return id.equals(idOf(stack));
    }

    private ConfigurationSection section(String id) {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("items");
        ConfigurationSection s = items == null ? null : items.getConfigurationSection(id);
        if (s == null) {
            plugin.getLogger().warning("В config.yml нет секции items." + id + " — использую значения по умолчанию.");
        }
        return s;
    }

    public double cfgDouble(String id, String path, double def) {
        ConfigurationSection s = section(id);
        return s == null ? def : s.getDouble(path, def);
    }

    public int cfgInt(String id, String path, int def) {
        ConfigurationSection s = section(id);
        return s == null ? def : s.getInt(path, def);
    }

    public boolean cfgBool(String id, String path, boolean def) {
        ConfigurationSection s = section(id);
        return s == null ? def : s.getBoolean(path, def);
    }

    /** Создать кастомный предмет. */
    public ItemStack create(String id, int amount) {
        id = id.toLowerCase(Locale.ROOT);
        if (!exists(id)) return null;
        ConfigurationSection s = section(id);

        Material mat = Material.STICK;
        String matName = s == null ? null : s.getString("material");
        if (matName != null) {
            Material m = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (m == null) {
                plugin.getLogger().warning("Неизвестный материал '" + matName + "' у предмета " + id + ", беру STICK.");
            } else {
                mat = m;
            }
        }

        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        // название
        String name = s == null ? id : s.getString("name", id);
        meta.displayName(Msg.item(name));

        // лор
        List<String> lore = s == null ? List.of() : s.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Msg.item(l));
            meta.lore(lines);
        }

        // метка предмета
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);

        // 3D-модель из ресурспака
        applyModel(meta, new NamespacedKey(NAMESPACE, id));

        // прочность
        if (s != null && s.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        // характеристики оружия
        if (s != null && s.contains("attack-damage")) {
            double dmg = s.getDouble("attack-damage", 7.0);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_damage"), dmg - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (s != null && s.contains("attack-speed")) {
            double spd = s.getDouble("attack-speed", 1.6);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_speed"), spd - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }

        // еда
        if (s != null && s.contains("nutrition")) {
            FoodComponent food = meta.getFood();
            food.setNutrition(s.getInt("nutrition", 6));
            food.setSaturation((float) s.getDouble("saturation", 6.0));
            food.setCanAlwaysEat(s.getBoolean("always-edible", false));
            meta.setFood(food);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private void applyModel(ItemMeta meta, NamespacedKey key) {
        if (setItemModel == null) {
            if (!modelWarned) {
                modelWarned = true;
                plugin.getLogger().warning("ItemMeta#setItemModel недоступен в этой версии сервера — "
                        + "3D-модели работать не будут. Нужен Paper 1.21.4+.");
            }
            return;
        }
        try {
            setItemModel.invoke(meta, key);
        } catch (Exception e) {
            if (!modelWarned) {
                modelWarned = true;
                plugin.getLogger().warning("Не удалось выставить item_model: " + e.getMessage());
            }
        }
    }

    public List<String> idsStartingWith(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return IDS.stream().filter(i -> i.startsWith(p)).sorted().toList();
    }

    public static List<String> all() {
        return new ArrayList<>(Arrays.asList(IDS.toArray(new String[0])));
    }
}
