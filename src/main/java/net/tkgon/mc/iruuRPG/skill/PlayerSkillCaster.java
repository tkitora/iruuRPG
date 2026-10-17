package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * A player casting a skill with the weapon in their hand. The player is not frozen (they can move during the warning),
 * the skill goes where they look, and the damage goes through the normal attack path: the weapon's damage times the
 * skill's {@code damage-rate} (capped by {@code skill-limits.player-damage-rate-max}), with the weapon and the critical
 * hit passed on, so numbers, status effects, class effects and XP credit work as for a normal attack.
 */
public final class PlayerSkillCaster implements SkillCaster {

    private static final double MIN_SEARCH_RADIUS = 14.0;
    private static final double MAX_SEARCH_RADIUS = 45.0;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final Player player;
    private final RpgItemDefinition weapon;
    private final double searchRadius;
    private int cachedTick = -1;
    private List<LivingEntity> cached = List.of();

    public PlayerSkillCaster(JavaPlugin plugin, AttackService attackService, Player player, RpgItemDefinition weapon, SkillSpec spec) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.player = player;
        this.weapon = weapon;
        // the skill never reaches farther than its range / radius (a flying wave can go on a bit): search only there
        this.searchRadius = Math.max(MIN_SEARCH_RADIUS, Math.min(MAX_SEARCH_RADIUS, Math.max(spec.range(), spec.radius()) + 6.0));
    }

    /** Where the skill is aimed: straight ahead of where the player looks, {@code distance} blocks away. */
    public static Location aimPoint(Player player, double distance) {
        Location eye = player.getEyeLocation();
        return eye.clone().add(eye.getDirection().multiply(Math.max(6.0, distance)));
    }

    @Override
    public Player owner() {
        return player;
    }

    @Override
    public World world() {
        return player.getWorld();
    }

    @Override
    public Location location() {
        return player.getLocation();
    }

    @Override
    public Location eye() {
        return player.getEyeLocation();
    }

    @Override
    public boolean valid() {
        return player.isOnline() && !player.isDead();
    }

    @Override
    public Element element() {
        return weapon == null ? Element.WHITE : weapon.element();
    }

    @Override
    public void freeze() {
    }

    @Override
    public void release() {
    }

    @Override
    public void face(Location at) {
        // the player turns by themself
    }

    @Override
    public void swing() {
        player.swingMainHand();
    }

    @Override
    public void lunge(Vector velocity) {
        player.setFallDistance(0.0f);
        player.setVelocity(velocity);
    }

    @Override
    public List<? extends LivingEntity> candidates() {
        // once per tick: every flying piece of the skill asks every tick
        int tick = org.bukkit.Bukkit.getCurrentTick();
        if (tick == cachedTick) return cached;
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), searchRadius, searchRadius, searchRadius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.equals(player) || entity instanceof Player || entity instanceof ArmorStand) continue;
            if (living.isDead() || !attackService.isAttackTarget(living, player)) continue;
            result.add(living);
        }
        cachedTick = tick;
        cached = result;
        return result;
    }

    @Override
    public boolean applyEffects() {
        return true;
    }

    @Override
    public void damage(LivingEntity victim, double rate) {
        if (weapon == null || victim.isDead() || !victim.isValid()) return;
        double cap = Math.max(0.0, plugin.getConfig().getDouble("skill-limits.player-damage-rate-max", 8.0));
        AttackService.AttackDamage attack = attackService.calculateDamage(player, victim, weapon);
        double damage = Math.round(attack.result().damage() * Math.min(rate, cap) * 10.0) / 10.0;
        if (damage <= 0.0) return;
        boolean critical = attack.result().critical();
        AttackType type = weapon.attackType() == AttackType.RANGE ? AttackType.RANGE : AttackType.MELEE;
        boolean applied = type == AttackType.RANGE
                ? attackService.applyRangeDamage(player, victim, damage, weapon, critical)
                : attackService.applyDirectDamage(player, victim, damage, weapon, critical);
        if (applied) attackService.sendDamageDebug(player, type, victim, damage, critical, false);
    }
}
