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
 * The assassin's four heavy attacks (see {@link MeleeCombo}): 0 a step in the walking direction (the right click),
 * 1 a thrust on the spot (the skill {@code combat.combo.assassin.thrust-skill}, so a skill can call it too), 2 a sweep
 * forwards and then a small hop back, 3 a dash forwards with cuts all around on the way.
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
            default -> whirlDash(player, weapon, step);
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

    // ---- 2: the sweep and the hop back ------------------------------------------------------------------------------

    private void sweepHop(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        Vector facing = facing(player);
        effects.playSweepMelee(player, weapon);
        double radius = number("sweep-radius", 3.6);
        double halfAngle = number("sweep-half-angle", 70.0);
        for (LivingEntity target : around(player, player.getLocation(), radius)) {
            Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0.0);
            if (to.lengthSquared() > 1.0 && Math.toDegrees(facing.angle(to)) > halfAngle) continue;
            attackService.strike(player, target, weapon, step.damageMultiplier());
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            player.setFallDistance(0.0f);
            player.setVelocity(facing.clone().multiply(-number("hop-back-power", 0.55)).setY(number("hop-up-power", 0.5)));
            effects.playStepTrail(player, weapon, player.getLocation(), facing.clone().multiply(-1.0));
        }, 4L);
    }

    // ---- 3: the dash with cuts all around -----------------------------------------------------------------------------

    private void whirlDash(Player player, RpgItemDefinition weapon, MeleeCombo.Step step) {
        Vector direction = facing(player);
        int ticks = (int) Math.max(4, Math.round(number("whirl-ticks", 10.0)));
        double power = number("whirl-power", 0.85);
        double radius = number("whirl-radius", 2.8);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.3f);
        new SafeTask(plugin) {
            private int age;

            @Override
            protected void tick() {
                if (!player.isOnline() || player.isDead() || age >= ticks) {
                    cancel();
                    return;
                }
                Vector velocity = direction.clone().multiply(power);
                velocity.setY(Math.max(-0.4, Math.min(0.15, player.getVelocity().getY())));
                player.setVelocity(velocity);
                player.setFallDistance(0.0f);
                if (age % 2 == 0) {
                    effects.playSpinPulse(player, weapon, radius, age);
                    for (LivingEntity target : around(player, player.getLocation(), radius)) {
                        attackService.strike(player, target, weapon, step.damageMultiplier());
                    }
                }
                age++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
