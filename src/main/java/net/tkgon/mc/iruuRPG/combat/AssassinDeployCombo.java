package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * The assassin's combo with a deployed weapon (a canister of smoke): the <b>veil of night</b> (t168: things set down that take away the enemies'
 * sight and way, so that they lose the assassin; it helps the assassin alone, not the allies). Every attack sets (or keeps) the veil round the
 * placement ({@code veil-radius} 5; as long as the placement lasts). Twice a second the enemies in it cannot see the assassin for a moment (they
 * lose them as a target: {@link AssassinSkills#blind}) and are slowed; the assassin in it is cloaked by the night: {@code veil-might} (25%) more
 * damage while they stay in it.
 *
 * <p>Normal: 1 shadow knives at the two nearest enemies in the veil (35% x2, confused), 2 the smoke swells: the enemies in it cannot see the assassin
 * for {@code swell-blind-seconds} (2.5) and are confused (30%), 3 the rim turns to thorns for 3 s: an enemy that crosses it cannot move for a
 * moment (30%), 4 shadow bonds on the enemy nearest the middle: it cannot move for 1.2 s and is cut three times (35% x3), 5 nightfall: every enemy in
 * it is struck (80%) and blinded for 3 s, and the assassin in it is cloaked twice as strongly for 4 s.
 *
 * <p>The heavy attacks below are what they were before t168 (a deployed weapon has no skills).
 */
public final class AssassinDeployCombo extends BaseCombo {

    static final String PLAN = "assassin_deploy";

    public AssassinDeployCombo() {
        super("assassin", AttackType.DEPLOY, new ComboSpec(PLAN,
                new double[]{0.35, 0.3, 0.3, 0.35, 0.8}, new int[]{2, 1, 1, 3, 1},
                new double[]{0.2, 1.2, 0.4, 0.07, 0.2, 0.25}, new int[]{140, 0, 0, 0, 0, 0}, 40));
    }

    private void smoke(MoveKit kit, Location at, double radius) {
        kit.fx(at).particle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, at.getX(), at.getY() + 0.5, at.getZ(), 2, radius * 0.4, 0.3, radius * 0.4, 0.01);
    }

    /**
     * A canister of smoke standing at {@code at}: a cloud of {@code radius} blocks that cuts and slows what is in it every {@code 10} ticks (a share of {@code rate}) and, every
     * {@code knifeEvery} ticks, throws a knife of {@code rate} out of the middle of it at the nearest enemy within 9 blocks. It stays for as long as the placement does.
     */
    private void canister(MoveKit kit, String key, Location at, double radius, double rate, int knifeEvery, double slowPercent, int life) {
        kit.sound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 0.7f);
        kit.zone(key, at, kit.linger(life), 2, zone -> {
            // (the smoke and the ring are drawn sparingly: a cloud that was too thick to see through)
            if (zone.age() % 6 == 0) smoke(kit, at, radius);
            if (zone.age() % 10 == 0) {
                if (zone.age() % 20 == 0) kit.fx(at).flatRing(at.clone().add(0.0, 0.15, 0.0), radius, (int) Math.max(14, radius * 5), kit.palette(), 0.3, 0.2, 0.9);
                for (LivingEntity victim : kit.around(at, radius)) {
                    if (kit.hit(victim, rate * 0.6) && slowPercent > 0.0) kit.slow(victim, slowPercent, 1.5);
                }
            }
            if (zone.age() % knifeEvery != 0 || zone.age() == 0) return;
            LivingEntity best = null;
            double bestDistance = 81.0;
            for (LivingEntity candidate : kit.around(at, 9.0)) {
                double d = candidate.getLocation().distanceSquared(at);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            if (best == null) return;
            Location from = at.clone().add(0.0, 1.0, 0.0);
            Vector toward = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0).toVector().subtract(from.toVector());
            if (toward.lengthSquared() < 1.0E-6) return;
            Blades.fly(kit, from, toward, 1.6, 10.0, rate, null);
        }, zone -> smoke(kit, at, radius * 1.6));
    }

    /** Where each assassin's veil stands (a veil set somewhere else is a new one). */
    private static final java.util.Map<java.util.UUID, Location> VEILS = new java.util.concurrent.ConcurrentHashMap<>();

    static void forget(java.util.UUID player) {
        VEILS.remove(player);
    }

    @Override
    public void normal(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(AssassinShade.SHADE);
        double rate = step.damageMultiplier();
        int hits = step.extraHits() + 1;
        Location center = kit.ground(kit.origin());
        double radius = kit.number(PLAN, "veil-radius", 5.0);
        veil(kit, center, radius);
        org.bukkit.entity.Player player = kit.player;
        java.util.List<LivingEntity> inside = kit.around(center, radius);
        inside.sort(java.util.Comparator.comparingDouble(victim -> victim.getLocation().distanceSquared(center)));
        boolean cloaked = player.getWorld() == center.getWorld() && player.getLocation().distanceSquared(center) <= radius * radius;
        switch (step.stage()) {
            case 1 -> {
                Location from = center.clone().add(0.0, 1.4, 0.0);
                kit.sound(from, Sfx.SHADOW_SLICE, 0.7f, 1.3f);
                for (int index = 0; index < Math.min(hits, inside.size()); index++) {
                    LivingEntity victim = inside.get(index);
                    kit.later(index * 2L, () -> {
                        if (!victim.isValid() || victim.isDead()) return;
                        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                        kit.fx(from).line(from, chest, AssassinShade.SHADE, 0.1, 0.9, 1.5, 1.0, 0.25, null, 1);
                        if (kit.hit(victim, rate)) {
                            AssassinShade.struck(kit, victim);
                            kit.confuse(victim, kit.number(PLAN, "knife-confusion", 8.0));
                        }
                    });
                }
            }
            case 2 -> {
                // the smoke swells: the enemies in it lose the assassin for longer
                kit.sound(center, Sfx.VEIL_SWELL, 1.0f, 1.0f);
                kit.shockwave(center, 0.8, radius, 5, 1.2);
                kit.particle(org.bukkit.Particle.SMOKE_LARGE, center.clone().add(0.0, 1.0, 0.0), 20, radius * 0.4, 0.02);
                long millis = Math.round(kit.number(PLAN, "swell-blind-seconds", 2.5) * 1000.0);
                for (LivingEntity victim : inside) {
                    AssassinSkills.blind(victim, player, millis);
                    kit.confuse(victim, kit.number(PLAN, "swell-confusion", 10.0));
                    kit.hit(victim, rate);
                }
            }
            case 3 -> thorns(kit, center, radius, rate);
            case 4 -> {
                if (inside.isEmpty()) return;
                LivingEntity victim = inside.get(0);
                kit.slow(victim, 100.0, kit.number(PLAN, "bond-seconds", 1.2));
                kit.sound(victim.getLocation(), Sfx.SHADE_HIT, 1.0f, 0.8f);
                Location feet = kit.ground(victim.getLocation());
                for (int index = 0; index < 4; index++) {
                    double angle = index * Math.PI / 2.0;
                    Location from = feet.clone().add(Math.cos(angle) * 1.4, 0.05, Math.sin(angle) * 1.4);
                    kit.fx(from).line(from, victim.getLocation().add(0.0, victim.getHeight() * 0.4, 0.0), AssassinShade.SHADE, 0.1, 0.8, 0.0, 0.9, 0.2, null, 1);
                }
                for (int index = 0; index < hits; index++) {
                    int which = index;
                    kit.later(3L + index * 4L, () -> {
                        if (!victim.isValid() || victim.isDead()) return;
                        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                        Vector cut = new Vector(Math.cos(which * 2.1), 0.4 * (which % 2 == 0 ? 1 : -1), Math.sin(which * 2.1)).normalize();
                        kit.fx(chest).line(chest.clone().subtract(cut.clone().multiply(1.0)), chest.clone().add(cut.clone().multiply(1.0)), AssassinShade.SHADE, 0.2, 1.0, 0.0, 1.0, 0.15, null, 1);
                        kit.sound(chest, Sfx.SHADOW_SLICE, 0.6f, 1.4f);
                        if (kit.hit(victim, rate)) AssassinShade.struck(kit, victim);
                    });
                }
            }
            default -> {
                // nightfall: everyone in the veil is struck and blinded; the assassin in it is cloaked twice as strongly
                kit.sound(center, Sfx.VEIL_NIGHTFALL, 1.0f, 1.0f);
                kit.shockwave(center, radius, 0.6, 6, 1.3);
                kit.fx(center.clone().add(0.0, 2.5, 0.0)).sphereShell(center.clone().add(0.0, 1.5, 0.0), radius * 0.6, 0.0, 40, AssassinShade.SHADE, 1.2);
                long millis = Math.round(kit.number(PLAN, "nightfall-blind-seconds", 3.0) * 1000.0);
                for (LivingEntity victim : inside) {
                    AssassinSkills.blind(victim, player, millis);
                    if (kit.hit(victim, rate)) AssassinShade.struck(kit, victim);
                }
                if (cloaked) {
                    kit.buff(player, StatusEffectType.MIGHT, kit.number(PLAN, "veil-might", 25.0) * 2.0, Math.round(kit.number(PLAN, "nightfall-seconds", 4.0) * 1000.0));
                    kit.sound(player.getLocation(), Sfx.SHADOW_VANISH, 0.6f, 1.4f);
                }
            }
        }
    }

    /**
     * The veil round the placement (one; the attacks keep it): its dark rim and smoke; twice a second the enemies in it cannot see the assassin for a
     * moment and are slowed, and the assassin in it is cloaked ({@code veil-might} more damage).
     */
    private static void veil(MoveKit kit, Location center, double radius) {
        java.util.UUID id = kit.player.getUniqueId();
        Location before = VEILS.put(id, center.clone());
        boolean fresh = before == null || before.getWorld() != center.getWorld() || before.distanceSquared(center) > 0.25;
        if (fresh) kit.sound(center, Sfx.VEIL_SWELL, 0.8f, 1.2f);
        org.bukkit.entity.Player player = kit.player;
        double might = kit.number(PLAN, "veil-might", 25.0);
        double slow = kit.number(PLAN, "veil-slow", 20.0);
        kit.zone("assassin_veil", center, kit.linger(60), 2, zone -> {
            int age = zone.age();
            if (age % 4 == 0) {
                net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(center);
                fx.flatRing(center.clone().add(0.0, 0.1, 0.0), radius, 50, AssassinShade.SHADE, 0.15, 0.2, 1.1);
                fx.flatRing(center.clone().add(0.0, 1.2 + 0.2 * Math.sin(age * 0.1), 0.0), radius * 0.95, 40, AssassinShade.SHADE, 0.3, 0.2, 0.9);
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                double angle = random.nextDouble(Math.PI * 2.0);
                double r = radius * Math.sqrt(random.nextDouble());
                kit.particle(org.bukkit.Particle.SMOKE_LARGE, center.clone().add(Math.cos(angle) * r, 0.6, Math.sin(angle) * r), 2, 0.4, 0.01);
                kit.particle(org.bukkit.Particle.SQUID_INK, center.clone().add(Math.cos(angle + 2.0) * r, 1.0, Math.sin(angle + 2.0) * r), 1, 0.3, 0.0);
            }
            if (age % 10 != 0) return;
            for (LivingEntity victim : kit.around(center, radius)) {
                AssassinSkills.blind(victim, player, 800L);
                kit.slow(victim, slow, 0.8);
            }
            if (player.getWorld() == center.getWorld() && player.getLocation().distanceSquared(center) <= radius * radius) {
                kit.buff(player, StatusEffectType.MIGHT, might, 800L);
                Location feet = player.getLocation();
                kit.fx(feet).flatRing(feet.clone().add(0.0, 0.1, 0.0), 0.6, 12, AssassinShade.SHADE, 0.6, 0.2, 0.9);
            }
        }, null);
    }

    /** The rim of the veil turns to thorns for 3 s: an enemy that crosses it cannot move for a moment and is cut (each at most once a second). */
    private static void thorns(MoveKit kit, Location center, double radius, double rate) {
        kit.sound(center, Sfx.VEIL_THORNS, 1.0f, 1.0f);
        java.util.Map<java.util.UUID, Boolean> wasInside = new java.util.HashMap<>();
        java.util.Map<java.util.UUID, Integer> last = new java.util.HashMap<>();
        for (LivingEntity victim : kit.around(center, radius + 3.0)) wasInside.put(victim.getUniqueId(), victim.getLocation().distanceSquared(center) <= radius * radius);
        double root = kit.number(PLAN, "thorn-root-seconds", 0.6);
        kit.zone("assassin_thorns", center, kit.linger(60), 2, zone -> {
            int age = zone.age();
            if (age % 4 == 0) {
                net.tkgon.mc.iruuRPG.vfx.Fx fx = kit.fx(center);
                for (int index = 0; index < 24; index++) {
                    double angle = index * Math.PI * 2.0 / 24.0 + age * 0.01;
                    Location base = center.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
                    Location tip = base.clone().add(Math.cos(angle) * 0.2, 0.8 + 0.3 * (index % 2), Math.sin(angle) * 0.2);
                    fx.line(base, tip, AssassinShade.SHADE, 0.1, 0.8, 1.5, 0.8, 0.2, null, 1);
                }
            }
            for (LivingEntity victim : kit.around(center, radius + 3.0)) {
                boolean in = victim.getLocation().distanceSquared(center) <= radius * radius;
                Boolean before = wasInside.put(victim.getUniqueId(), in);
                if (before == null || before == in) continue;
                Integer struck = last.get(victim.getUniqueId());
                if (struck != null && age - struck < 20) continue;
                last.put(victim.getUniqueId(), age);
                kit.slow(victim, 100.0, root);
                if (kit.hit(victim, rate)) AssassinShade.struck(kit, victim);
                kit.sound(victim.getLocation(), Sfx.SHADE_HIT, 0.6f, 1.5f);
            }
        }, null);
    }

    @Override
    public void heavy(MoveKit kit, MeleeCombo.Step step) {
        kit.palette(Palettes.of(kit.weapon.element()).mix(Palettes.STEEL, 0.4));
        double rate = step.damageMultiplier();
        Location feet = kit.origin();
        Location target = ComboParts.target(kit);
        switch (step.heavyIndex()) {
            case 0 -> {
                Location at = kit.ground(feet);
                kit.sound(feet, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
                ComboParts.sigil(kit, "assassin_screen", at, 3.4, rate, 50, 10, here -> {
                    for (LivingEntity victim : kit.around(here, 3.4)) kit.slow(victim, 40.0, 1.0);
                    smoke(kit, here, 3.4);
                });
            }
            case 1 -> ComboParts.bomb(kit, "assassin_decoy", target, 36, 3.2, rate, 0.0);
            case 2 -> {
                Vector toward = target.toVector().subtract(feet.toVector()).setY(0.0);
                Vector facing = toward.lengthSquared() < 1.0 ? kit.originFacing() : toward.normalize();
                for (int index = 0; index < 3; index++) {
                    Location spot = kit.ground(feet.clone().add(facing.clone().multiply(3.0 + 2.5 * index)));
                    kit.later(index * 2L, () -> ComboParts.mine(kit, "assassin_mines_" + spot.getBlockX() + "_" + spot.getBlockZ(), spot, 1.8, 2.8, rate, 0.0));
                }
            }
            case 3 -> {
                // three canisters round the aim that throw knives at the nearest enemy
                for (int index = 0; index < 3; index++) {
                    double angle = Math.PI * 2.0 * index / 3.0 + Math.PI * 0.5;
                    Location spot = kit.ground(target.clone().add(Math.cos(angle) * 2.2, 0.0, Math.sin(angle) * 2.2));
                    String key = "assassin_turret_" + index;
                    kit.later(index, () -> canister(kit, key, spot, 1.8, rate, 6, 0.0, 50));
                }
            }
            case 4 -> {
                // a veil of night: the aim goes dark and what stands in it is cut again and again
                kit.sound(target, Sfx.MAGIC_DARK, 1.0f, 0.7f);
                ComboParts.sigil(kit, "assassin_night", target, 4.6, rate, 50, 10, here ->
                        kit.fx(here).particle(org.bukkit.Particle.SQUID_INK, here.getX(), here.getY() + 1.0, here.getZ(), 10, 2.0, 0.8, 2.0, 0.01));
            }
            // (skill 6, t138)
            default -> sixth(kit, step.damageMultiplier());
        }
    }

    /** Skill 6 (t138): a trap of poison mist at the aim for 2.5 s: every 10 ticks it hurts and slows what is in it, and confuses it. */
    private void sixth(MoveKit kit, double rate) {
        Location target = kit.ground(ComboParts.target(kit));
        kit.sound(target, Sfx.MAGIC_DARK, 0.8f, 0.8f);
        ComboParts.sigil(kit, "assassin_mist", target, 3.2, rate, 50, 10, here -> {
            for (org.bukkit.entity.LivingEntity victim : kit.around(here, 3.2)) {
                kit.slow(victim, 35.0, 1.0);
                kit.confuse(victim, 20.0);
            }
            for (int index = 0; index < 6; index++) {
                double angle = Math.PI * 2.0 * index / 6.0;
                Location puff = here.clone().add(Math.cos(angle) * 1.8, 0.4, Math.sin(angle) * 1.8);
                kit.fx(puff).flatRing(puff, 0.6, 10, kit.palette(), 0.2, 0.3, 1.2);
            }
        });
    }
}
