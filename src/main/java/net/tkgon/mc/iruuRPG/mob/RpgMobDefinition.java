package net.tkgon.mc.iruuRPG.mob;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RpgMobDefinition(
        String id,
        EntityType entityType,
        String name,
        int level,
        long experience,
        boolean ai,
        boolean gravity,
        boolean silent,
        boolean glowing,
        boolean invulnerable,
        boolean fireProof,
        boolean persistent,
        boolean removeWhenFarAway,
        boolean baby,
        boolean showHealth,
        StatSet stats,
        ElementStatSet elementStats,
        Map<EquipmentSlot, MobEquipmentItem> equipment,
        List<MobDropItem> drops,
        List<String> description
) {
    public RpgMobDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Mob id must not be blank");
        }

        Objects.requireNonNull(entityType, "entityType");
        name = name == null || name.isBlank() ? id : name;
        level = Math.max(1, level);
        experience = Math.max(0L, experience);
        stats = stats == null ? new StatSet() : stats.copy();
        elementStats = elementStats == null ? new ElementStatSet() : elementStats.copy();
        equipment = equipment == null ? Map.of() : Map.copyOf(equipment);
        drops = drops == null ? List.of() : List.copyOf(drops);
        description = description == null ? List.of() : List.copyOf(description);
    }
}
