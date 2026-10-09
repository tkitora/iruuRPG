package net.tkgon.mc.iruuRPG.stat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum StatType {
    MAX_HP("max-hp", "Max HP", "addHp"),
    HP_REGEN("hp-regen", "HP Regen", "addRegHp"),
    MAX_MP("max-mp", "Max MP", "addMp"),
    MP_REGEN("mp-regen", "MP Regen", "addRegMp"),

    WEAPON_DAMAGE("weapon-damage", "Weapon Damage", "weaponDmg"),
    STRENGTH("strength", "Strength", "addPower"),
    MAGIC("magic", "Magic", "addAPower"),
    ADD_DAMAGE("add-damage", "Add Damage", "addDmg"),

    MELEE_DAMAGE("melee-damage", "Melee Damage", "meleeDmg"),
    MELEE_DAMAGE_PERCENT("melee-damage-percent", "Melee Damage %", "meleeDmgS"),
    RANGE_DAMAGE("range-damage", "Range Damage", "rangeDmg"),
    RANGE_DAMAGE_PERCENT("range-damage-percent", "Range Damage %", "rangeDmgS"),

    CRIT_DAMAGE("crit-damage", "Critical Damage %", "addCritDmg"),
    CRIT_CHANCE("crit-chance", "Critical Chance %", "addCritChance"),
    ADRENALINE("adrenaline", "Adrenaline %"),
    MAGIC_OVERLOAD("magic-overload", "Magic Amplify %", "apOver", "magic-amplify", "magic-amplification"),
    STABILITY("stability", "Stability %", "stable"),
    DURATION("duration", "Duration", "duration"),
    ATTACK_SPEED("attack-speed", "Attack Speed %", "atkSpeed"),
    MOVE_SPEED("move-speed", "Move Speed %", "speed"),

    DEFENSE("defense", "Defense", "def"),
    PROTECTION("protection", "Protection %"),
    PHYSICAL_RESIST("physical-resist", "Physical Resist", "atkDef"),
    MAGIC_RESIST("magic-resist", "Magic Resist", "apDef"),
    DAMAGE_REDUCTION("damage-reduction", "Damage Reduction %", "dmgDef"),

    SPECIAL_DAMAGE("special-damage", "Special Damage"),
    BLEED("bleed", "Bleed"),
    ABSORB_PERCENT("absorb-percent", "Absorb %"),
    SLOW_PERCENT("slow-percent", "Slow %"),
    CORROSION("corrosion", "Corrosion"),
    DECAY("decay", "Decay"),
    LACERATION("laceration", "Laceration"),
    CONFUSION("confusion", "Confusion"),
    EXPLOSION("explosion", "Explosion");

    private static final Map<String, StatType> BY_KEY = new HashMap<>();

    static {
        for (StatType type : values()) {
            BY_KEY.put(normalize(type.key), type);
            for (String alias : type.aliases) {
                BY_KEY.put(normalize(alias), type);
            }
        }
    }

    private final String key;
    private final String displayName;
    private final String[] aliases;

    StatType(String key, String displayName, String... aliases) {
        this.key = key;
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isPersistentBaseStat() {
        return switch (this) {
            case MAX_HP, HP_REGEN, MAX_MP, MP_REGEN, STRENGTH, MAGIC, CRIT_DAMAGE, CRIT_CHANCE, DEFENSE -> true;
            default -> false;
        };
    }

    public static Optional<StatType> fromConfigKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(BY_KEY.get(normalize(key)));
    }

    private static String normalize(String raw) {
        return raw.trim()
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
    }
}
