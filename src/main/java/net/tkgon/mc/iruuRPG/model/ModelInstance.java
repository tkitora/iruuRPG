package net.tkgon.mc.iruuRPG.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One model on one mob: an item display for every part of the model, kept on the mob's place and turned with it, and moved by the animation that is playing. The mob's own body is
 * hidden. Animations: a one-shot action (attack, skill, ...) while one is playing; else {@code walk} while the mob moves and {@code idle} while it stands; {@code death} when it dies
 * (the model stays and plays it, then goes). The displays are not saved with the world: they are made again from the mob after a restart.
 */
final class ModelInstance {

    private final BbModel model;
    private final LivingEntity base;
    private final double scale;
    private final int period;
    private final List<ItemDisplay> displays = new ArrayList<>();

    /** The display entities of the model now. */
    List<org.bukkit.entity.Entity> parts() {
        return new ArrayList<>(displays);
    }
    private final List<Matrix4f> last = new ArrayList<>();
    private final boolean hideBase;
    /** The mob's name and life above the model (an invisible mob's own name is not drawn by the game). */
    private org.bukkit.entity.TextDisplay nameplate;
    private net.kyori.adventure.text.Component shownName;

    private String locomotion = "idle";
    private int locomotionStart;
    private String action;
    private int actionStart;
    private double actionLength;
    private boolean dying;
    private int dyingStart;
    private double dyingLength;
    private Location frozen;
    private Location previous;
    private int previousTick;
    /** How fast the mob goes over the ground (blocks a second, smoothed). */
    private double speed;
    /** Where the walk is in its animation (seconds of the animation): it runs faster or slower with the mob's speed. */
    private double walkTime;
    /** The speed a model's walk is made for (blocks a second): the walk plays at the mob's speed divided by this. */
    private final double walkSpeed;
    /** What the model faces instead of the way the mob's body goes (a mob circling its target), and until when. */
    private org.bukkit.entity.Entity facing;
    private int facingUntil;
    /** The way the model was drawn last (degrees), and until when it still turns smoothly back to the body after facing something. */
    private float shownYaw = Float.NaN;
    private int blendUntil;
    /** How long the model takes to go up in smoke after its death animation (seconds; 0: it stays a moment and goes as before), and whether the smoke has come. */
    private double vanishSeconds;
    private boolean smoked;

    ModelInstance(BbModel model, LivingEntity base, double scale, int period, Material baseItem, NamespacedKey partKey, boolean hideBase, double walkSpeed) {
        this.walkSpeed = walkSpeed > 0.0 ? walkSpeed : 1.0;
        this.model = model;
        this.base = base;
        this.scale = scale;
        this.period = Math.max(1, period);
        this.hideBase = hideBase;
        if (hideBase) base.setInvisible(true);
        for (BbModel.Part part : model.parts) {
            ItemStack item = new ItemStack(baseItem);
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(part.customModelData);
            item.setItemMeta(meta);
            Location start = base.getLocation();
            start.setYaw(0.0f);
            start.setPitch(0.0f);
            ItemDisplay display = base.getWorld().spawn(start, ItemDisplay.class, spawned -> {
                spawned.setItemStack(item);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                spawned.setBillboard(Display.Billboard.FIXED);
                spawned.setInterpolationDuration(this.period);
                spawned.setTeleportDuration(Math.min(59, this.period));
                spawned.setViewRange(1.0f);
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setSilent(true);
                spawned.setInvulnerable(true);
                spawned.getPersistentDataContainer().set(partKey, PersistentDataType.STRING, base.getUniqueId().toString());
            });
            displays.add(display);
            last.add(new Matrix4f().scale(Float.NaN));
        }
    }

    /** How high above the feet the top of the model is (blocks). */
    double top() {
        return model.height() / 16.0 * scale;
    }

    LivingEntity base() {
        return base;
    }

    boolean dying() {
        return dying;
    }

    private final java.util.Random random = new java.util.Random();
    private BbModel.Animation actionAnimation;
    private BbModel.Animation deathAnimation;

    boolean hasAnimation(String name) {
        return model.find(name, random) != null;
    }

    double length(String name) {
        BbModel.Animation animation = model.find(name, random);
        return animation == null ? 0.0 : animation.length;
    }

    /** Plays the animation once (over the walk or the idle). */
    boolean play(String name, int tick) {
        BbModel.Animation animation = model.find(name, random);
        if (animation == null || dying) return false;
        action = name;
        actionAnimation = animation;
        actionStart = tick;
        actionLength = animation.length;
        return true;
    }

    /** Whether the mob is in the air now: it has no gravity (a flying mob) and its model has a {@code fly} animation, which is then its walk and its idle. */
    private boolean airborne() {
        return !base.hasGravity() && model.named("fly") != null;
    }

    /** The mob flinches: {@code hurt}, or in the air {@code fly_hurt} (a hurt made standing would pull the model down to the ground); false when there is none. */
    boolean hurt(int tick) {
        if (airborne()) return model.named("fly_hurt") != null && play("fly_hurt", tick);
        return play("hurt", tick);
    }

    /** Whether a one-shot animation is playing (a hurt does not cut an attack short). */
    boolean busy(int tick) {
        return dying || (action != null && (tick - actionStart) / 20.0 < actionLength);
    }

    /** The mob died: the model stays where it is and plays {@code death} (if it has it). */
    void die(int tick) {
        if (dying) return;
        dying = true;
        dyingStart = tick;
        deathAnimation = model.find("death", random);
        dyingLength = deathAnimation == null ? 0.0 : deathAnimation.length;
        frozen = base.getLocation();
        frozen.setYaw(base.getBodyYaw());
    }

    /** The death animation is over (and a moment after). */
    boolean finished(int tick) {
        return dying && (tick - dyingStart) / 20.0 >= dyingLength + (vanishSeconds > 0.0 ? vanishSeconds + 0.1 : 0.6);
    }

    /** At the end of its death animation the model shrinks away into smoke over {@code seconds} (0: no smoke). */
    void vanish(double seconds) {
        vanishSeconds = Math.max(0.0, seconds);
    }

    /** How big the model still is (1 until the death animation is over, then down to nearly 0 while it goes up in smoke). */
    private float fade(Location at, int tick) {
        if (!dying || vanishSeconds <= 0.0) return 1.0f;
        double over = (tick - dyingStart) / 20.0 - dyingLength;
        if (over < 0.0) return 1.0f;
        double tall = Math.max(0.5, top());
        double wide = Math.max(0.4, tall * 0.3);
        org.bukkit.World world = at.getWorld();
        if (world == null) return 0.02f;
        Location middle = at.clone().add(0.0, tall * 0.5, 0.0);
        if (!smoked) {
            smoked = true;
            int puffs = (int) Math.max(20, Math.min(120, tall * 25));
            world.spawnParticle(org.bukkit.Particle.SMOKE_LARGE, middle, puffs, wide, tall * 0.35, wide, 0.03);
            world.spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, middle, puffs, wide, tall * 0.4, wide, 0.06);
            world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0.0, tall * 0.3, 0.0), Math.max(4, puffs / 8), wide * 0.7, tall * 0.2, wide * 0.7, 0.01);
            world.playSound(middle, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.6f);
        } else {
            // (a little more rises while it shrinks)
            world.spawnParticle(org.bukkit.Particle.SMOKE_LARGE, middle, 4, wide * 0.6, tall * 0.25, wide * 0.6, 0.02);
        }
        return (float) Math.max(0.02, 1.0 - over / vanishSeconds);
    }

    /** Keeps the model turned toward {@code target} until {@code untilTick} (null: back to the body). */
    void face(org.bukkit.entity.Entity target, int untilTick) {
        facing = target;
        facingUntil = target == null ? 0 : untilTick;
    }

    /**
     * The way to draw the model: toward what it is told to face while that lasts, else the body's way. Going into and out of facing it turns at most 40 degrees a tick
     * (the body and the target can be a quarter turn apart), at other times it follows the body as it is.
     */
    private float facingYaw(Location at, float body, int tick) {
        float wanted = body;
        if (facing != null && tick <= facingUntil && facing.isValid() && facing.getWorld() == at.getWorld()) {
            double dx = facing.getLocation().getX() - at.getX();
            double dz = facing.getLocation().getZ() - at.getZ();
            if (dx * dx + dz * dz > 1.0E-4) wanted = (float) Math.toDegrees(Math.atan2(-dx, dz));
            blendUntil = tick + 10;
        } else {
            facing = null;
        }
        if (Float.isNaN(shownYaw) || tick > blendUntil) {
            shownYaw = wanted;
            return wanted;
        }
        float delta = ((wanted - shownYaw) % 360.0f + 540.0f) % 360.0f - 180.0f;
        float most = 40.0f * Math.max(1, Math.min(10, tick - previousTick));
        shownYaw += Math.max(-most, Math.min(most, delta));
        return shownYaw;
    }

    /** Moves the displays to the pose of this tick. */
    void update(int tick) {
        Location at = dying && frozen != null ? frozen : base.getLocation();
        // the way the mob's body faces (not its head, which looks at the target and up and down)
        float yaw = dying && frozen != null ? frozen.getYaw() : base.getBodyYaw();
        if (!dying) yaw = facingYaw(at, yaw, tick);
        double elapsed = 0.0;
        if (previous != null && tick > previousTick && previous.getWorld() == at.getWorld()) {
            elapsed = (tick - previousTick) / 20.0;
            double now = Math.hypot(at.getX() - previous.getX(), at.getZ() - previous.getZ()) / elapsed;
            // (smoothed: a step of the path finder is uneven from one update to the next)
            speed = elapsed > 1.0 ? now : speed + (now - speed) * 0.5;
        }
        previous = at.clone();
        previousTick = tick;

        String name;
        int start;
        if (dying) {
            name = "death";
            start = dyingStart;
        } else if (action != null && (tick - actionStart) / 20.0 < actionLength) {
            name = action;
            start = actionStart;
        } else {
            action = null;
            String wanted = airborne() ? "fly" : speed > 0.3 && model.find("walk", random) != null ? "walk" : "idle";
            if (!wanted.equals(locomotion)) {
                locomotion = wanted;
                locomotionStart = tick;
                walkTime = 0.0;
            } else if (wanted.equals("walk")) {
                // the walk is made for walkSpeed blocks a second: at twice that it plays twice as fast, so the feet keep up with the ground
                walkTime += Math.min(elapsed, 1.0) * Math.max(0.25, Math.min(4.0, speed / walkSpeed));
            }
            name = locomotion;
            start = locomotionStart;
        }
        BbModel.Animation animation = dying ? deathAnimation : action != null ? actionAnimation : model.find(name, random);
        double time = !dying && action == null && name.equals("walk") ? walkTime : (tick - start) / 20.0;
        if (animation != null) {
            time = animation.loop.equals("loop") && !dying && action == null ? time % Math.max(0.05, animation.length) : Math.min(time, animation.length);
        }
        Map<BbModel.Bone, Matrix4f> bones = Pose.bones(model, Pose.sample(animation, time));
        // the model's front is north (-z); the mob's yaw 0 faces south (+z)
        float turn = (float) (Math.PI - Math.toRadians(yaw));
        // the displays themselves stand unturned at the mob's feet: the turn is all in their transformation (a display turned as well would be turned twice, and tilted by
        // the mob's pitch)
        Location stand = at.clone();
        stand.setYaw(0.0f);
        stand.setPitch(0.0f);
        float size = (float) scale;
        updateNameplate(stand);
        // going up in smoke: every part shrinks toward the middle of the model
        float fade = fade(at, tick);
        float middle = (float) (top() * 0.5);
        for (int index = 0; index < model.parts.size(); index++) {
            BbModel.Part part = model.parts.get(index);
            Matrix4f matrix = new Matrix4f(bones.get(part.bone));
            double[] pivot = part.pivot();
            matrix.translate((float) pivot[0], (float) pivot[1], (float) pivot[2]);
            if (part.cube != null) matrix.rotate(Pose.euler(part.cube.rotation));
            matrix.translate((float) part.offset[0], (float) part.offset[1], (float) part.offset[2]);
            if (part.shrink != 1.0) matrix.scale((float) (1.0 / part.shrink));
            // The display draws its item model at block size already (16 model units = 1 block), so the turn and the scale of the pose apply as they are; only where the part
            // stands (the translation, in model units) is turned into blocks.
            Vector3f offset = matrix.getTranslation(new Vector3f()).mul(1.0f / 16.0f);
            matrix.setTranslation(offset);
            Matrix4f world = new Matrix4f().translate(0.0f, middle * (1.0f - fade), 0.0f).scale(size * fade).rotateY(turn).mul(matrix);
            ItemDisplay display = displays.get(index);
            if (!display.isValid()) continue;
            if (!world.equals(last.get(index), 1.0E-4f)) {
                last.get(index).set(world);
                Vector3f translation = world.getTranslation(new Vector3f());
                Vector3f scaling = world.getScale(new Vector3f());
                Quaternionf rotation = world.getNormalizedRotation(new Quaternionf());
                // (the game turns an item display's model half round about the vertical before it draws it; turning it back here keeps the cubes where the model has them)
                display.setTransformation(new Transformation(translation, rotation, scaling, new Quaternionf().rotationY((float) Math.PI)));
                display.setInterpolationDelay(0);
            }
            Location now = display.getLocation();
            if (now.getWorld() != stand.getWorld() || now.distanceSquared(stand) > 1.0E-4 || now.getYaw() != 0.0f || now.getPitch() != 0.0f) display.teleport(stand);
        }
    }

    void remove() {
        for (ItemDisplay display : displays) {
            if (display.isValid()) display.remove();
        }
        displays.clear();
        removeNameplate();
    }

    private void removeNameplate() {
        if (nameplate != null && nameplate.isValid()) nameplate.remove();
        nameplate = null;
        shownName = null;
    }

    /** Keeps the nameplate above the model with the mob's name (the name the game gives it: the plugin's name with the level and the life). */
    private void updateNameplate(Location stand) {
        net.kyori.adventure.text.Component name = base.customName();
        if (!hideBase || dying || name == null) {
            removeNameplate();
            return;
        }
        Location at = stand.clone().add(0.0, top() + 0.35, 0.0);
        if (nameplate == null || !nameplate.isValid()) {
            nameplate = base.getWorld().spawn(at, org.bukkit.entity.TextDisplay.class, spawned -> {
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
                spawned.setDefaultBackground(true);
                spawned.setShadowed(false);
                spawned.setSeeThrough(false);
                spawned.setTeleportDuration(Math.min(59, this.period));
                spawned.setPersistent(false);
                spawned.setGravity(false);
                spawned.setInvulnerable(true);
            });
            shownName = null;
        }
        if (!name.equals(shownName)) {
            nameplate.text(name);
            shownName = name;
        }
        if (nameplate.getLocation().distanceSquared(at) > 1.0E-4) nameplate.teleport(at);
    }

    /** Gives the mob its own body back (and shows its equipment again to the players near it). */
    void showBase() {
        if (!base.isValid()) return;
        base.setInvisible(false);
        if (base.getEquipment() == null) return;
        for (org.bukkit.entity.Player player : base.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(base.getLocation()) > 64.0 * 64.0) continue;
            for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                player.sendEquipmentChange(base, slot, base.getEquipment().getItem(slot));
            }
        }
    }

    /**
     * Hides what the mob holds and wears from the player (an invisible mob still shows its equipment): the player is told the slots are empty. The mob keeps them (its damage
     * and the vanilla behavior that needs a held bow do not change); the server tells a player the real ones again when they change or the mob comes into view, so this is sent
     * again then (see ModelModule).
     */
    void hideEquipment(org.bukkit.entity.Player player) {
        if (!hideBase || !base.isValid() || base.getEquipment() == null) return;
        org.bukkit.inventory.ItemStack empty = new org.bukkit.inventory.ItemStack(Material.AIR);
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) player.sendEquipmentChange(base, slot, empty);
    }
}
