package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DamageKind;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RpgItemDefinition(
        String id,
        Material material,
        ItemVisualOptions visualOptions,
        String name,
        int requiredLevel,
        Rarity rarity,
        Element element,
        DamageKind damageKind,
        AttackType attackType,
        ItemSkillType skill,
        String skillId,
        Map<String, Object> skillOverrides,
        WeaponStackSpec stacks,
        StatSet stats,
        ElementStatSet elementStats,
        List<String> description,
        Map<String, Object> comboStyle,
        String kindLabel,
        String typeLabel,
        Map<String, ClassUse> classUse
) {
    public RpgItemDefinition(String id, Material material, ItemVisualOptions visualOptions, String name, int requiredLevel,
                             Rarity rarity, Element element, DamageKind damageKind, AttackType attackType, ItemSkillType skill,
                             String skillId, Map<String, Object> skillOverrides, WeaponStackSpec stacks, StatSet stats,
                             ElementStatSet elementStats, List<String> description, Map<String, Object> comboStyle) {
        this(id, material, visualOptions, name, requiredLevel, rarity, element, damageKind, attackType, skill, skillId,
                skillOverrides, stacks, stats, elementStats, description, comboStyle, "", "", Map.of());
    }

    public RpgItemDefinition(String id, Material material, ItemVisualOptions visualOptions, String name, int requiredLevel,
                             Rarity rarity, Element element, DamageKind damageKind, AttackType attackType, ItemSkillType skill,
                             String skillId, Map<String, Object> skillOverrides, WeaponStackSpec stacks, StatSet stats,
                             ElementStatSet elementStats, List<String> description, Map<String, Object> comboStyle,
                             String kindLabel, String typeLabel) {
        this(id, material, visualOptions, name, requiredLevel, rarity, element, damageKind, attackType, skill, skillId,
                skillOverrides, stacks, stats, elementStats, description, comboStyle, kindLabel, typeLabel, Map.of());
    }

    public RpgItemDefinition(String id, Material material, ItemVisualOptions visualOptions, String name, int requiredLevel,
                             Rarity rarity, Element element, DamageKind damageKind, AttackType attackType, ItemSkillType skill,
                             String skillId, Map<String, Object> skillOverrides, WeaponStackSpec stacks, StatSet stats,
                             ElementStatSet elementStats, List<String> description) {
        this(id, material, visualOptions, name, requiredLevel, rarity, element, damageKind, attackType, skill, skillId,
                skillOverrides, stacks, stats, elementStats, description, Map.of());
    }

    public RpgItemDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item id must not be blank");
        }

        Objects.requireNonNull(material, "material");
        visualOptions = visualOptions == null ? ItemVisualOptions.NONE : visualOptions;
        name = name == null || name.isBlank() ? id : name;
        requiredLevel = Math.max(0, requiredLevel);
        rarity = rarity == null ? Rarity.COMMON : rarity;
        element = element == null ? Element.WHITE : element;
        damageKind = damageKind == null ? DamageKind.NONE : damageKind;
        attackType = attackType == null ? AttackType.SPECIAL : attackType;
        skill = skill == null ? ItemSkillType.NONE : skill;
        skillId = skillId == null ? "" : skillId;
        skillOverrides = skillOverrides == null ? Map.of() : Map.copyOf(skillOverrides);
        stats = stats == null ? new StatSet() : stats.copy();
        elementStats = elementStats == null ? new ElementStatSet() : elementStats.copy();
        description = description == null ? List.of() : List.copyOf(description);
        comboStyle = comboStyle == null ? Map.of() : comboStyle;
        kindLabel = kindLabel == null ? "" : kindLabel.trim();
        typeLabel = typeLabel == null ? "" : typeLabel.trim();
        classUse = classUse == null ? Map.of() : Map.copyOf(classUse);
    }

    /** True for a weapon with an attack type (melee / ranged / deployed): these have a mark line and a use per class. */
    public boolean marked() {
        return attackType != AttackType.SPECIAL && isWeaponLike();
    }

    /**
     * What the item is to the class: what its `classes:` says, else the default of its attack type (see
     * {@link ClassMark#defaultUse}). Usable for anything that is not a melee / ranged / deployed weapon.
     */
    public ClassUse classUse(String classId) {
        if (!marked()) return ClassUse.USABLE;
        ClassUse set = classId == null ? null : classUse.get(classId);
        return set != null ? set : ClassMark.defaultUse(attackType, classId);
    }

    /** False for a class the item is red to. */
    public boolean usableByClass(String classId) {
        return classUse(classId) != ClassUse.UNUSABLE;
    }

    /** True when the class's combo comes with the item: it is yellow to the class (anything not marked has no restriction). */
    public boolean comboByClass(String classId) {
        return !marked() || classUse(classId) == ClassUse.FAVORITE;
    }

    /** True when the weapon's `skill:` names a skill of skills/*.yml (the item skills sweep / heal / ... are `skill`). */
    public boolean hasGeneralSkill() {
        return !skillId.isEmpty();
    }

    public boolean isWeaponLike() {
        return attackType != AttackType.SPECIAL || !damageKind.noDamage();
    }

    /**
     * The first word after the level on the item: the item's own `kind-label` if it has one, else 物理 / 魔法, 防具 or 素材
     * for what is not a weapon (an armor kind, or a plain one whose material is a helmet / chestplate / leggings / boots /
     * shield / elytra is 防具, anything else 素材). Nothing for a special damage kind.
     */
    public String kindText() {
        if (!kindLabel.isEmpty()) return kindLabel;
        return switch (damageKind) {
            case PHYSICAL, MAGIC -> damageKind.displayName();
            case ARMOR -> "防具";
            case MATERIAL -> "素材";
            case NONE -> isArmorMaterial(material) ? "防具" : "素材";
            case SPECIAL -> "";
        };
    }

    /** The second word: the item's own `type-label`, else the attack type (近接 / 遠距離 / 設置). Nothing for a special one. */
    public String typeText() {
        if (!typeLabel.isEmpty()) return typeLabel;
        return attackType == AttackType.SPECIAL ? "" : attackType.displayName();
    }

    /** Helmets, chestplates, leggings, boots, shields and elytra. */
    public static boolean isArmorMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || material == Material.SHIELD || material == Material.ELYTRA;
    }
}
