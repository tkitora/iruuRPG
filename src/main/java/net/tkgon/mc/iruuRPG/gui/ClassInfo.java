package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassDefinition;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.item.ClassMark;
import net.tkgon.mc.iruuRPG.item.ClassUse;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * How a class is shown in the menus: the same words (catchphrase, what it is, its features: classes/*.yml) wherever it is
 * chosen, changed or skilled, its color, and the combo guide of the skill tree (one item per kind of weapon).
 */
public final class ClassInfo {

    /** The order of the classes on the class menu. */
    public static final List<String> ORDER = List.of("warrior", "mage", "healer", "gifted", "assassin");

    private ClassInfo() {
    }

    /** The color of the class (its element): warrior red, mage blue, cleric green, ranger white, assassin orange. */
    public static Color color(String classId) {
        return switch (classId == null ? "" : classId) {
            case "warrior" -> Color.fromRGB(225, 45, 45);
            case "mage" -> Color.fromRGB(55, 100, 235);
            case "healer" -> Color.fromRGB(65, 205, 95);
            case "assassin" -> Color.fromRGB(255, 150, 30);
            default -> Color.fromRGB(245, 245, 245);
        };
    }

    static TextColor textColor(String classId) {
        return TextColor.color(color(classId).asRGB());
    }

    static Material glass(String classId) {
        return switch (classId == null ? "" : classId) {
            case "warrior" -> Material.RED_STAINED_GLASS_PANE;
            case "mage" -> Material.BLUE_STAINED_GLASS_PANE;
            case "healer" -> Material.LIME_STAINED_GLASS_PANE;
            case "assassin" -> Material.ORANGE_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    /** The lines that introduce the class: catchphrase, what it is, then 特徴. */
    static List<Component> blurb(ClassDefinition definition) {
        List<Component> lore = new ArrayList<>();
        if (!definition.catchphrase().isBlank()) {
            lore.add(plain(definition.catchphrase(), textColor(definition.id())).decoration(TextDecoration.BOLD, true));
        }
        for (String line : definition.description()) {
            lore.add(plain(line, NamedTextColor.WHITE));
        }
        if (!definition.features().isEmpty()) {
            lore.add(plain("特徴: ", NamedTextColor.GRAY)
                    .append(Component.text(String.join("・", definition.features()), NamedTextColor.AQUA)));
        }
        return lore;
    }

    /** The class's icon with its name and introduction, then {@code extra} lines. */
    static ItemStack icon(ClassDefinition definition, List<Component> extra) {
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(definition.name(), textColor(definition.id()), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>(blurb(definition));
        if (!extra.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(extra);
        }
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    /** The kinds of weapon of the combo guide, in the order of the items. */
    static final List<AttackType> GUIDE_KINDS = List.of(AttackType.MELEE, AttackType.RANGE, AttackType.DEPLOY);

    /**
     * The combo guide of the class for one kind of weapon: how to throw the attacks, then the class's lines (弱1-5, 強0-4,
     * notes; 【】 are what the attack does to the enemy). Null when the class yml has no lines for it.
     */
    static ItemStack comboGuide(ClassDefinition definition, AttackType kind) {
        String key = kind.name().toLowerCase(java.util.Locale.ROOT);
        List<String> lines = definition.comboGuide().get(key);
        if (lines == null || lines.isEmpty()) return null;

        boolean favorite = ClassMark.defaultUse(kind, definition.id()) == ClassUse.FAVORITE;
        Material material = switch (kind) {
            case MELEE -> Material.IRON_SWORD;
            case RANGE -> Material.BOW;
            default -> Material.LANTERN;
        };
        String kindName = switch (kind) {
            case MELEE -> "近接武器";
            case RANGE -> "遠距離武器";
            default -> "設置武器";
        };
        List<Component> lore = new ArrayList<>();
        lore.add(plain("得意な武器 (黄色の印) で出せるコンボ", NamedTextColor.GRAY));
        for (String how : controls(kind)) {
            lore.add(plain(how, NamedTextColor.DARK_AQUA));
        }
        lore.add(plain("強の番号 = その前に出した弱の数", NamedTextColor.DARK_AQUA));
        lore.add(plain("【】は敵への効果", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        for (String line : lines) {
            lore.add(guideLine(line));
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        Component name = Component.text("コンボ解説: " + kindName, NamedTextColor.GOLD, TextDecoration.BOLD);
        if (favorite) name = name.append(Component.text(" (得意)", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false));
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> controls(AttackType kind) {
        if (net.tkgon.mc.iruuRPG.combat.HeavyInput.numberKeys()) {
            return switch (kind) {
                case MELEE -> List.of("左クリック: 弱 (弱1→弱5)", "右クリック→数字キー1〜5: 強1〜強5", "Q: 武器のスキル");
                case RANGE -> List.of("右クリック: 弱 (弱1→弱5)", "左クリック→数字キー1〜5: 強1〜強5", "Q: 武器のスキル");
                default -> List.of("右クリック: 1回目で設置、その後は弱", "左クリック→数字キー1〜5: 強1〜強5 (設置ごとに1回)", "Shift+Q: 設置を消す");
            };
        }
        return switch (kind) {
            case MELEE -> List.of("左クリック: 弱 (弱1→弱5)", "右→右: 強1 / 右→左: 強2", "Shift+右: 強3 / Shift+左: 強4", "Shift 2回: 強5", "Q: 武器のスキル");
            case RANGE -> List.of("右クリック: 弱 (弱1→弱5)", "左→左: 強1 / 左→右: 強2", "Shift+左: 強3 / Shift+右: 強4", "Shift 2回: 強5", "Q: 武器のスキル");
            default -> List.of("右クリック: 1回目で設置、その後は弱", "左→左: 強1 / 左→右: 強2", "Shift+左: 強3 / Shift+右: 強4", "Shift 2回: 強5 (強は設置ごとに1回)",
                    "Shift+Q: 設置を消す");
        };
    }

    /** "弱1: text【effect】": the label colored by 弱 / 強, the text white, what is in 【】 aqua; ※ lines gray. */
    static Component guideLine(String line) {
        if (line.isBlank()) return Component.empty();
        if (line.startsWith("※")) return withEffects(line, NamedTextColor.GRAY);

        int colon = line.indexOf(':');
        if (colon < 0) return withEffects(line, NamedTextColor.WHITE);
        String label = line.substring(0, colon + 1);
        NamedTextColor labelColor = label.startsWith("強") ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
        return Component.text(label, labelColor).append(withEffects(line.substring(colon + 1), NamedTextColor.WHITE));
    }

    private static Component withEffects(String text, NamedTextColor color) {
        Component result = Component.empty();
        int at = 0;
        while (at < text.length()) {
            int open = text.indexOf('【', at);
            int close = open < 0 ? -1 : text.indexOf('】', open);
            if (open < 0 || close < 0) {
                result = result.append(Component.text(text.substring(at), color));
                break;
            }
            if (open > at) result = result.append(Component.text(text.substring(at, open), color));
            result = result.append(Component.text(text.substring(open, close + 1), NamedTextColor.AQUA));
            at = close + 1;
        }
        return result;
    }

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
