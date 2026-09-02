package ru.tarkmull.kitmod;

import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * "Шаблон для лавы" и "шаблон для лавы с лавой" — кастомные блоки на базе нотного блока.
 * Состояния: instrument=custom_head, note=0 (пустой) / note=1 (с лавой), powered=false.
 * Ресурспак подменяет модели этих состояний (assets/minecraft/blockstates/note_block.json).
 */
public final class LavaMoldListener implements Listener {

    public static final String EMPTY = "lava_mold";
    public static final String FILLED = "lava_mold_filled";

    private static final Instrument MARK = Instrument.CUSTOM_HEAD;
    private static final int NOTE_EMPTY = 0;
    private static final int NOTE_FILLED = 1;

    private final KitModPlugin plugin;

    public LavaMoldListener(KitModPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Определение блока
    // ------------------------------------------------------------------
    /** @return id кастомного блока или null. */
    public static String moldId(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK) return null;
        BlockData data = block.getBlockData();
        if (!(data instanceof NoteBlock nb)) return null;
        if (nb.getInstrument() != MARK || nb.isPowered()) return null;
        int note = nb.getNote().getId();
        if (note == NOTE_EMPTY) return EMPTY;
        if (note == NOTE_FILLED) return FILLED;
        return null;
    }

    private static NoteBlock dataFor(String id) {
        NoteBlock nb = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        nb.setInstrument(MARK);
        nb.setNote(new Note(FILLED.equals(id) ? NOTE_FILLED : NOTE_EMPTY));
        nb.setPowered(false);
        return nb;
    }

    // ------------------------------------------------------------------
    //  Установка
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = plugin.items().idOf(event.getItemInHand());
        if (!EMPTY.equals(id) && !FILLED.equals(id)) return;
        Block block = event.getBlockPlaced();
        // на следующий тик — чтобы ванильное обновление состояния уже прошло
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() == Material.NOTE_BLOCK) {
                block.setBlockData(dataFor(id), false);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Заливка / вычерпывание лавы
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = moldId(event.getClickedBlock());
        if (id == null) return;

        // никакой настройки нотного блока и никакой установки лавы рядом
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack hand = event.getItem();
        Material tool = hand == null ? Material.AIR : hand.getType();

        if (EMPTY.equals(id) && tool == Material.LAVA_BUCKET) {
            block.setBlockData(dataFor(FILLED), false);
            boolean consume = plugin.items().cfgBool(EMPTY, "consume-bucket", false);
            replaceHand(player, consume ? null : new ItemStack(Material.BUCKET));
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 1f);
        } else if (FILLED.equals(id) && tool == Material.BUCKET
                && plugin.items().cfgBool(FILLED, "allow-scoop", true)) {
            block.setBlockData(dataFor(EMPTY), false);
            replaceHand(player, new ItemStack(Material.LAVA_BUCKET));
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1f, 1f);
        }
    }

    private void replaceHand(Player player, ItemStack replacement) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            if (replacement != null) {
                player.getInventory().addItem(replacement).values()
                        .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        } else {
            player.getInventory().setItemInMainHand(replacement);
        }
    }

    // ------------------------------------------------------------------
    //  Ломание — выпадает кастомный предмет
    // ------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        String id = moldId(event.getBlock());
        if (id == null) return;
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        ItemStack drop = plugin.items().create(id, 1);
        if (drop != null) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.2, 0.5), drop);
        }
    }

    // ------------------------------------------------------------------
    //  Защита состояния блока
    // ------------------------------------------------------------------
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (moldId(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (moldId(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> moldId(b) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> moldId(b) != null)) {
            event.setCancelled(true);
        }
    }
}
