package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DamageKind;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The first word of the header of an item that is not a weapon: 防具 for armor and for what gives stats, 素材 for the rest. */
class KindTextTest {

    private String kind(Material material, StatSet stats, ElementStatSet elementStats) {
        return new RpgItemDefinition("t", material, ItemVisualOptions.NONE, "t", 1, Rarity.COMMON, Element.RED, DamageKind.NONE,
                AttackType.SPECIAL, ItemSkillType.NONE, "", Map.of(), null, stats, elementStats, List.of()).kindText();
    }

    @Test
    void armorMaterialIsArmor() {
        assertEquals("防具", kind(Material.IRON_HELMET, new StatSet(), new ElementStatSet()));
    }

    @Test
    void headWithStatsIsArmor() {
        StatSet stats = new StatSet();
        stats.set(StatType.DEFENSE, 5.0);
        assertEquals("防具", kind(Material.PLAYER_HEAD, stats, new ElementStatSet()));
        ElementStatSet resist = new ElementStatSet();
        resist.setResist(Element.RED, 5.0);
        assertEquals("防具", kind(Material.CARVED_PUMPKIN, new StatSet(), resist));
    }

    @Test
    void descriptionOnlyItemIsMaterial() {
        assertEquals("素材", kind(Material.BONE, new StatSet(), new ElementStatSet()));
    }
}
