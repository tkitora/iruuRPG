package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The warrior's floating greatswords and chains (t168: the warrior with a ranged weapon of its combo, {@link WarriorSkills}). The swords are item
 * displays of an iron sword, made large (seen by everyone, never saved with the world, all taken away when the plugin stops); they hover behind the
 * warrior's shoulders while they fight, fly at the enemies, fall from the sky and stand planted for a moment. The chains are drawn links of iron
 * between two points; a <b>bound</b> enemy is held to a point by one (it can move only a little way from it: pulled back when it goes farther, not
 * stopped) and takes more from the swords while it is bound.
 */
final class WarriorSwords {

    /** The swords' light: the pale steel of a spectral blade. */
    static final Gradient STEEL = Gradient.of(Color.fromRGB(40, 48, 72), Color.fromRGB(150, 178, 220), Color.fromRGB(240, 248, 255));
    /** The chains' iron. */
    static final Gradient CHAIN = Gradient.of(Color.fromRGB(42, 42, 50), Color.fromRGB(118, 120, 134), Color.fromRGB(210, 214, 224));

    private static final Vector UP = new Vector(0.0, 1.0, 0.0);
    /** How long a sword's model is for a size of 1 (the blade of the sprite, corner to corner). */
    static final double LENGTH = 1.25;

    /** Every sword shown now. */
    private static final Set<ItemDisplay> SHOWN = ConcurrentHashMap.newKeySet();
    /** The swords hovering behind each warrior and until when (ms). */
    private static final Map<UUID, Hover> HOVER = new HashMap<>();
    /** The enemies bound by a chain: until when (ms). */
    private static final Map<UUID, Long> BOUND = new ConcurrentHashMap<>();
    /** The chains that hold an enemy now (one each), so that a new one or a yank can let the old go. */
    private static final Map<UUID, boolean[]> HOLDS = new ConcurrentHashMap<>();

    private WarriorSwords() {
    }

    // ---- one sword -----------------------------------------------------------------------------------------------------------

    /** A sword at {@code at} (its middle), its point toward {@code dir}, {@code size} times an iron sword. */
    static ItemDisplay spawn(Location at, Vector dir, double size) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            spawned.setItemStack(new ItemStack(Material.IRON_SWORD));
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setViewRange(1.5f);
            spawned.setTransformation(shape(dir, size));
        });
        SHOWN.add(display);
        return display;
    }

    /** The turn and size of a sword pointing along {@code dir} (the sprite's blade runs corner to corner: turned upright first). */
    static Transformation shape(Vector dir, double size) {
        Vector d = dir.lengthSquared() < 1.0E-6 ? UP.clone() : dir.clone().normalize();
        Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f((float) d.getX(), (float) d.getY(), (float) d.getZ()))
                .mul(new Quaternionf().rotateZ((float) (-Math.PI / 4.0)));
        float s = (float) size;
        return new Transformation(new Vector3f(), turn, new Vector3f(s, s, s), new Quaternionf());
    }

    /** Moves a sword to {@code to} over {@code ticks} (smoothly on the screen), pointing along {@code dir}. */
    static void move(ItemDisplay display, Location to, Vector dir, double size, int ticks) {
        if (display == null || !display.isValid()) return;
        int duration = Math.max(0, Math.min(59, ticks));
        display.setTeleportDuration(duration);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(duration);
        display.setTransformation(shape(dir, size));
        Location target = to.clone();
        target.setYaw(0.0f);
        target.setPitch(0.0f);
        display.teleport(target);
    }

    /** Shrinks a sword to nothing over {@code ticks}, then takes it away. */
    static void vanish(MoveKit kit, ItemDisplay display, Vector dir, int ticks) {
        if (display == null || !display.isValid()) {
            SHOWN.remove(display);
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(1, ticks));
        display.setTransformation(shape(dir, 0.01));
        kit.later(Math.max(1, ticks), () -> remove(display));
        // (taken away even when the warrior has left: the task of the kit stops then)
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> remove(display), Math.max(1, ticks) + 2L);
    }

    static void remove(ItemDisplay display) {
        if (display == null) return;
        SHOWN.remove(display);
        if (display.isValid()) display.remove();
    }

    /** The plugin stops: every sword goes. */
    static void shutdown() {
        for (ItemDisplay display : SHOWN) if (display.isValid()) display.remove();
        SHOWN.clear();
        for (Hover hover : HOVER.values()) hover.clear();
        HOVER.clear();
        BOUND.clear();
        HOLDS.clear();
    }

    // ---- hovering ----------------------------------------------------------------------------------------------------------------

    /** The two swords behind a warrior's shoulders. */
    private static final class Hover {
        final ItemDisplay[] swords = new ItemDisplay[2];
        long until;

        void clear() {
            for (ItemDisplay sword : swords) remove(sword);
        }
    }

    /**
     * The warrior fights with the swords: two hover behind their shoulders, points up, for {@code seconds} after the last time this is called (a
     * slow bob and sway; they follow the warrior smoothly).
     */
    static void hover(MoveKit kit, double seconds) {
        Player player = kit.player;
        UUID id = player.getUniqueId();
        long until = System.currentTimeMillis() + Math.round(seconds * 1000.0);
        Hover hover = HOVER.get(id);
        if (hover != null) {
            hover.until = until;
            return;
        }
        Hover fresh = new Hover();
        fresh.until = until;
        HOVER.put(id, fresh);
        for (int index = 0; index < 2; index++) fresh.swords[index] = spawn(hoverSpot(player, index, 0), UP, 1.6);
        kit.sound(player.getLocation(), Sfx.SWORD_SUMMON, 0.6f, 1.0f);
        new SafeTask(kit.plugin()) {
            private int age;

            @Override
            protected void tick() {
                age += 2;
                if (!player.isOnline() || player.isDead() || System.currentTimeMillis() > fresh.until || HOVER.get(id) != fresh) {
                    if (HOVER.get(id) == fresh) HOVER.remove(id);
                    for (ItemDisplay sword : fresh.swords) {
                        if (sword != null && sword.isValid()) sword.getWorld().spawnParticle(Particle.END_ROD, sword.getLocation(), 4, 0.2, 0.5, 0.2, 0.02);
                    }
                    fresh.clear();
                    cancel();
                    return;
                }
                for (int index = 0; index < 2; index++) {
                    ItemDisplay sword = fresh.swords[index];
                    if (sword == null || !sword.isValid() || sword.getWorld() != player.getWorld()) {
                        remove(sword);
                        fresh.swords[index] = spawn(hoverSpot(player, index, age), UP, 1.6);
                        continue;
                    }
                    Vector lean = new Vector(0.0, 1.0, 0.0).add(right(player).multiply(index == 0 ? -0.18 : 0.18));
                    move(sword, hoverSpot(player, index, age), lean, 1.6, 2);
                }
            }
        }.runTaskTimer(kit.plugin(), 2L, 2L);
    }

    /** Where hovering sword {@code index} (0 left, 1 right) is: behind the shoulder, bobbing. */
    static Location hoverSpot(Player player, int index, int age) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        Vector side = right(player).multiply(index == 0 ? -0.95 : 0.95);
        double bob = Math.sin(age * 0.12 + index * Math.PI) * 0.12;
        return player.getLocation().add(0.0, 1.75 + bob, 0.0).subtract(facing.multiply(0.55)).add(side);
    }

    /** The middle of hovering sword {@code index}, where a sword sent out leaves from (a little ahead of the hovering one). */
    static Location launch(Player player, int index) {
        Location spot = hoverSpot(player, index, org.bukkit.Bukkit.getCurrentTick());
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        return spot.add(facing.normalize().multiply(0.8));
    }

    static Vector right(Player player) {
        Vector facing = player.getLocation().getDirection().setY(0.0);
        if (facing.lengthSquared() < 1.0E-6) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        return new Vector(-facing.getZ(), 0.0, facing.getX());
    }

    // ---- flying and falling --------------------------------------------------------------------------------------------------------

    /**
     * A sword flies straight from {@code from} to {@code to} ({@code speed} blocks a tick; it stops at a wall): every enemy within {@code width} of
     * its way is given to {@code onHit} once; {@code onEnd} at the end, where the sword shrinks away.
     */
    static void fly(MoveKit kit, Location from, Location to, double size, double speed, double width, Consumer<LivingEntity> onHit, Consumer<Location> onEnd) {
        Vector way = to.toVector().subtract(from.toVector());
        double length = way.length();
        if (length < 0.3) {
            if (onEnd != null) onEnd.accept(from);
            return;
        }
        way.normalize();
        RayTraceResult wall = from.getWorld().rayTraceBlocks(from, way, length, FluidCollisionMode.NEVER, true);
        if (wall != null && wall.getHitPosition() != null) length = Math.max(0.3, wall.getHitPosition().distance(from.toVector()) - 0.2);
        Location end = from.clone().add(way.clone().multiply(length));
        int ticks = Math.max(1, (int) Math.ceil(length / Math.max(0.2, speed)));
        ItemDisplay sword = spawn(from, way, size);
        Set<UUID> struck = new HashSet<>();
        double[] travelled = {0.0};
        double total = length;
        kit.later(1L, () -> move(sword, end, way, size, ticks));
        // (taken away even when the warrior has left: the tasks of the kit stop then)
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> remove(sword), ticks + 30L);
        kit.every(ticks + 2, age -> {
            if (age == 0) return false;
            double before = travelled[0];
            double step = Math.min(total - before, total / ticks);
            Location at = from.clone().add(way.clone().multiply(before));
            for (LivingEntity victim : kit.corridor(at, way, step, width)) {
                if (struck.add(victim.getUniqueId()) && onHit != null) onHit.accept(victim);
            }
            Location tail = at.clone().subtract(way.clone().multiply(size * LENGTH * 0.5));
            kit.fx(at).line(tail, at.clone().add(way.clone().multiply(step)), STEEL, 0.15, 0.85, 1.5, 1.0, 0.3, null, 1);
            travelled[0] = before + step;
            if (travelled[0] >= total - 1.0E-6) {
                if (onEnd != null) onEnd.accept(end);
                vanish(kit, sword, way, 4);
                return true;
            }
            return false;
        });
    }

    /**
     * A sword falls point first from {@code height} blocks over {@code ground} in {@code ticks}, sinks its point in, and stands planted for
     * {@code stay} ticks before it fades; {@code onLand} when it strikes.
     */
    static ItemDisplay fall(MoveKit kit, Location ground, double size, double height, int ticks, int stay, Consumer<Location> onLand) {
        Vector down = new Vector(0.0, -1.0, 0.0);
        double half = size * LENGTH * 0.5;
        Location top = ground.clone().add(0.0, height + half, 0.0);
        Location planted = ground.clone().add(0.0, half - 0.35 * size, 0.0);
        ItemDisplay sword = spawn(top, down, size);
        kit.later(1L, () -> move(sword, planted, down, size, ticks));
        kit.every(ticks + 1, age -> {
            if (age == 0) return false;
            double t = Math.min(1.0, age / (double) ticks);
            Location at = top.clone().add(0.0, (planted.getY() - top.getY()) * t, 0.0);
            kit.fx(at).line(at.clone().add(0.0, half + 1.2, 0.0), at, STEEL, 0.1, 0.7, 1.5, 1.0, 0.35, null, 1);
            return false;
        });
        kit.later(ticks + 1L, () -> {
            if (onLand != null) onLand.accept(ground);
            kit.later(Math.max(1, stay), () -> vanish(kit, sword, down, 6));
        });
        org.bukkit.Bukkit.getScheduler().runTaskLater(kit.plugin(), () -> remove(sword), ticks + Math.max(1, stay) + 20L);
        return sword;
    }

    // ---- chains -------------------------------------------------------------------------------------------------------------------

    /** A chain from {@code from} to {@code to}: links of iron, dark and light by turns, sagging a little. */
    static void chain(MoveKit kit, Location from, Location to, double sag) {
        Fx fx = kit.fx(from);
        if (!fx.active()) return;
        Vector way = to.toVector().subtract(from.toVector());
        double length = way.length();
        if (length < 0.2) return;
        int links = Math.max(2, (int) Math.ceil(length / 0.24));
        for (int index = 0; index <= links; index++) {
            double t = index / (double) links;
            double drop = sag * 4.0 * t * (1.0 - t);
            double x = from.getX() + way.getX() * t;
            double y = from.getY() + way.getY() * t - drop;
            double z = from.getZ() + way.getZ() * t;
            fx.dust(x, y, z, CHAIN, index % 2 == 0 ? 0.35 : 0.85, index % 2 == 0 ? 0.85 : 0.6);
        }
    }

    /** Whether {@code victim} is bound by a chain now. */
    static boolean bound(LivingEntity victim) {
        Long until = BOUND.get(victim.getUniqueId());
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        BOUND.remove(victim.getUniqueId());
        return false;
    }

    /**
     * Binds {@code victim} by a chain to {@code anchor} for {@code ticks}: it can move {@code radius} blocks from it, no farther (pulled back, not
     * stopped). A new chain on the same enemy lets the old one go. {@code onEnd} when the chain lets go (its time, a yank, the enemy dead).
     */
    static void bind(MoveKit kit, LivingEntity victim, Location anchor, double radius, int ticks, Consumer<LivingEntity> onEnd) {
        UUID id = victim.getUniqueId();
        boolean[] old = HOLDS.get(id);
        if (old != null) old[0] = true;
        boolean[] released = {false};
        HOLDS.put(id, released);
        BOUND.put(id, System.currentTimeMillis() + ticks * 50L);
        Location hold = anchor.clone();
        kit.every(ticks + 1, age -> {
            boolean over = released[0] || age >= ticks || !victim.isValid() || victim.isDead() || victim.getWorld() != hold.getWorld();
            if (over) {
                HOLDS.remove(id, released);
                if (!released[0]) BOUND.remove(id);
                if (victim.isValid() && !released[0]) {
                    Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
                    kit.particle(Particle.CRIT, chest, 6, 0.3, 0.1);
                    kit.sound(chest, Sfx.CHAIN_BREAK, 0.5f, 1.2f);
                }
                if (onEnd != null && !released[0]) onEnd.accept(victim);
                return true;
            }
            Vector off = victim.getLocation().toVector().subtract(hold.toVector()).setY(0.0);
            double distance = off.length();
            if (distance > radius && distance > 1.0E-3) {
                Vector back = off.multiply(-1.0 / distance).multiply(Math.min(0.55, 0.12 + (distance - radius) * 0.35));
                Vector velocity = victim.getVelocity();
                victim.setVelocity(new Vector(back.getX(), Math.min(velocity.getY(), 0.1), back.getZ()));
            }
            if (age % 2 == 0) chain(kit, hold, victim.getLocation().add(0.0, victim.getHeight() * 0.55, 0.0), 0.25);
            return false;
        });
    }

    /** Lets the chain on {@code victim} go at once (a yank): true when there was one. */
    static boolean release(LivingEntity victim) {
        boolean[] hold = HOLDS.remove(victim.getUniqueId());
        BOUND.remove(victim.getUniqueId());
        if (hold == null) return false;
        hold[0] = true;
        return true;
    }

    static void forget(UUID player) {
        Hover hover = HOVER.remove(player);
        if (hover != null) hover.clear();
    }
}
