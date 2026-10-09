package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DamageKind;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;

import java.util.List;
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
        StatSet stats,
        ElementStatSet elementStats,
        List<String> description
) {
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
        stats = stats == null ? new StatSet() : stats.copy();
        elementStats = elementStats == null ? new ElementStatSet() : elementStats.copy();
        description = description == null ? List.of() : List.copyOf(description);
    }

    public boolean isWeaponLike() {
        return attackType != AttackType.SPECIAL || damageKind != DamageKind.NONE;
    }
}
