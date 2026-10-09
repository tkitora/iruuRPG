package net.tkgon.mc.iruuRPG.stat;

import java.util.Map;

/** Japanese display names for stats. Screens show these names and whole numbers only (no internal keys or formulas). */
public final class StatLabels {

    private static final Map<StatType, String> NAMES = Map.ofEntries(
            Map.entry(StatType.MAX_HP, "最大HP"),
            Map.entry(StatType.HP_REGEN, "HP回復"),
            Map.entry(StatType.MAX_MP, "最大MP"),
            Map.entry(StatType.MP_REGEN, "MP回復"),
            Map.entry(StatType.WEAPON_DAMAGE, "武器ダメージ"),
            Map.entry(StatType.STRENGTH, "筋力"),
            Map.entry(StatType.STRENGTH_PERCENT, "筋力%"),
            Map.entry(StatType.MAGIC, "魔力"),
            Map.entry(StatType.MAGIC_PERCENT, "魔力%"),
            Map.entry(StatType.MELEE_DAMAGE, "近接ダメージ"),
            Map.entry(StatType.MELEE_DAMAGE_PERCENT, "近接ダメージ%"),
            Map.entry(StatType.RANGE_DAMAGE, "遠距離ダメージ"),
            Map.entry(StatType.RANGE_DAMAGE_PERCENT, "遠距離ダメージ%"),
            Map.entry(StatType.DEPLOY_DAMAGE, "設置ダメージ"),
            Map.entry(StatType.DEPLOY_DAMAGE_PERCENT, "設置ダメージ%"),
            Map.entry(StatType.CRIT_DAMAGE, "会心ダメージ"),
            Map.entry(StatType.CRIT_CHANCE, "会心率"),
            Map.entry(StatType.ADRENALINE, "アドレナリン"),
            Map.entry(StatType.MAGIC_OVERLOAD, "魔力増幅"),
            Map.entry(StatType.STABILITY, "安定性"),
            Map.entry(StatType.DURATION, "効果時間"),
            Map.entry(StatType.ATTACK_SPEED, "攻撃速度"),
            Map.entry(StatType.MOVE_SPEED, "移動速度"),
            Map.entry(StatType.DEFENSE, "防御"),
            Map.entry(StatType.PROTECTION, "保護"),
            Map.entry(StatType.PHYSICAL_RESIST, "物理耐性"),
            Map.entry(StatType.MAGIC_RESIST, "魔法耐性"),
            Map.entry(StatType.DAMAGE_REDUCTION, "ダメージ軽減"),
            Map.entry(StatType.SPECIAL_DAMAGE, "特殊ダメージ"),
            Map.entry(StatType.BLEED, "出血"),
            Map.entry(StatType.ABSORB_PERCENT, "吸血"),
            Map.entry(StatType.SLOW_PERCENT, "鈍足"),
            Map.entry(StatType.CORROSION, "腐食"),
            Map.entry(StatType.DECAY, "腐敗"),
            Map.entry(StatType.LACERATION, "裂傷"),
            Map.entry(StatType.CONFUSION, "混乱"),
            Map.entry(StatType.EXPLOSION, "爆破")
    );

    /** Resists are stored as fractions (0.01 = 1%) and shown as percent. */
    private static final java.util.Set<StatType> FRACTION_STATS = java.util.Set.of(StatType.PHYSICAL_RESIST, StatType.MAGIC_RESIST);

    private StatLabels() {
    }

    public static String name(StatType type) {
        return NAMES.getOrDefault(type, type.key());
    }

    public static String elementName(Element element) {
        return switch (element) {
            case RED -> "赤属性ダメージ";
            case BLUE -> "青属性ダメージ";
            case WHITE -> "白属性ダメージ";
            case GREEN -> "緑属性ダメージ";
            case ORANGE -> "橙属性ダメージ";
        };
    }

    /** A signed whole-number text like "+3" / "-2". Non-zero values never show as 0 (rounded away from zero). */
    public static String signed(StatType type, double value) {
        double shown = FRACTION_STATS.contains(type) ? value * 100.0 : value;
        long rounded = (long) (shown > 0 ? Math.ceil(shown) : Math.floor(shown));
        return (rounded >= 0 ? "+" : "") + rounded + (isPercent(type) ? "%" : "");
    }

    public static String signedElement(double value, boolean percent) {
        long rounded = (long) (value > 0 ? Math.ceil(value) : Math.floor(value));
        return (rounded >= 0 ? "+" : "") + rounded + (percent ? "%" : "");
    }

    /** A whole-number text without a sign, e.g. "1200" or "15%". Resists are shown as percent. */
    public static String plain(StatType type, double value) {
        double shown = FRACTION_STATS.contains(type) ? value * 100.0 : value;
        return Math.round(shown) + (isPercent(type) ? "%" : "");
    }

    public static String plainElement(double value, boolean percent) {
        return Math.round(value) + (percent ? "%" : "");
    }

    public static boolean isPercent(StatType type) {
        return switch (type) {
            case STRENGTH_PERCENT, MAGIC_PERCENT, MELEE_DAMAGE_PERCENT, RANGE_DAMAGE_PERCENT, DEPLOY_DAMAGE_PERCENT,
                 CRIT_DAMAGE, CRIT_CHANCE, ADRENALINE, MAGIC_OVERLOAD, STABILITY, ATTACK_SPEED, MOVE_SPEED,
                 PROTECTION, PHYSICAL_RESIST, MAGIC_RESIST, DAMAGE_REDUCTION, ABSORB_PERCENT, SLOW_PERCENT -> true;
            default -> false;
        };
    }
}
