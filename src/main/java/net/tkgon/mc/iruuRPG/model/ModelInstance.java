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
    private final boolean hideBase;

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
        this.hideBase = hideBase;
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
            String wanted = speed > 0.04 && model.find("walk", random) != null ? "walk" : "idle";
            if (!wanted.equals(locomotion)) {
                locomotion = wanted;
                locomotionStart = tick;
            }
            name = locomotion;
            start = locomotionStart;
        }
        BbModel.Animation animation = dying ? deathAnimation : action != null ? actionAnimation : model.find(name, random);
        double time = (tick - start) / 20.0;
        if (animation != null) {
            time = animation.loop.equals("loop") && !dying && action == null ? time % Math.max(0.05, animation.length) : Math.min(time, animation.length);
        }
        Map<BbModel.Bone, Matrix4f> bones = Pose.bones(model, Pose.sample(animation, time));
        // the model's front is north (-z); the mob's yaw 0 faces south (+z)
        float turn = (float) (Math.PI - Math.toRadians(at.getYaw()));
        float size = (float) scale;
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
            Matrix4f world = new Matrix4f().scale(size).rotateY(turn).mul(matrix);
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
            if (display.getLocation().distanceSquared(at) > 1.0E-4 || display.getWorld() != at.getWorld()) display.teleport(at);
        }
    }

    void remove() {
        for (ItemDisplay display : displays) {
            if (display.isValid()) display.remove();
        }
        displays.clear();
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
