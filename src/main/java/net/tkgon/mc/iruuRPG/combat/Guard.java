package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What keeps a player from taking damage for a while, in the order {@link #absorb} applies it: a moment of invulnerability
 * ({@link #invulnerable}: the ranger's dash), a guard that cuts the damage by a share for a few ticks ({@link #grant}: the
 * warrior's blows), and the shields turning round the player ({@link #gainShields}: the warrior's bulwark; each damage takes one and
 * is cut by 70%, they last 10 seconds at most). All of it runs out by itself.
 */
public final class Guard {

    /** Steel in the middle going to a cool blue at the edge. */
    private static final Gradient SHIELD = Gradient.of(Color.fromRGB(235, 240, 250), Color.fromRGB(170, 195, 235), Color.fromRGB(95, 150, 255));

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> shields = new HashMap<>();
    /** When each player's shields run out. */
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    /** {until (ms), share cut (0 - 1)} of each player's guard. */
    private final Map<UUID, double[]> guards = new HashMap<>();
    private SafeTask shieldTask;
    private int shieldTicks;

    public Guard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private double number(String key, double fallback) {
        return plugin.getConfig().getDouble("combat.combo.warrior." + key, fallback);
    }

    // ---- guard and invulnerability -----------------------------------------------------------------------------------------

    /** Cuts the damage {@code player} takes by {@code share} (1.0 = none at all) for {@code ticks} ticks; a stronger or longer one already on stays. */
    public void grant(Player player, double share, int ticks) {
        long until = System.currentTimeMillis() + ticks * 50L;
        double[] now = guards.get(player.getUniqueId());
        if (now != null && now[0] > System.currentTimeMillis()) {
            until = Math.max(until, (long) now[0]);
            share = Math.max(share, now[1]);
        }
        guards.put(player.getUniqueId(), new double[]{until, Math.min(1.0, Math.max(0.0, share))});
    }

    /** No damage at all for {@code ticks} ticks. */
    public void invulnerable(Player player, int ticks) {
        grant(player, 1.0, ticks);
    }

    private double guardShare(Player player) {
        double[] now = guards.get(player.getUniqueId());
        if (now == null) return 0.0;
        if (now[0] < System.currentTimeMillis()) {
            guards.remove(player.getUniqueId());
            return 0.0;
        }
        return now[1];
    }

    // ---- shields -----------------------------------------------------------------------------------------------------------------

    public int shieldCount(Player player) {
        return shields.getOrDefault(player.getUniqueId(), 0);
    }

    /** Puts up shields (the number and the seconds from {@code combat.combo.warrior}: {@code shield-count} 3, {@code shield-seconds} 10). */
    public void gainShields(Player player) {
        shields.put(player.getUniqueId(), (int) number("shield-count", 3.0));
        shieldUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(number("shield-seconds", 10.0) * 1000.0));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0f, 0.9f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT_MAGIC, player.getLocation().add(0.0, 1.0, 0.0), 20, 0.6, 0.6, 0.6, 0.3);
        ensureShieldTask();
    }

    /** Adds one shield (up to the number a bulwark gives). */
    public void addShield(Player player) {
        int max = (int) number("shield-count", 3.0);
        int count = shieldCount(player);
        if (count >= max) {
            shieldUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(number("shield-seconds", 10.0) * 1000.0));
            return;
        }
        shields.put(player.getUniqueId(), count + 1);
        shieldUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.round(number("shield-seconds", 10.0) * 1000.0));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 0.7f, 1.1f);
        ensureShieldTask();
    }

    /**
     * Damage {@code player} is about to take, after the guard and the shields: nothing in a moment of invulnerability; cut by the
     * guard's share; and with a shield up cut by 70% more and the shield is lost. Returns the damage to take.
     */
    public double absorb(Player player, double damage) {
        if (damage <= 0.0) return damage;
        double share = guardShare(player);
        if (share >= 1.0) {
            ParticleFx.to(player, player.getWorld()).spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 6, 0.4, 0.4, 0.4, 0.2);
            return 0.0;
        }
        damage *= 1.0 - share;
        int count = shieldCount(player);
        if (count <= 0) return damage;
        if (shieldExpired(player)) {
            dropShields(player.getUniqueId());
            return damage;
        }
        World world = player.getWorld();
        if (count - 1 <= 0) dropShields(player.getUniqueId());
        else shields.put(player.getUniqueId(), count - 1);
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.9f, 1.2f);
        ParticleFx.to(player, world).spawnParticle(Particle.CRIT, player.getLocation().add(0.0, 1.0, 0.0), 14, 0.5, 0.5, 0.5, 0.3);
        return damage * (1.0 - number("shield-reduction", 0.7));
    }

    private boolean shieldExpired(Player player) {
        Long until = shieldUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() > until;
    }

    private void dropShields(UUID id) {
        shields.remove(id);
        shieldUntil.remove(id);
    }

    private void ensureShieldTask() {
        if (shieldTask != null) return;
        shieldTask = new SafeTask(plugin) {
            @Override
            protected void tick() {
                shieldTicks++;
                if (shields.isEmpty()) {
                    cancel();
                    shieldTask = null;
                    return;
                }
                for (UUID id : new ArrayList<>(shields.keySet())) {
                    Player owner = plugin.getServer().getPlayer(id);
                    if (owner == null || !owner.isOnline() || owner.isDead()) {
                        dropShields(id);
                        continue;
                    }
                    if (shieldExpired(owner)) {
                        dropShields(id);
                        owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 0.6f, 0.8f);
                        continue;
                    }
                    int count = shields.get(id);
                    Location center = owner.getLocation().add(0.0, 1.1, 0.0);
                    World world = owner.getWorld();
                    Fx fxShield = Fx.of(owner, world, center);
                    for (int index = 0; index < count; index++) {
                        double angle = shieldTicks * 0.22 + Math.PI * 2.0 * index / count;
                        Vector out = new Vector(Math.cos(angle), 0.0, Math.sin(angle));
                        Location at = center.clone().add(out.clone().multiply(1.3));
                        Vector tangent = new Vector(-out.getZ(), 0.0, out.getX());
                        // a small plate standing on the circle: the middle of it is steel, the edges a cool blue
                        for (double h = -0.35; h <= 0.35; h += 0.12) {
                            for (double w = -0.24; w <= 0.24; w += 0.12) {
                                Location p = at.clone().add(tangent.clone().multiply(w)).add(0.0, h, 0.0);
                                fxShield.dust(p.getX(), p.getY(), p.getZ(), SHIELD, Math.min(1.0, Math.abs(w) / 0.24 * 0.6 + Math.abs(h) / 0.35 * 0.4), 1.1);
                            }
                        }
                    }
                }
            }
        };
        shieldTask.runTaskTimer(plugin, 0L, 2L);
    }

    public void forget(UUID id) {
        dropShields(id);
        guards.remove(id);
    }

    public void shutdown() {
        if (shieldTask != null) shieldTask.cancel();
        shieldTask = null;
        shields.clear();
        shieldUntil.clear();
        guards.clear();
    }
}
