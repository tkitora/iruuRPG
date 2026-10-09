package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;

import java.util.concurrent.ThreadLocalRandom;

public final class DamageCalculator {

    private static final double W0 = 30.0;
    private static final double WA = 1.25;
    private static final double W1 = 300.0;
    private static final double W2 = 800.0;
    private static final double W3 = 800.0;
    private static final double W4 = 2.0;
    private static final double W5 = 10.0;
    private static final double TYPE_SPIKE_BONUS = 1.0;
    private static final double ELEMENT_SPIKE_BONUS = 1.0;
    private static final double POWER_SPIKE_BONUS = 0.6;
    private static final double DEFENSE_K = 600.0;

    public DamageResult calculate(DamageInput input) {
        RpgItemDefinition weapon = input.weapon();
        if (weapon.damageKind() == DamageKind.NONE) {
            return new DamageResult(0.0, false);
        }

        if (weapon.damageKind() == DamageKind.SPECIAL) {
            double damage = input.attacker().finalStats().get(StatType.SPECIAL_DAMAGE)
                    + input.attacker().finalStats().get(StatType.WEAPON_DAMAGE);
            return new DamageResult(round(Math.max(0.0, damage)), false);
        }

        DamageParts parts = buildParts(input.attacker(), input.victim(), weapon);
        double base = baseDamage(parts);
        double typeMultiplier = typeMultiplier(parts);
        double elementMultiplier = elementMultiplier(parts);

        SpikeResult spike = applySpikeBonus(parts, base, typeMultiplier, elementMultiplier);
        double defenseFactor = defenseFactor(parts.defense);
        double resistFactor = resistFactor(parts.kindResist, parts.elementResist);
        CriticalResult critical = criticalResult(input.attacker(), weapon);

        double finalDamage = spike.base
                * spike.typeMultiplier
                * spike.elementMultiplier
                * defenseFactor
                * resistFactor
                * finalTypeMultiplier(input.attacker(), weapon)
                * critical.multiplier;

        return new DamageResult(round(Math.max(0.0, finalDamage)), critical.critical);
    }

    private DamageParts buildParts(PlayerProfile attacker, PlayerProfile victim, RpgItemDefinition weapon) {
        StatSet attackerStats = attacker.finalStats();
        StatSet victimStats = victim.finalStats();
        Element element = weapon.element();

        double power = switch (weapon.damageKind()) {
            case PHYSICAL -> attackerStats.get(StatType.STRENGTH);
            case MAGIC -> attackerStats.get(StatType.MAGIC);
            default -> 0.0;
        };

        double kindResist = switch (weapon.damageKind()) {
            case PHYSICAL -> victimStats.get(StatType.PHYSICAL_RESIST);
            case MAGIC -> victimStats.get(StatType.MAGIC_RESIST);
            default -> 0.0;
        };

        double typeDamage = switch (weapon.attackType()) {
            case MELEE -> attackerStats.get(StatType.MELEE_DAMAGE);
            case RANGE -> attackerStats.get(StatType.RANGE_DAMAGE);
            default -> 0.0;
        };

        double typePercent = switch (weapon.attackType()) {
            case MELEE -> attackerStats.get(StatType.MELEE_DAMAGE_PERCENT);
            case RANGE -> attackerStats.get(StatType.RANGE_DAMAGE_PERCENT);
            default -> 0.0;
        };

        double defense = victimStats.get(StatType.DEFENSE);
        double elementResist = victim.finalElementStats().resist(element);
        if (element == Element.ORANGE) {
            defense = 0.0;
            kindResist = 0.0;
            elementResist = 0.0;
        }

        return new DamageParts(
                Math.max(0.0, attackerStats.get(StatType.WEAPON_DAMAGE)),
                Math.max(0.0, power),
                Math.max(0.0, typeDamage),
                Math.max(0.0, typePercent),
                Math.max(0.0, attacker.finalElementStats().damage(element)),
                Math.max(0.0, attacker.finalElementStats().damagePercent(element)),
                Math.max(0.0, defense),
                Math.max(0.0, kindResist),
                Math.max(0.0, elementResist)
        );
    }

    private double baseDamage(DamageParts parts) {
        double weaponScale = Math.pow(parts.weaponDamage + W0, WA);
        return weaponScale * (1.0 + Math.sqrt((1.0 + parts.power) / W1));
    }

    private double typeMultiplier(DamageParts parts) {
        double scale = 1.0 + parts.typePercent / 100.0;
        return 1.0 + scale * parts.typeDamage / (parts.typeDamage + W2);
    }

    private double elementMultiplier(DamageParts parts) {
        double scale = 1.0 + parts.elementPercent / 100.0;
        return 1.0 + scale * parts.elementDamage / (parts.elementDamage + W3);
    }

    private SpikeResult applySpikeBonus(DamageParts parts, double base, double typeMultiplier, double elementMultiplier) {
        double typeValue = parts.typeDamage * (1.0 + parts.typePercent / 100.0);
        double elementValue = parts.elementDamage * (1.0 + parts.elementPercent / 100.0);
        double total = parts.power + typeValue + elementValue;

        if (total <= 1.0E-9) {
            return new SpikeResult(base, typeMultiplier, elementMultiplier);
        }

        double powerShare = parts.power * W5 / total;
        double typeShare = typeValue / total;
        double elementShare = elementValue / total;
        double maxShare = Math.max(powerShare, Math.max(typeShare, elementShare));
        double spiky = (maxShare - 1.0 / 3.0) / (2.0 / 3.0);
        spiky = Math.max(0.0, Math.min(1.0, spiky));
        double spikePower = Math.pow(spiky, W4);

        if (powerShare >= typeShare && powerShare >= elementShare) {
            base *= 1.0 + POWER_SPIKE_BONUS * spikePower;
        } else if (typeShare >= elementShare) {
            typeMultiplier *= 1.0 + TYPE_SPIKE_BONUS * spikePower;
        } else {
            elementMultiplier *= 1.0 + ELEMENT_SPIKE_BONUS * spikePower;
        }

        return new SpikeResult(base, typeMultiplier, elementMultiplier);
    }

    private double defenseFactor(double defense) {
        return DEFENSE_K / (defense + DEFENSE_K);
    }

    private double resistFactor(double kindResist, double elementResist) {
        double kind = kindResist / (1.0 + kindResist);
        double element = elementResist / (1.0 + elementResist);
        return (1.0 - kind) * (1.0 - element);
    }

    private double finalTypeMultiplier(PlayerProfile attacker, RpgItemDefinition weapon) {
        StatSet stats = attacker.finalStats();
        return switch (weapon.attackType()) {
            case RANGE -> 1.0 + Math.max(0.0, stats.get(StatType.MAGIC_OVERLOAD)) / 100.0;
            case DEPLOY -> 1.0 + Math.max(0.0, stats.get(StatType.STABILITY)) / 100.0;
            default -> 1.0;
        };
    }

    private CriticalResult criticalResult(PlayerProfile attacker, RpgItemDefinition weapon) {
        if (weapon.attackType() != AttackType.MELEE || weapon.damageKind() != DamageKind.PHYSICAL) {
            return new CriticalResult(false, 1.0);
        }

        double chance = Math.max(0.0, attacker.finalStats().get(StatType.CRIT_CHANCE));
        boolean critical = ThreadLocalRandom.current().nextDouble(100.0) < chance;
        if (!critical) {
            return new CriticalResult(false, 1.0);
        }

        double criticalDamage = Math.max(0.0, attacker.finalStats().get(StatType.CRIT_DAMAGE));
        return new CriticalResult(true, 1.0 + criticalDamage / 100.0);
    }

    private double round(double value) {
        return Math.floor(value * 10.0 + 0.5) / 10.0;
    }

    private record DamageParts(
            double weaponDamage,
            double power,
            double typeDamage,
            double typePercent,
            double elementDamage,
            double elementPercent,
            double defense,
            double kindResist,
            double elementResist
    ) {
    }

    private record SpikeResult(double base, double typeMultiplier, double elementMultiplier) {
    }

    private record CriticalResult(boolean critical, double multiplier) {
    }
}
