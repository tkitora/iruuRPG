package net.tkgon.mc.iruuRPG.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out where every bone is for one moment of an animation, with the conventions of Blockbench's own viewer: the rotation of a bone is its static rotation plus the animation's
 * (the animation's x and y turned the other way), applied in the order z, y, x about the bone's origin; the animation's position is added to the origin (x the other way); the scale
 * multiplies. A bone's matrix is its parent's times its own (model units, absolute model coordinates).
 */
final class Pose {

    private Pose() {
    }

    /** The values of one bone at one moment: rotation, position and scale (null for a channel the animation does not have). */
    static final class Values {
        double[] rotation;
        double[] position;
        double[] scale;
    }

    /** The values of every animated bone (by uuid) of {@code animation} at {@code time} seconds. */
    static Map<String, Values> sample(BbModel.Animation animation, double time) {
        Map<String, Values> result = new HashMap<>();
        if (animation == null) return result;
        for (Map.Entry<String, Map<String, List<BbModel.Keyframe>>> bone : animation.channels.entrySet()) {
            Values values = new Values();
            values.rotation = value(bone.getValue().get("rotation"), time);
            values.position = value(bone.getValue().get("position"), time);
            values.scale = value(bone.getValue().get("scale"), time);
            result.put(bone.getKey(), values);
        }
        return result;
    }

    private static double[] value(List<BbModel.Keyframe> keys, double time) {
        if (keys == null || keys.isEmpty()) return null;
        if (time <= keys.get(0).time() || keys.size() == 1) return keys.get(0).value();
        BbModel.Keyframe last = keys.get(keys.size() - 1);
        if (time >= last.time()) return last.value();
        for (int index = 0; index + 1 < keys.size(); index++) {
            BbModel.Keyframe a = keys.get(index);
            BbModel.Keyframe b = keys.get(index + 1);
            if (time < a.time() || time > b.time()) continue;
            double span = b.time() - a.time();
            double t = span < 1.0E-9 ? 1.0 : (time - a.time()) / span;
            if (a.interpolation().equals("step")) return a.value();
            if (a.interpolation().equals("catmullrom") && keys.size() > 2) {
                BbModel.Keyframe before = keys.get(Math.max(0, index - 1));
                BbModel.Keyframe after = keys.get(Math.min(keys.size() - 1, index + 2));
                double[] out = new double[3];
                for (int axis = 0; axis < 3; axis++) out[axis] = catmull(before.value()[axis], a.value()[axis], b.value()[axis], after.value()[axis], t);
                return out;
            }
            return new double[]{a.value()[0] + (b.value()[0] - a.value()[0]) * t, a.value()[1] + (b.value()[1] - a.value()[1]) * t, a.value()[2] + (b.value()[2] - a.value()[2]) * t};
        }
        return last.value();
    }

    private static double catmull(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    /** The matrix of every bone of the model with these animation values (absolute model coordinates, model units). */
    static Map<BbModel.Bone, Matrix4f> bones(BbModel model, Map<String, Values> animated) {
        Map<BbModel.Bone, Matrix4f> world = new HashMap<>();
        for (BbModel.Bone root : model.roots) walk(root, new Matrix4f(), animated, world);
        return world;
    }

    private static void walk(BbModel.Bone bone, Matrix4f parent, Map<String, Values> animated, Map<BbModel.Bone, Matrix4f> world) {
        Values values = animated.get(bone.uuid);
        double[] rotation = bone.rotation.clone();
        double px = 0.0;
        double py = 0.0;
        double pz = 0.0;
        double sx = 1.0;
        double sy = 1.0;
        double sz = 1.0;
        if (values != null) {
            if (values.rotation != null) {
                rotation[0] -= values.rotation[0];
                rotation[1] -= values.rotation[1];
                rotation[2] += values.rotation[2];
            }
            if (values.position != null) {
                px = -values.position[0];
                py = values.position[1];
                pz = values.position[2];
            }
            if (values.scale != null) {
                sx = values.scale[0];
                sy = values.scale[1];
                sz = values.scale[2];
            }
        }
        Matrix4f local = new Matrix4f()
                .translate((float) (bone.origin[0] + px), (float) (bone.origin[1] + py), (float) (bone.origin[2] + pz))
                .rotate(euler(rotation))
                .scale((float) sx, (float) sy, (float) sz)
                .translate((float) -bone.origin[0], (float) -bone.origin[1], (float) -bone.origin[2]);
        Matrix4f matrix = new Matrix4f(parent).mul(local);
        world.put(bone, matrix);
        for (BbModel.Bone child : bone.children) walk(child, matrix, animated, world);
    }

    /** Euler angles in degrees applied in the order z, y, x (Blockbench's order for groups). */
    static Quaternionf euler(double[] degrees) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(degrees[2]), (float) Math.toRadians(degrees[1]), (float) Math.toRadians(degrees[0]));
    }
}
