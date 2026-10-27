package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemVisualOptions;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage of a standard build at some levels (no server needed): the numbers of design/damage-rebalance-2026-10-28.md. A melee swing is 0.7 s, so
 * the damage of one hit divided by 0.7 is the DPS of the table (about 50 at Lv1, 20 thousand at Lv200).
 */
class DamageCalculatorTest {

    private final DamageCalculator calculator = new DamageCalculator();

    private double hit(double weapon, double strength, double meleeDamage, double redDamage) {
        PlayerProfile attacker = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        attacker.finalStats().set(StatType.WEAPON_DAMAGE, weapon);
        attacker.finalStats().set(StatType.STRENGTH, strength);
        attacker.finalStats().set(StatType.MELEE_DAMAGE, meleeDamage);
        attacker.finalElementStats().setDamage(Element.RED, redDamage);
        PlayerProfile victim = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        RpgItemDefinition sword = new RpgItemDefinition("test_sword", Material.IRON_SWORD, ItemVisualOptions.NONE, "test", 1, Rarity.COMMON,
                Element.RED, DamageKind.PHYSICAL, AttackType.MELEE, ItemSkillType.NONE, "", Map.of(), null, new StatSet(), new ElementStatSet(), List.of());
        return calculator.calculate(new DamageInput(attacker, victim, sword)).damage();
    }

    @Test
    void levelOneStarterSwordIsAbout50Dps() {
        // the starter sword (weapon 10, strength 4, melee 3): one hit ~33, ~47 a second
        assertEquals(33.2, hit(10, 4, 3, 0), 0.5);
    }

    @Test
    void standardBuildOfLevelFifty() {
        assertEquals(881.1, hit(230, 25, 150, 100), 5.0);
    }

    @Test
    void standardBuildOfLevelTwoHundredIsAbout20kDps() {
        double damage = hit(1080, 100, 1000, 800);
        assertEquals(14437.4, damage, 70.0);
        assertTrue(damage / 0.7 > 19000.0 && damage / 0.7 < 22000.0, "DPS " + damage / 0.7);
    }
}
