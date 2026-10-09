package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;

import java.util.EnumMap;
import java.util.Map;

public record ItemSkillDefinition(
        ItemSkillType type,
        String displayName,
        Map<AttackType, ItemSkillVariant> variants
) {

    public ItemSkillDefinition {
        type = type == null ? ItemSkillType.NONE : type;
        displayName = displayName == null || displayName.isBlank() ? type.displayName() : displayName;
        EnumMap<AttackType, ItemSkillVariant> copy = new EnumMap<>(AttackType.class);
        if (variants != null) {
            copy.putAll(variants);
        }
        variants = Map.copyOf(copy);
    }

    public ItemSkillVariant variant(AttackType attackType) {
        if (attackType == null) return ItemSkillVariant.disabled(AttackType.SPECIAL);
        return variants.getOrDefault(attackType, ItemSkillVariant.disabled(attackType));
    }
}
