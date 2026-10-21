package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The loop of the ranger's three kinds of attack (the combos with each kind of weapon are {@link RangerRangeCombo},
 * {@link RangerDeployCombo} and the others): a melee hit powers up the next ranged attack (+10% damage, a faster arrow);
 * a ranged hit makes the player's deployed weapons resonate (2% of their damage on the enemies around each of them); damage
 * from a deployed weapon powers up melee attacks for 5 seconds (+10%, and faster movement while attacking).
 */
public final class RangerMoves {

    private final JavaPlugin plugin;
    private DeployService deployService;
    private final Map<UUID, Long> rangeBoostUntil = new HashMap<>();
    private final Map<UUID, Long> meleeBoostUntil = new HashMap<>();
    private final Map<UUID, Integer> lastResonanceTick = new HashMap<>();

    public RangerMoves(JavaPlugin plugin, AttackService attackService, AttackEffects effects) {
        this.plugin = plugin;
    }

    public void setDeployService(DeployService deployService) {
        this.deployService = deployService;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.ranger." + key, fallback);
    }

    /** Called after every hit of a ranger: the weapon's kind decides which of the three powers up. */
    public void onHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        if (weapon == null) return;
        switch (weapon.attackType()) {
            case MELEE -> grantRangeBoost(attacker);
            case RANGE -> resonate(attacker);
            case DEPLOY -> grantMeleeBoost(attacker);
            default -> {
            }
        }
    }

    /** A melee hit: the next ranged attack is stronger and faster. */
    public void grantRangeBoost(Player player) {
        long now = System.currentTimeMillis();
        Long until = rangeBoostUntil.get(player.getUniqueId());
        boolean fresh = until == null || until < now;
        rangeBoostUntil.put(player.getUniqueId(), now + Math.round(number("range-boost-seconds", 10.0) * 1000.0));
        if (fresh) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.8f);
            ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)), 8, 0.2, 0.2, 0.2, 0.2);
        }
    }

    /** Damage from a deployed weapon: melee attacks are stronger for a while. */
    public void grantMeleeBoost(Player player) {
        long now = System.currentTimeMillis();
        Long until = meleeBoostUntil.get(player.getUniqueId());
        boolean fresh = until == null || until < now;
        meleeBoostUntil.put(player.getUniqueId(), now + Math.round(number("melee-boost-seconds", 5.0) * 1000.0));
        if (fresh) {
            World world = player.getWorld();
            world.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.3f);
            ParticleFx.to(player, world).spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** What a melee hit of {@code player} is multiplied by now (the boost from deployed damage; also speeds the player up). */
    public double meleeMultiplier(Player player) {
        Long until = meleeBoostUntil.get(player.getUniqueId());
        if (until == null) return 1.0;
        if (System.currentTimeMillis() > until) {
            meleeBoostUntil.remove(player.getUniqueId());
            return 1.0;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 0, false, false, true));
        return 1.0 + number("melee-boost-percent", 10.0) / 100.0;
    }

    /** {damage multiplier, arrow speed multiplier} of the next ranged attack; takes the boost. */
    public double[] takeRangeBoostPublic(Player player) {
        Long until = rangeBoostUntil.remove(player.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return new double[]{1.0, 1.0};
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.5f, 1.9f);
        return new double[]{1.0 + number("range-boost-percent", 10.0) / 100.0, number("range-boost-speed", 1.5)};
    }

    /** A ranged hit: every deployed weapon of the player pulses once for 2% of its damage (not more than once in 4 ticks). */
    private void resonate(Player player) {
        if (deployService == null) return;
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastResonanceTick.get(player.getUniqueId());
        if (last != null && tick - last < (int) number("resonance-gap-ticks", 4.0)) return;
        lastResonanceTick.put(player.getUniqueId(), tick);
        deployService.resonate(player, number("resonance-rate", 0.02));
    }
}
