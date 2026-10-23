package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The placement of a deployed weapon's combo: the right click that starts it fixes a place (where the player stood and the way they faced) that stays for 10 s plus the
 * effect time. Every later right click is the next attack of the combo (1 to 5, and after the fifth the first again), made at that place and not where the player is now; the
 * left click is the heavy attack, once for the whole placement; sneaking and left click takes the placement away. The cooldown (10 s, shorter with a cooldown reduction) starts
 * with the placement and keeps a new place from being set, also after this one was taken away. A faint ring and column mark the place, going dim as the time runs out.
 */
final class DeployHold {

    /** One placement. */
    static final class Hold {
        final Location spot;
        final Vector facing;
        final long endsAt;
        final Gradient palette;
        /** The attack of the combo made last (0 before the first). */
        int stage;
        boolean heavyUsed;

        Hold(Location spot, Vector facing, long endsAt, Gradient palette) {
            this.spot = spot;
            this.facing = facing;
            this.endsAt = endsAt;
            this.palette = palette;
        }
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Hold> holds = new HashMap<>();
    /** When each player may set a new place (ms). */
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private BukkitTask task;
    private int age;

    DeployHold(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The placement the player has, or null (a placement whose time is over is gone). */
    Hold active(Player player) {
        Hold hold = holds.get(player.getUniqueId());
        if (hold != null && System.currentTimeMillis() >= hold.endsAt) {
            holds.remove(player.getUniqueId());
            return null;
        }
        return hold;
    }

    long cooldownMillis(Player player) {
        Long ready = readyAt.get(player.getUniqueId());
        return ready == null ? 0L : Math.max(0L, ready - System.currentTimeMillis());
    }

    Hold create(Player player, Location spot, Vector facing, int lifeTicks, int cooldownTicks, Gradient palette) {
        long now = System.currentTimeMillis();
        Hold hold = new Hold(spot.clone(), facing.clone(), now + lifeTicks * 50L, palette);
        holds.put(player.getUniqueId(), hold);
        readyAt.put(player.getUniqueId(), now + cooldownTicks * 50L);
        ensureTask();
        return hold;
    }

    /** Takes the placement away (the cooldown stays). True when there was one. */
    boolean remove(Player player) {
        return holds.remove(player.getUniqueId()) != null;
    }

    void forget(UUID id) {
        holds.remove(id);
        readyAt.remove(id);
    }

    void shutdown() {
        holds.clear();
        readyAt.clear();
        if (task != null) task.cancel();
        task = null;
    }

    private void ensureTask() {
        if (task != null) return;
        task = new SafeTask(plugin) {
            @Override
            protected void tick() {
                age++;
                long now = System.currentTimeMillis();
                for (UUID id : new ArrayList<>(holds.keySet())) {
                    Hold hold = holds.get(id);
                    Player owner = plugin.getServer().getPlayer(id);
                    if (hold == null || owner == null || !owner.isOnline() || now >= hold.endsAt) {
                        holds.remove(id);
                        if (owner != null && owner.isOnline() && hold != null) owner.sendMessage("[iruuRPG] 設置が消えました。");
                        continue;
                    }
                    draw(owner, hold, now);
                }
                if (holds.isEmpty()) {
                    cancel();
                    task = null;
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /** The ring and the column at the place; dimmer in the last two seconds. */
    private void draw(Player owner, Hold hold, long now) {
        ParticleFx.mark(owner);
        Location at = hold.spot;
        Fx fx = Fx.of(owner, at.getWorld(), at);
        if (!fx.active()) return;
        double left = (hold.endsAt - now) / 1000.0;
        double fade = left < 2.0 ? Math.max(0.2, left / 2.0) : 1.0;
        fx.flatRing(at.clone().add(0.0, 0.12, 0.0), 0.9, 20, hold.palette, 0.6 * fade, 0.05, 0.9);
        if (age % 2 == 0) {
            for (double y = 0.2; y <= 1.8; y += 0.4) fx.dust(at.getX(), at.getY() + y, at.getZ(), hold.palette, fade * (0.3 + 0.7 * y / 1.8), 1.0);
        }
    }
}
