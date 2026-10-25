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
    private final List<Matrix4f> last = new ArrayList<>();

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
    private double speed;

    ModelInstance(BbModel model, LivingEntity base, double scale, int period, Material baseItem, NamespacedKey partKey, boolean hideBase) {
        this.model = model;
        this.base = base;
        this.scale = scale;
        this.period = Math.max(1, period);
        if (hideBase) base.setInvisible(true);
        for (BbModel.Part part : model.parts) {
            ItemStack item = new ItemStack(baseItem);
            ItemMeta meta = item.getItemMeta();
            meta.setCustomModelData(part.customModelData);
            item.setItemMeta(meta);
            ItemDisplay display = base.getWorld().spawn(base.getLocation(), ItemDisplay.class, spawned -> {
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

    LivingEntity base() {
        return base;
    }

    boolean dying() {
        return dying;
    }

    boolean hasAnimation(String name) {
        return model.animations.containsKey(name);
    }

    double length(String name) {
        BbModel.Animation animation = model.animations.get(name);
        return animation == null ? 0.0 : animation.length;
    }

    /** Plays the animation once (over the walk or the idle). */
    boolean play(String name, int tick) {
        BbModel.Animation animation = model.animations.get(name);
        if (animation == null || dying) return false;
        action = name;
        actionStart = tick;
        actionLength = animation.length;
        return true;
    }

    /** The mob died: the model stays where it is and plays {@code death} (if it has it). */
    void die(int tick) {
        if (dying) return;
        dying = true;
        dyingStart = tick;
        BbModel.Animation animation = model.animations.get("death");
        dyingLength = animation == null ? 0.0 : animation.length;
        frozen = base.getLocation();
    }

    /** The death animation is over (and a moment after). */
    boolean finished(int tick) {
        return dying && (tick - dyingStart) / 20.0 >= dyingLength + 0.6;
    }

    /** Moves the displays to the pose of this tick. */
    void update(int tick) {
        Location at = dying && frozen != null ? frozen : base.getLocation();
        if (previous != null && tick > previousTick && previous.getWorld() == at.getWorld()) {
            speed = Math.hypot(at.getX() - previous.getX(), at.getZ() - previous.getZ()) / (tick - previousTick);
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
            String wanted = speed > 0.04 && model.animations.containsKey("walk") ? "walk" : "idle";
            if (!wanted.equals(locomotion)) {
                locomotion = wanted;
                locomotionStart = tick;
            }
            name = locomotion;
            start = locomotionStart;
        }
        BbModel.Animation animation = model.animations.get(name);
        double time = (tick - start) / 20.0;
        if (animation != null) {
            time = animation.loop.equals("loop") && !dying && action == null ? time % Math.max(0.05, animation.length) : Math.min(time, animation.length);
        }
        Map<BbModel.Bone, Matrix4f> bones = Pose.bones(model, Pose.sample(animation, time));
        // the model's front is north (-z); the mob's yaw 0 faces south (+z)
        float turn = (float) (Math.PI - Math.toRadians(at.getYaw()));
        float size = (float) (scale / 16.0);
        for (int index = 0; index < model.parts.size(); index++) {
            BbModel.Part part = model.parts.get(index);
            Matrix4f matrix = new Matrix4f(bones.get(part.bone));
            double[] pivot = part.pivot();
            matrix.translate((float) pivot[0], (float) pivot[1], (float) pivot[2]);
            if (part.cube != null) matrix.rotate(Pose.euler(part.cube.rotation));
            Matrix4f world = new Matrix4f().scale(size).rotateY(turn).mul(matrix);
            ItemDisplay display = displays.get(index);
            if (!display.isValid()) continue;
            if (!world.equals(last.get(index), 1.0E-4f)) {
                last.get(index).set(world);
                Vector3f translation = world.getTranslation(new Vector3f());
                Vector3f scaling = world.getScale(new Vector3f());
                Quaternionf rotation = world.getNormalizedRotation(new Quaternionf());
                display.setTransformation(new Transformation(translation, rotation, scaling, new Quaternionf()));
                display.setInterpolationDelay(0);
            }
            if (display.getLocation().distanceSquared(at) > 1.0E-4 || display.getWorld() != at.getWorld()) display.teleport(at);
        }
    }

    void remove() {
        for (ItemDisplay display : displays) {
            if (display.isValid()) display.remove();
        }
        displays.clear();
    }

    /** Gives the mob its own body back. */
    void showBase() {
        if (base.isValid()) base.setInvisible(false);
    }
}
