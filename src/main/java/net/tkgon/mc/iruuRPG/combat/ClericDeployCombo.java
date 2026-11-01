package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * The cleric's combo with a deployed weapon (a lantern of sanctuary): healing ground. It is made at the placement of the weapon (see {@link DeployHold}) and, like the rest,
 * stays as long as the placement does. The normal attacks are built round one thing, a lantern that sends a SHOCKWAVE of light out every second or so (hurting what it passes
 * and healing the cleric when they stand in it); each attack changes that one lantern (a larger wave, two waves, a quicker one, three waves in a row, a great wide one) and
 * replaces the lantern of the one before, so the normal attacks do not pile lanterns up. The heavy attacks are the ones that make more of them. All heals from things set
 * down together are held to 1.5% of the maximum health a second ({@link MoveKit#healCapped}). Normal: 1 a lantern (20% a wave, radius 3), 2 a larger lantern, two waves (15% a
 * wave, radius 4), 3 a lantern of prayer, a quicker wave (25%, radius 5), 4 a lantern sending three waves in a row (12% each, radius 4), 5 a great lantern (20%, radius 6).
 * Heavy: 0 a sanctuary under the feet (15% a pulse, cooldown 8 s), 1 four lanterns running ahead (20% x3 each), 2 a lantern that swells for 2 s, healing the cleric while it
 * does, and bursts (100%; nothing is pulled or held), 3 four lanterns round the aim that shoot light at the nearest enemy (7% a shot), 4 a great sanctuary (20% a pulse).
 */
public final class ClericDeployCombo extends BaseCombo {

    static final String PLAN = "cleric_deploy";

    public ClericDeployCombo() {
        super("healer", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.2, 0.15, 0.25, 0.12, 0.2}, new int[]{1, 1, 1, 1, 1},
                new double[]{0.15, 0.2, 1.0, 0.07, 0.2, 0.15}, new int[]{160, 0, 0, 0, 0, 0}, 40));
    }

    /**
     * The lantern of the normal attacks: one at a time (the key is the same, so the next attack replaces it), standing at {@code at} for as long as the placement does. Every
     * {@code every} ticks it sends {@code rings} shockwaves of light out to {@code radius}, three ticks apart; each hurts everything in the radius for {@code rate / rings} and,
     * if the cleric stands in it, heals {@code healPercent / rings} of the maximum health.
     */
    private void lantern(MoveKit kit, Location at, double radius, double rate, int every, double healPercent, int rings) {
        ComboParts.appear(kit, at);
        kit.later(2L, () -> {
            kit.sound(at, Sound.BLOCK_BELL_USE, 0.8f, 1.5f);
            kit.zone("cleric_lantern", at, kit.linger(50), 2, zone -> {
                boolean pulse = zone.age() % every == 0;
                ComboParts.standLantern(kit, at, 1.1, pulse ? 1.0 : 0.0);
                if (zone.age() % 6 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.12, 0.0), radius, (int) Math.max(24, radius * 8), kit.palette(), 0.25, 0.1, 0.8);
                if (!pulse) return;
                for (int ring = 0; ring < rings; ring++) {
                    kit.later(ring * 3L, () -> {
                        kit.shockwave(at, radius * 0.25, radius, 4, 1.0);
                        kit.sound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
                        kit.hitAll(kit.around(at, radius), rate / rings);
                        if (kit.player.getLocation().distanceSquared(at) <= (radius + 1.0) * (radius + 1.0)) kit.healCapped(healPercent / rings);
                    });
                }
            }, zone -> kit.shockwave(at, radius * 0.5, radius, 3, 0.8));
        });
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.4));
        double rate = step.damageMultiplier();
        Location target = ComboParts.target(kit);
        switch (step.stage()) {
            case 1 -> lantern(kit, target, 3.0, rate, 20, 0.5, 1);
            case 2 -> lantern(kit, target, 4.0, rate, 20, 0.6, 2);
            case 3 -> lantern(kit, target, 5.0, rate, 16, 0.8, 1);
            case 4 -> lantern(kit, target, 4.0, rate, 24, 0.9, 3);
            default -> lantern(kit, target, 6.0, rate, 24, 1.2, 1);
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.GOLD, 0.45));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
                ComboParts.sigil(kit, "cleric_deploy_sanctuary", at, 3.5, rate, 50, 10, ignored -> kit.healCapped(1.0));
            }
            case 1 -> {
                // four lanterns set down one after the other running ahead
                Vector facing = kit.originFacing();
                kit.sound(feet, Sfx.WIND, 0.9f, 0.8f);
                for (int index = 1; index <= 4; index++) {
                    Location at = kit.ground(feet.clone().add(facing.clone().multiply(2.0 * index)));
                    String key = "cleric_trail_" + index;
                    kit.later(index * 2L, () -> ComboParts.placeAt(kit, key, at, 44, 14, 2.0, rate, 0.0, 0.3, null));
                }
            }
            case 2 -> ComboParts.swellBurst(kit, target.clone().add(0.0, 1.5, 0.0), 4.5, rate, 1.0, 40);
            case 3 -> {
                // four lanterns round the aim that shoot light at the nearest enemy
                for (int index = 0; index < 4; index++) {
                    double angle = Math.PI * 0.5 * index + Math.PI * 0.25;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.0, 0.0, Math.sin(angle) * 2.0));
                    String key = "cleric_guard_" + index;
                    kit.later(index, () -> ComboParts.turretAt(kit, key, spot, 50, 10, 7.0, rate, null));
                }
            }
            case 4 -> {
                kit.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
                ComboParts.sigil(kit, "cleric_deploy_great", target, 6.0, rate, 50, 10, ignored -> {
                    if (kit.player.getLocation().distanceSquared(target) <= 36.0) kit.healCapped(1.2);
                });
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a lighthouse of prayer at the aim for 3 s: every 10 ticks it hurts the enemies round it and heals the allies by it 2%. */
    private void sixth(MoveKit kit, double rate) {
        Location target = ComboParts.target(kit);
        ComboParts.place(kit, "healer_lighthouse", target, 60, 10, 3.6, rate, 0.0, 2.0, here -> kit.pillar(here, 0.5, 3.0));
    }
}
