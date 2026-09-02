# KitMod — Paper 1.21.4

Плагин + ресурспак: 17 кастомных предметов с 3D-моделями из Blockbench.
Ставится **только на сервер**, игрокам ничего устанавливать не нужно —
ресурспак раздаётся сервером.

---

## Установка

1. **Плагин.** Положи `KitMod-1.0.0.jar` в `plugins/`, перезапусти сервер.
2. **Ресурспак.** Заархивируй папку `resourcepack` **в zip так, чтобы `pack.mcmeta`
   лежал в корне архива** (не папка внутри папки), залей куда-нибудь
   (Dropbox / GitHub Releases / свой хостинг) и пропиши в `server.properties`:

   ```properties
   resource-pack=https://ссылка/на/KitModPack.zip
   resource-pack-sha1=<sha1 архива>
   require-resource-pack=true
   ```

   SHA-1 считается так: `sha1sum KitModPack.zip`.

Требуется **Paper 1.21.4+ и Java 21**. На Spigot 3D-модели работать не будут
(нужен `ItemMeta#setItemModel`).

---

## Команды

| Команда | Права | Что делает |
|---|---|---|
| `/kmgive <ник> <предмет> [кол-во]` | `kitmod.give` | выдать кастомный предмет |
| `/hpgive <ник> <хп>` | `kitmod.hpgive` | вернуть навсегда отнятое здоровье |
| `/kitmod reload` | `kitmod.admin` | перечитать `config.yml` |
| `/kitmod list` | `kitmod.admin` | список id предметов |

Все права по умолчанию — только для операторов.

---

## Предметы и механики

### С механиками

| id | Что делает |
|---|---|
| `pizza` | Еда. Восстанавливает **15 голода**. |
| `pizza_sword` | При ударе даёт **+2 голода**. Перезарядка **2 сек**. |
| `leviathan` | ПКМ — бросает **трезубец с Верностью III**. Перезарядка **2 сек**. |
| `vampire_scythe` | При ударе: **+5 HP** и **+1 голода** владельцу. Перезарядка **4 сек**. С шансом **1%** навсегда отнимает у противника **2 HP**. |
| `lava_mold` | Блок «шаблон для лавы». Ставится, заливается ведром лавы. |
| `lava_mold_filled` | Блок «шаблон для лавы с лавой». Появляется на месте пустого шаблона после заливки. |

### Просто предметы (декоративные)

`legendary_sword`, `lava_crystal`, `reinforced_string`, `dragon_ingot`,
`ender_ingot`, `blood_ingot`, `lava_ingot`, `ruby_diamond`, `rare_glove`,
`steel`, `master_redstone`

---

## Навсегда отнятое здоровье

Коса вампира с шансом 1% снимает 2 HP **навсегда** — это модификатор атрибута
`max_health`, который сохраняется в `plugins/KitMod/health.yml` и применяется
при каждом входе игрока.

Вернуть:

```
/hpgive Steve 2
```

Защита: отнять нельзя, если у цели останется меньше `drain-min-health`
(по умолчанию 6 HP = 3 сердца) — чтобы игрок не остался с 0 HP.

---

## Конфиг

Всё в `plugins/KitMod/config.yml`. Редактируются:

* `cooldown` — перезарядка каждого предмета в **секундах**;
* `drain-chance` — шанс отнять HP в **процентах** (по умолчанию `1.0`);
* `drain-amount`, `drain-min-health`, `heal`, `hunger`, `hunger-per-hit`;
* `nutrition` / `saturation` у пиццы;
* `tridents`, `loyalty-level`, `speed`, `spread`, `pickup` у левиафана;
* `material`, `name`, `lore`, `attack-damage`, `attack-speed`, `unbreakable`
  у любого предмета.

После правок — `/kitmod reload`.

---

## Как сделан кастомный блок

Шаблон для лавы — это нотный блок в редком состоянии
`instrument=custom_head, note=0/1, powered=false`, которому ресурспак подменяет
модель (`assets/minecraft/blockstates/note_block.json`). Плагин отменяет
физику, звук ноты и настройку блока, а при ломании выдаёт кастомный предмет.

⚠️ Известное ограничение метода: если игрок вручную поставит обычный нотный
блок и доведёт его ровно до этого состояния (голова сверху + нота 0/1), он
визуально станет шаблоном. На практике встречается крайне редко.

---

## Сборка

```bash
mvn -B package
```

Готовый jar — `target/KitMod-1.0.0.jar`.
В репозитории есть GitHub Actions workflow (`.github/workflows/build.yml`),
который собирает jar и zip ресурспака при каждом push.
