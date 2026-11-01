package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The ranger's combo with a ranged weapon (the bow): arrows that fly in arcs (gravity pulls them down; the one exception is the charged arrow of the fifth attack and the
 * piercing arrow of heavy 1, which fly straight), and crowd control with them. An arrow of the ranger marks the enemy it hits for 5 s: the ranger's melee and deployed weapons
 * hit a marked enemy for 20% more (the loop with the other weapons). A melee hit makes the next arrow stronger and faster.
 * Normal: 1 one arrow (70%, slows a little), 2 two arrows at once (47% x2), 3 three arrows one after the other, 3 ticks apart (39% x3), 4 a fan of five arrows (28% x5, pushes
 * the enemies apart), 5 a charge of 0.75 s and then one arrow that goes straight through everything in the way (164%, which cannot move for 1.2 s).
 * Heavy: 0 a backstep shot (the ranger hops back, nothing can hurt for 4 ticks, 130% and slows 50%) that gives one effect to the arrows after it, drawn from a shuffle bag of
 * four (ricochet and poison for the next 5 arrows; a small explosion and orange damage for the next 3 normal attacks; see {@link RangerBuffs}), 1 a straight piercing arrow that
 * hits what it passes for 5% only and breaks a trap or a mine of the ranger it meets, with a burst on the enemies round it, 2 a ring a block ahead for 3 s: an arrow through it
 * is 20% faster and stronger and its tip is red, 3 four homing arrows, one every 2 ticks (35% x4), 4 a volley loosed up into the air that falls a second later in three
 * steps, 3, 6 and 9 blocks ahead (an area of 3.2 blocks each, from the ground up to 10 blocks, so an enemy in the air is hit too; hit twice for 70% each time; the arrows are only the look of it).
 */
public final class RangerRangeCombo implements ClassCombo {

    static final String PLAN = "ranger_range";

    private static final ComboSpec SPEC = new ComboSpec(PLAN,
            new double[]{0.7, 0.47, 0.39, 0.28, 1.64}, new int[]{1, 2, 3, 5, 1},
            new double[]{1.3, 0.4, 0.4, 0.35, 0.7, 0.22}, new int[]{100, 0, 0, 0, 0, 0}, 40);

    /** The look of the piercing arrow of heavy 1: violet and white, unlike the other arrows. */
    private static final Gradient PIERCE = Gradient.of(Color.fromRGB(90, 40, 160), Color.fromRGB(190, 140, 255), Color.fromRGB(255, 255, 255));

    /** What an arrow that ended at {@code end} does to the enemies within {@code radius} of it: slows them. */
    private void slowAt(MoveKit kit, Location end, double radius, double percent, double seconds) {
        for (LivingEntity victim : kit.around(end, radius)) kit.slow(victim, percent, seconds);
    }

    @Override
    public String classId() {
        return "gifted";
    }

    @Override
    public AttackType type() {
        return AttackType.RANGE;
    }

    @Override
    public ComboSpec spec() {
        return SPEC;
    }

    private Location muzzle(MoveKit kit, double side, double up) {
        Vector[] axes = kit.axes();
        return kit.eye().add(axes[0].clone().multiply(0.6)).add(axes[1].clone().multiply(side)).add(axes[2].clone().multiply(up));
    }

    /** From the muzzle to the aim: the direction of an arrow. */
    private Vector toward(MoveKit kit, Location start) {
        Vector direction = kit.aim().toVector().subtract(start.toVector());
        return direction.lengthSquared() < 1.0E-6 ? kit.axes()[0] : direction.normalize();
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.25));
        kit.rangerBegin(true);
        double[] boost = kit.rangeBoost();
        double rate = step.damageMultiplier() * boost[0];
        double speed = 2.0 * boost[1];
        int hits = step.extraHits() + 1;
        Vector[] axes = kit.axes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (step.stage()) {
            case 1 -> {
                // one arrow
                Location start = muzzle(kit, 0.0, -0.1);
                kit.arrowShot(start, toward(kit, start), rate, speed, 1.0, end -> slowAt(kit, end, 1.3, 20.0, 1.0));
            }
            case 2 -> {
                // two arrows at once, one either side of the bow
                for (int index = 0; index < hits; index++) {
                    Location start = muzzle(kit, index == 0 ? 0.3 : -0.3, -0.1);
                    kit.arrowShot(start, toward(kit, start), rate, speed, 1.0, end -> slowAt(kit, end, 1.3, 20.0, 1.0));
                }
            }
            case 3 -> {
                // three arrows one after the other, 3 ticks apart
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(index * 3L, () -> {
                        Location start = muzzle(kit, which % 2 == 0 ? 0.3 : -0.3, -0.1);
                        Vector direction = toward(kit, start);
                        if (which > 0) {
                            direction.add(axes[1].clone().multiply(random.nextDouble(-0.04, 0.04))).add(axes[2].clone().multiply(random.nextDouble(-0.03, 0.03))).normalize();
                        }
                        kit.arrowShot(start, direction, rate, speed * 1.05, 1.0, end -> slowAt(kit, end, 1.3, 20.0, 1.0));
                    });
                }
            }
            case 4 -> {
                // a fan of five arrows
                Location start = muzzle(kit, 0.0, -0.1);
                for (int index = 0; index < hits; index++) {
                    double turn = Math.toRadians((index - 2) * 10.0);
                    Vector direction = axes[0].clone().multiply(Math.cos(turn)).add(axes[1].clone().multiply(Math.sin(turn))).normalize();
                    kit.later(index / 3, () -> kit.arrowShot(start.clone(), direction, rate, speed, 1.0, end -> {
                        for (LivingEntity victim : kit.around(end, 1.3)) kit.push(victim, kit.player.getLocation().toVector(), 0.3, 0.1);
                    }));
                }
            }
            default -> {
                // the finisher: a charge of 0.75 s (the light draws in to the bow, higher and higher), then one arrow straight through everything in the way
                Location at = kit.player.getLocation();
                kit.sound(at, Sound.ITEM_CROSSBOW_LOADING_START, 0.9f, 0.8f);
                for (int tick = 0; tick < 15; tick++) {
                    int which = tick;
                    kit.later(tick, () -> {
                        Location bow = muzzle(kit, 0.0, -0.1);
                        Vector[] now = kit.axes();
                        double r = 1.6 - 1.2 * which / 14.0;
                        kit.fx(bow).ring(bow.clone().add(now[0].clone().multiply(0.7)), now[1], now[2], r, 22, kit.palette(), 0.4 + 0.04 * which, 0.1, 1.0);
                        if (which % 5 == 4) kit.sound(bow, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.9f + 0.3f * which / 14.0f);
                    });
                }
                kit.later(15L, () -> {
                    Location start = muzzle(kit, 0.0, -0.1);
                    Vector direction = toward(kit, start);
                    Vector[] now = kit.axes();
                    kit.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.6f);
                    kit.sound(start, Sound.ITEM_CROSSBOW_SHOOT, 0.8f, 0.6f);
                    kit.sound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
                    kit.shockwave(start, 0.4, 2.0, 3, 1.0);
                    for (int ring = 0; ring < 6; ring++) {
                        Location ringAt = start.clone().add(direction.clone().multiply(2.0 + ring * 2.6));
                        kit.later(ring, () -> kit.fx(ringAt).ring(ringAt, now[1], now[2], 0.6 + 0.08 * ringAt.distance(start) / 3.0, 16, kit.palette(), 0.5, 0.3, 1.1));
                    }
                    kit.pierceArrow(start, direction, 3.0, 20.0, kit.palette(), victim -> {
                        if (kit.hit(victim, rate)) {
                            kit.slow(victim, 90.0, 1.2);
                            kit.burst(victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0), 1.2);
                            kit.buffHit(victim, victim.getLocation(), rate);
                        }
                    }, null, end -> kit.burst(end, 1.6));
                });
            }
        }
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.25));
        kit.rangerBegin(false);
        double[] boost = kit.rangeBoost();
        double rate = step.damageMultiplier() * (step.heavyIndex() == 0 ? 1.0 : boost[0]);
        Location feet = kit.player.getLocation();
        Vector[] axes = kit.axes();
        switch (step.heavyIndex()) {
            case 0 -> {
                // a backstep shot: the ranger hops back (nothing can hurt for 4 ticks) and looses an arrow at the aim that slows what it hits;
                // then one of four effects is given to the arrows after it
                Location start = muzzle(kit, 0.0, -0.1);
                Vector direction = toward(kit, start);
                kit.invulnerable(4);
                kit.hopBack(0.9, 0.3);
                kit.sound(feet, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.9f);
                kit.arrowShot(start, direction, 0.0, 2.0, 1.0, end -> {
                    kit.burst(end, 1.4);
                    for (LivingEntity victim : kit.around(end, 1.5)) {
                        if (kit.hit(victim, rate)) kit.slow(victim, 50.0, 2.0);
                    }
                });
                RangerBuffs.Type given = RangerBuffs.roll(kit.player);
                kit.palette(given.trail);
                kit.sound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
                kit.later(1L, () -> {
                    Location at = kit.player.getLocation();
                    kit.fx(at).flatRing(at.clone().add(0.0, 0.2, 0.0), 1.8, 36, given.trail, 0.8, 0.2, 1.1);
                    kit.fx(at).sphereShell(at.clone().add(0.0, 1.0, 0.0), 1.1, 0.0, 28, given.trail, 1.0);
                    kit.particle(switch (given) {
                        case RICOCHET -> Particle.CRIT_MAGIC;
                        case POISON -> Particle.SPORE_BLOSSOM_AIR;
                        case BLAST -> Particle.FLAME;
                        case ORANGE -> Particle.WAX_ON;
                    }, at.clone().add(0.0, 1.0, 0.0), 24, 0.7, 0.05);
                });
            }
            case 1 -> {
                // a straight piercing arrow: it hits what it passes for 5% only, and a trap, snare or mine of the ranger that it meets is broken and bursts on the enemies round it
                Location start = muzzle(kit, 0.0, -0.1);
                Vector direction = toward(kit, start);
                double share = kit.number(PLAN, "pierce-share", 0.05);
                double blast = kit.number(PLAN, "placement-blast-rate", 1.2);
                kit.sound(start, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.1f);
                kit.sound(start, Sound.ITEM_TRIDENT_THROW, 0.7f, 1.3f);
                kit.pierceArrow(start, direction, 2.4, 22.0, PIERCE, victim -> {
                    if (kit.hit(victim, share)) kit.burst(victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0), 0.8);
                }, here -> {
                    kit.particle(Particle.END_ROD, here, 1, 0.12, 0.01);
                    for (Location placed : kit.breakPlacements(here, 1.8)) {
                        kit.burst(placed.clone().add(0.0, 0.5, 0.0), 3.0);
                        kit.shockwave(placed, 0.8, 3.4, 4, 1.1);
                        kit.sound(placed, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                        kit.hitAll(kit.around(placed, 3.4), blast);
                    }
                    return false;
                }, end -> kit.burst(end, 1.2));
            }
            case 2 -> {
                // a ring a block ahead for 3 s: an arrow through it is 20% faster and stronger, with a red tip (a new ring takes the old one's place)
                Location centre = kit.eye().add(axes[0].clone().multiply(1.0)).subtract(0.0, 0.1, 0.0);
                int until = org.bukkit.Bukkit.getCurrentTick() + 60;
                RangerBuffs.setCircle(kit.player.getUniqueId(), new RangerBuffs.Circle(centre, axes[0].clone(), 1.0, until));
                kit.sound(centre, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.7f);
                kit.sound(centre, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
                Vector right = axes[1].clone();
                Vector up = axes[2].clone();
                kit.zone("ringc_circle", centre, 60, 2, zone -> {
                    double pulse = 0.5 + 0.5 * Math.sin(zone.age() * 0.3);
                    kit.fx(centre).ring(centre, right, up, 1.0, 36, kit.palette(), 0.5 + 0.4 * pulse, 0.1, 1.1);
                    kit.fx(centre).ring(centre, right, up, 0.7, 24, kit.palette(), 0.4, 0.1, 0.9);
                    if (zone.age() % 6 == 0) kit.particle(Particle.END_ROD, centre, 2, 0.8, 0.0);
                }, zone -> kit.shockwave(centre, 0.3, 1.2, 2, 0.7));
            }
            case 3 -> {
                // four homing arrows, one every 2 ticks
                kit.sound(feet, Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.3f);
                for (int index = 0; index < 4; index++) {
                    double side = (index - 1.5) * 0.7;
                    Location start = muzzle(kit, side, 0.1);
                    Vector heading = axes[0].clone().add(axes[1].clone().multiply(side * 0.5)).add(new Vector(0.0, 0.25, 0.0));
                    kit.later(index * 2L, () -> kit.homing(start, heading, 1.3, 45, rate, 16.0, end -> kit.burst(end, 1.0)));
                }
            }
            case 4 -> {
                // a volley loosed up into the air that comes down a second later in three steps, 3, 6 and 9 blocks ahead; the arrows are the look of it, the damage is the area
                // (where the player stands and looks now, not where the combo began: origin() is the start of the combo)
                Location origin = kit.player.getLocation();
                Vector forward = kit.facing();
                Vector side = new Vector(-forward.getZ(), 0.0, forward.getX());
                ThreadLocalRandom random = ThreadLocalRandom.current();
                kit.sound(feet, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.7f);
                kit.sound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
                for (int stage = 0; stage < 3; stage++) {
                    Location centre = kit.ground(origin.clone().add(forward.clone().multiply(3.0 * (stage + 1))));
                    int begin = stage * 6;
                    kit.later(begin, () -> {
                        // the arrows loosed up and out, an arc each
                        Location from = muzzle(kit, 0.0, -0.1);
                        for (int arrow = 0; arrow < 3; arrow++) {
                            Vector way = forward.clone().multiply(0.5).add(new Vector(0.0, 1.0, 0.0)).add(side.clone().multiply((arrow - 1) * 0.18)).normalize();
                            Location peak = from.clone().add(way.clone().multiply(8.0));
                            for (int seg = 0; seg < 4; seg++) {
                                Location a = from.clone().add(peak.clone().subtract(from).toVector().multiply(seg / 4.0));
                                Location b = from.clone().add(peak.clone().subtract(from).toVector().multiply((seg + 1) / 4.0));
                                kit.later(seg, () -> kit.line(a, b, 0.2, 1.0, 1.1));
                            }
                        }
                        kit.sound(from, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.2f);
                        kit.fx(centre).flatRing(centre.clone().add(0.0, 0.15, 0.0), 3.2, 44, kit.palette(), 0.5, 0.2, 1.0);
                    });
                    for (int tick = 6; tick < 20; tick += 4) {
                        kit.later(begin + tick, () -> kit.fx(centre).flatRing(centre.clone().add(0.0, 0.15, 0.0), 3.2, 44, kit.palette(), 0.5, 0.2, 0.9));
                    }
                    kit.later(begin + 20L, () -> {
                        // the arrows fall
                        for (int arrow = 0; arrow < 10; arrow++) {
                            double angle = random.nextDouble(Math.PI * 2.0);
                            double distance = Math.sqrt(random.nextDouble()) * 3.0;
                            Location spot = kit.ground(centre.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance));
                            Location top = spot.clone().add(random.nextDouble(-1.0, 1.0), 10.0, random.nextDouble(-1.0, 1.0));
                            kit.later(arrow / 3, () -> {
                                for (int seg = 0; seg < 3; seg++) {
                                    Location a = top.clone().add(spot.clone().subtract(top).toVector().multiply(seg / 3.0));
                                    Location b = top.clone().add(spot.clone().subtract(top).toVector().multiply((seg + 1) / 3.0));
                                    kit.later(seg, () -> kit.line(a, b, 0.2, 1.0, 1.1));
                                }
                            });
                        }
                        kit.later(4L, () -> {
                            kit.burst(centre.clone().add(0.0, 0.3, 0.0), 2.4);
                            kit.shockwave(centre, 0.6, 3.2, 3, 1.0);
                            kit.sound(centre, Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
                            kit.sound(centre, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.6f);
                            kit.hitAll(kit.column(centre, 3.2, 10.0), rate);
                            // and a second hit of the same area a moment later
                            kit.later(6L, () -> {
                                kit.burst(centre.clone().add(0.0, 0.3, 0.0), 1.8);
                                kit.shockwave(centre, 0.6, 3.2, 3, 0.8);
                                kit.sound(centre, Sound.ENTITY_ARROW_HIT, 0.7f, 1.0f);
                                kit.hitAll(kit.column(centre, 3.2, 10.0), rate);
                            });
                        });
                    });
                }
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): rapid fire: six arrows at the aim, two ticks apart. */
    private void sixth(MoveKit kit, double rate) {
        for (int index = 0; index < 6; index++) {
            kit.later(index * 2L, () -> {
                Location start = ComboParts.hand(kit);
                Vector direction = kit.aim().toVector().subtract(start.toVector());
                if (direction.lengthSquared() < 1.0E-6) return;
                kit.sound(start, org.bukkit.Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.4f);
                kit.arrowShot(start, direction.normalize(), rate, 1.2, end -> {
                });
            });
        }
    }
}
