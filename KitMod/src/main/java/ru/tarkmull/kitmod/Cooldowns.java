package ru.tarkmull.kitmod;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Перезарядки предметов: ключ = uuid игрока + id предмета. */
public final class Cooldowns {

    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    /** @return true если предмет готов (и перезарядка запущена заново). */
    public boolean tryUse(Player player, String id, double seconds) {
        if (seconds <= 0) return true;
        long now = System.currentTimeMillis();
        Map<String, Long> map = data.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        Long until = map.get(id);
        if (until != null && until > now) return false;
        map.put(id, now + (long) (seconds * 1000L));
        return true;
    }

    /** Сколько секунд осталось (округлено вверх до 0.1). */
    public double left(Player player, String id) {
        Map<String, Long> map = data.get(player.getUniqueId());
        if (map == null) return 0;
        Long until = map.get(id);
        if (until == null) return 0;
        long ms = until - System.currentTimeMillis();
        return ms <= 0 ? 0 : Math.round(ms / 100.0) / 10.0;
    }

    public void clear(UUID uuid) {
        data.remove(uuid);
    }
}
