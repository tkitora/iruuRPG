package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class StatsMenu implements Listener {

    private static final int SIZE = 54;
    private static final Component TITLE = Component.text("iruuRPG ステータス");

    private final EquipmentService equipmentService;
    private final LevelService levelService;

    public StatsMenu(EquipmentService equipmentService, LevelService levelService) {
        this.equipmentService = equipmentService;
        this.levelService = levelService;
    }

    public void open(Player player) {
        PlayerProfile profile = equipmentService.recalculate(player);
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        fill(inventory);
        inventory.setItem(4, playerIcon(player, profile));
        inventory.setItem(10, category(Material.RED_DYE, "体力とマナ", NamedTextColor.RED, List.of(
                resourceLine("HP", profile.currentHp(), profile.maxHp(), NamedTextColor.RED),
                resourceLine("MP", profile.currentMp(), profile.maxMp(), NamedTextColor.AQUA),
                Component.empty(),
                statLine(profile.finalStats(), StatType.MAX_HP),
                statLine(profile.finalStats(), StatType.HP_REGEN),
                statLine(profile.finalStats(), StatType.MAX_MP),
                statLine(profile.finalStats(), StatType.MP_REGEN)
        )));
        inventory.setItem(12, category(Material.DIAMOND_SWORD, "攻撃", NamedTextColor.GOLD, statLines(profile.finalStats(),
                StatType.WEAPON_DAMAGE,
                StatType.STRENGTH,
                StatType.STRENGTH_PERCENT,
                StatType.MAGIC,
                StatType.MAGIC_PERCENT,
                StatType.ADD_DAMAGE,
                StatType.MELEE_DAMAGE,
                StatType.MELEE_DAMAGE_PERCENT,
                StatType.RANGE_DAMAGE,
                StatType.RANGE_DAMAGE_PERCENT
        )));
        inventory.setItem(14, category(Material.CLOCK, "戦闘補助", NamedTextColor.YELLOW, statLines(profile.finalStats(),
                StatType.CRIT_DAMAGE,
                StatType.CRIT_CHANCE,
                StatType.ADRENALINE,
                StatType.MAGIC_OVERLOAD,
                StatType.STABILITY,
                StatType.DURATION,
                StatType.ATTACK_SPEED,
                StatType.MOVE_SPEED
        )));
        inventory.setItem(16, category(Material.SHIELD, "防御", NamedTextColor.BLUE, statLines(profile.finalStats(),
                StatType.DEFENSE,
                StatType.PROTECTION,
                StatType.PHYSICAL_RESIST,
                StatType.MAGIC_RESIST,
                StatType.DAMAGE_REDUCTION
        )));
        inventory.setItem(28, category(Material.BLAZE_POWDER, "属性ダメージ", NamedTextColor.LIGHT_PURPLE, elementDamageLines(profile.finalElementStats())));
        inventory.setItem(30, category(Material.PRISMARINE_SHARD, "属性防御", NamedTextColor.GREEN, elementResistLines(profile.finalElementStats())));
        inventory.setItem(32, category(Material.NETHER_STAR, "特殊とデバフ", NamedTextColor.DARK_PURPLE, statLines(profile.finalStats(),
                StatType.SPECIAL_DAMAGE,
                StatType.BLEED,
                StatType.ABSORB_PERCENT,
                StatType.SLOW_PERCENT,
                StatType.CORROSION,
                StatType.DECAY,
                StatType.LACERATION,
                StatType.CONFUSION,
                StatType.EXPLOSION
        )));
        inventory.setItem(34, category(Material.BOOK, "内訳", NamedTextColor.WHITE, sourceLines(profile)));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!isStatsView(event.getView())) return;

        event.setCancelled(true);
        if (event.getRawSlot() != 4) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!levelService.canLevelUp(levelService.profile(player))) return;

        if (levelService.tryLevelUp(player)) {
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!isStatsView(event.getView())) return;

        event.setCancelled(true);
    }

    private boolean isStatsView(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof Holder;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack playerIcon(Player player, PlayerProfile profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        if (meta != null) {
            meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>(List.of(
                    line("Lv", format(profile.level()), NamedTextColor.YELLOW, ""),
                    line("XP", xpText(profile), NamedTextColor.GREEN, ""),
                    resourceLine("HP", profile.currentHp(), profile.maxHp(), NamedTextColor.RED),
                    resourceLine("MP", profile.currentMp(), profile.maxMp(), NamedTextColor.AQUA)
            ));
            if (levelService.canLevelUp(profile)) {
                lore.add(Component.empty());
                lore.add(Component.text("クリックでレベルアップ", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack category(Material material, String name, NamedTextColor color, List<Component> lore) {
        return item(material, name, color, lore);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(component -> component.decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Component> statLines(StatSet stats, StatType... types) {
        List<Component> lines = new ArrayList<>();
        for (StatType type : types) {
            lines.add(statLine(stats, type));
        }
        return lines;
    }

    private Component statLine(StatSet stats, StatType type) {
        String suffix = isPercent(type) ? "%" : "";
        return line(statName(type), format(stats.get(type)), NamedTextColor.GRAY, suffix);
    }

    private Component resourceLine(String label, double current, double max, NamedTextColor color) {
        return Component.text(label + " ", color)
                .append(Component.text(format(current), NamedTextColor.WHITE))
                .append(Component.text("/", NamedTextColor.DARK_GRAY))
                .append(Component.text(format(max), NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component line(String label, String value, NamedTextColor labelColor, String suffix) {
        return Component.text(label + ": ", labelColor)
                .append(Component.text(value + suffix, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> elementDamageLines(ElementStatSet stats) {
        List<Component> lines = new ArrayList<>();
        for (Element element : Element.values()) {
            lines.add(Component.text(elementName(element) + " 固定: ", elementColor(element))
                    .append(Component.text(format(stats.damage(element)), NamedTextColor.WHITE)));
            lines.add(Component.text(elementName(element) + " 倍率: ", elementColor(element))
                    .append(Component.text(format(stats.damagePercent(element)) + "%", NamedTextColor.WHITE)));
        }
        return lines;
    }

    private List<Component> elementResistLines(ElementStatSet stats) {
        List<Component> lines = new ArrayList<>();
        for (Element element : Element.values()) {
            lines.add(Component.text(elementName(element) + " 防御: ", elementColor(element))
                    .append(Component.text(format(stats.resist(element)) + "%", NamedTextColor.WHITE)));
        }
        return lines;
    }

    private List<Component> sourceLines(PlayerProfile profile) {
        return List.of(
                Component.text("基礎/装備/職業/バフを合算した", NamedTextColor.GRAY),
                Component.text("現在の最終ステータスです。", NamedTextColor.GRAY),
                Component.empty(),
                sourceLine("基礎HP", profile.baseStats().get(StatType.MAX_HP)),
                sourceLine("装備HP", profile.equipmentStats().get(StatType.MAX_HP)),
                sourceLine("基礎MP", profile.baseStats().get(StatType.MAX_MP)),
                sourceLine("装備MP", profile.equipmentStats().get(StatType.MAX_MP))
        );
    }

    private Component sourceLine(String label, double value) {
        return line(label, format(value), NamedTextColor.DARK_AQUA, "");
    }

    private boolean isPercent(StatType type) {
        return switch (type) {
            case STRENGTH_PERCENT,
                    MAGIC_PERCENT,
                    MELEE_DAMAGE_PERCENT,
                    RANGE_DAMAGE_PERCENT,
                    CRIT_DAMAGE,
                    CRIT_CHANCE,
                    ADRENALINE,
                    MAGIC_OVERLOAD,
                    STABILITY,
                    ATTACK_SPEED,
                    MOVE_SPEED,
                    PROTECTION,
                    PHYSICAL_RESIST,
                    MAGIC_RESIST,
                    DAMAGE_REDUCTION,
                    ABSORB_PERCENT,
                    SLOW_PERCENT -> true;
            default -> false;
        };
    }

    private String statName(StatType type) {
        return switch (type) {
            case MAX_HP -> "最大HP";
            case HP_REGEN -> "HP自動回復";
            case MAX_MP -> "最大MP";
            case MP_REGEN -> "MP自動回復";
            case WEAPON_DAMAGE -> "武器ダメージ";
            case STRENGTH -> "筋力";
            case STRENGTH_PERCENT -> "筋力%";
            case MAGIC -> "魔力";
            case MAGIC_PERCENT -> "魔力%";
            case ADD_DAMAGE -> "追加ダメージ";
            case MELEE_DAMAGE -> "近接ダメージ";
            case MELEE_DAMAGE_PERCENT -> "近接ダメージ倍率";
            case RANGE_DAMAGE -> "遠距離ダメージ";
            case RANGE_DAMAGE_PERCENT -> "遠距離ダメージ倍率";
            case CRIT_DAMAGE -> "クリティカルダメージ";
            case CRIT_CHANCE -> "クリティカル率";
            case ADRENALINE -> "アドレナリン";
            case MAGIC_OVERLOAD -> "魔力増幅";
            case STABILITY -> "安定性";
            case DURATION -> "効果時間";
            case ATTACK_SPEED -> "攻撃速度";
            case MOVE_SPEED -> "移動速度";
            case DEFENSE -> "防御";
            case PROTECTION -> "保護";
            case PHYSICAL_RESIST -> "物理耐性";
            case MAGIC_RESIST -> "魔法耐性";
            case DAMAGE_REDUCTION -> "ダメージ軽減";
            case SPECIAL_DAMAGE -> "特殊ダメージ";
            case BLEED -> "出血";
            case ABSORB_PERCENT -> "吸収";
            case SLOW_PERCENT -> "鈍足";
            case CORROSION -> "腐食";
            case DECAY -> "腐敗";
            case LACERATION -> "裂傷";
            case CONFUSION -> "混乱";
            case EXPLOSION -> "爆破";
        };
    }

    private String elementName(Element element) {
        return switch (element) {
            case RED -> "赤";
            case BLUE -> "青";
            case WHITE -> "白";
            case GREEN -> "緑";
            case ORANGE -> "橙";
        };
    }

    private NamedTextColor elementColor(Element element) {
        return switch (element) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case WHITE -> NamedTextColor.WHITE;
            case GREEN -> NamedTextColor.GREEN;
            case ORANGE -> NamedTextColor.GOLD;
        };
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 2);
        return format.format(value);
    }

    private String format(long value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private String xpText(PlayerProfile profile) {
        long required = levelService.requiredXpForNextLevel(profile.level());
        if (required <= 0L) {
            return "MAX";
        }
        return format(profile.xp()) + "/" + format(required);
    }

    private static final class Holder implements InventoryHolder {

        private final UUID ownerId;
        private Inventory inventory;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
