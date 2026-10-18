package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillRunner;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * The assassin's five heavy attacks (see {@link MeleeCombo}): 0 a step in the walking direction (the right click),
 * 1 a thrust on the spot (the skill {@code combat.combo.assassin.thrust-skill}, so a skill can call it too), 2 two
 * sweeps forwards (40% each) and then a small hop back, 3 a leap along the view with cuts at random places all around
 * on the way, 4 several cuts around the player (5 hits of 25%), a little in front of them.
 */
public final class ComboMoves {

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects effects;
    private final MoveTracker moves;
    private final SkillRegistry skills;
    private final SkillRunner runner;

    public ComboMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects, MoveTracker moves,
                      SkillRegistry skills, SkillRunner runner) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.effects = effects;
        this.moves = moves;
        this.skills = skills;
        this.runner = runner;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.assassin." + key, fallback);
    }

    /** Throws heavy attack {@code step.heavyIndex()}. */
    public void perform(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        switch (step.heavyIndex()) {
            case 0 -> step(player, weapon);
            case 1 -> thrust(player, weapon, step);
            case 2 -> sweepHop(player, weapon, step);
            case 3 -> leapSlash(player, weapon, step);
            default -> surround(player, weapon, step);
        }
    }

    private Vector facing(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0.0);
        if (direction.lengthSquared() < 1.0E-6) direction = new Vector(0.0, 0.0, 1.0);
        return direction.normalize();
    }

    // ---- 0: the step ------------------------------------------------------------------------------------------------

    private void step(Player player, RpgItemDefinition weapon) {
        Vector direction = moves.direction(player);
        if (direction == null) direction = facing(player);
        Location from = player.getLocation().clone();
        player.setFallDistance(0.0f);
        player.setVelocity(direction.clone().multiply(number("step-power", 1.05)).setY(0.12));
        effects.playStepTrail(player, weapon, from, direction);
    }

    // ---- 1: the thrust ----------------------------------------------------------------------------------------------

    private void thrust(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        String id = plugin.getConfig().getString("combat.combo.assassin.thrust-skill", "assassin_thrust");
        SkillSpec spec = skills.all().get(id);
        if (spec == null) {
            player.sendMessage("[iruuRPG] 強攻撃1のスキルが見つかりません: " + id);
            return;
        }
        if (step.damageMultiplier() > 0.0) spec = spec.withDamageRate(step.damageMultiplier());
        SkillSpec cast = spec;
        runner.start(new PlayerSkillCaster(plugin, attackService, player, weapon, cast), cast,
                () -> PlayerSkillCaster.aimPoint(player, cast.range()), () -> {
                });
    }

    // ---- 2: two sweeps and the hop back -------------------------------------------------------------------------------

    private void sweepHop(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        Vector facing = facing(player);
        long gap = (long) number("sweep-gap-ticks", 5.0);
        for (int index = 0; index < 2; index++) {
            int repeat = index;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                effects.playSweepMelee(player, weapon, repeat);
                hitFront(player, weapon, facing, step.damageMultiplier());
            }, gap * index);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            player.setFallDistance(0.0f);
            player.setVelocity(facing.clone().multiply(-number("hop-back-power", 0.55)).setY(number("hop-up-power", 0.5)));
            effects.playStepTrail(player, weapon, player.getLocation(), facing.clone().multiply(-1.0));
        }, gap + 2L);
    }

    /** Every enemy in front (within the sweep's radius and half angle) takes {@code multiplier} of the weapon's damage. */
    private void hitFront(Player player, RpgItemDefinition weapon, Vector facing, double multiplier) {
        double radius = number("sweep-radius", 3.6);
        double halfAngle = number("sweep-half-angle", 70.0);
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            attackService.strike(player, target, weapon, multiplier);
        }
    }

    // ---- 3: the leap with cuts all around ---------------------------------------------------------------------------

    private void leapSlash(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        Vector view = player.getLocation().getDirection();
        Vector velocity = view.clone().multiply(number("leap-power", 0.95));
        velocity.setY(Math.max(number("leap-up-power", 0.45), view.getY() + 0.3));
        double range = number("leap-radius", 3.0);
        int maxTicks = (int) Math.max(6, Math.round(number("leap-ticks", 16.0)));
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= maxTicks || (age > 4 && player.isOnGround())) {
                    cancel();
                    return;
                }
                player.setFallDistance(0.0f);
                if (age % 2 == 0) {
                    Location center = player.getLocation().add(0.0, 1.0, 0.0);
                    effects.playRandomSlashes(player, weapon, center, range, 2);
                    for (LivingEntity target : around(player, player.getLocation(), range)) {
                        attackService.strike(player, target, weapon, step.damageMultiplier());
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- 4: cuts all around, a little in front ---------------------------------------------------------------------------

    private void surround(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        Vector facing = facing(player);
        int hits = (int) Math.max(2, Math.round(number("surround-hits", 5.0)));
        long gap = (long) Math.max(1, Math.round(number("surround-gap-ticks", 4.0)));
        double range = number("surround-radius", 3.0);
        for (int index = 0; index < hits; index++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                // the middle is a little in front of the player
                Location center = player.getLocation().add(facing.clone().multiply(number("surround-forward", 0.8))).add(0.0, 1.0, 0.0);
                effects.playRandomSlashes(player, weapon, center, range, 3);
                for (LivingEntity target : around(player, center, range)) {
                    attackService.strike(player, target, weapon, step.damageMultiplier());
                }
            }, gap * index);
        }
    }

    /** The enemies within {@code radius} (horizontally; a little more than a body's height up and down) of {@code center}. */
    private java.util.List<LivingEntity> around(Player player, Location center, double radius) {
        java.util.List<LivingEntity> result = new java.util.ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, 2.0, radius)) {
            if (!attackService.isAttackTarget(entity, player)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getLocation().getX() - center.getX();
            double dz = living.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius) continue;
            result.add(living);
        }
        return result;
    }
}
