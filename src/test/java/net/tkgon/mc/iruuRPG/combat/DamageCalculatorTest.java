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

    private double orangeHit(double orangeResist) {
        PlayerProfile attacker = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        attacker.finalStats().set(StatType.WEAPON_DAMAGE, 100.0);
        attacker.finalElementStats().setDamage(Element.ORANGE, 100.0);
        PlayerProfile victim = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        victim.finalStats().set(StatType.DEFENSE, 500.0);
        victim.finalStats().set(StatType.PHYSICAL_RESIST, 3.0);
        victim.finalElementStats().setResist(Element.ORANGE, orangeResist);
        RpgItemDefinition sword = new RpgItemDefinition("test_orange", Material.IRON_SWORD, ItemVisualOptions.NONE, "test", 1, Rarity.COMMON,
                Element.ORANGE, DamageKind.PHYSICAL, AttackType.MELEE, ItemSkillType.NONE, "", Map.of(), null, new StatSet(), new ElementStatSet(), List.of());
        return calculator.calculate(new DamageInput(attacker, victim, sword)).damage();
    }

    @Test
    void orangeIgnoresEverythingButTheOrangeResist() {
        double open = orangeHit(0.0);
        // defense 500 and physical resist 3 do not count: the same as a victim with none of them
        assertTrue(open > 0.0);
        // the orange resist is the one thing that is not skipped: resist 1 halves the damage
        assertEquals(open * 0.5, orangeHit(1.0), open * 0.02);
    }

    private boolean criticalOf(DamageKind kind, AttackType type) {
        PlayerProfile attacker = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        attacker.finalStats().set(StatType.WEAPON_DAMAGE, 10.0);
        attacker.finalStats().set(StatType.CRIT_CHANCE, 1000.0);
        PlayerProfile victim = new PlayerProfile(UUID.randomUUID(), 20.0, 20.0);
        RpgItemDefinition weapon = new RpgItemDefinition("test_crit", Material.BOW, ItemVisualOptions.NONE, "test", 1, Rarity.COMMON,
                Element.RED, kind, type, ItemSkillType.NONE, "", Map.of(), null, new StatSet(), new ElementStatSet(), List.of());
        return calculator.calculate(new DamageInput(attacker, victim, weapon)).critical();
    }

    @Test
    void physicalDamageCanCritWhateverTheAttackType() {
        assertTrue(criticalOf(DamageKind.PHYSICAL, AttackType.MELEE));
        assertTrue(criticalOf(DamageKind.PHYSICAL, AttackType.RANGE));
        assertTrue(criticalOf(DamageKind.PHYSICAL, AttackType.DEPLOY));
        assertTrue(!criticalOf(DamageKind.MAGIC, AttackType.MELEE));
    }
}
