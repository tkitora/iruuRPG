package net.tkgon.mc.iruuRPG.tools.procedural.ornate;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t282: the bones by the pipe model, the shape field, and the blocks of the ornate tree (no gaps, no thickening, the decoration's limits, the shading). */
class OrnateBuilderTest {

    static SkeletonGenerator.Params bones(int style) {
        return new SkeletonGenerator.Params(style, 9.0, 2.0, 0.5, 5, 6.0, 3, 4, 8.0, 40.0, 0.0, 0.5, 2.3);
    }

    static OrnateBuilder.Params params(int style) {
        return new OrnateBuilder.Params(bones(style), 1.5, 0.5, 0.6, 0.3, 0.6, 135.0, 2.5, 0.3, 85.0);
    }

    @Test
    void theRadiiFollowThePipeModel() {
        for (int style = 0; style < 4; style++) {
            Skeleton s = SkeletonGenerator.grow(bones(style), new Random(7));
            assertEquals(2.0, s.radius(0), 1.0e-9, "the base is as thick as the setting");
            double n = 2.3;
            double roots = 0.0;
            for (int node = 0; node < s.size(); node++) {
                List<Integer> children = s.children(node);
                if (node == 0) {
                    for (int child : children) if (s.kind(child) == Skeleton.ROOT) roots += Math.pow(s.startRadius(child), n);
                    continue;
                }
                if (children.size() < 2) continue;
                double sum = 0.0;
                for (int child : children) sum += Math.pow(s.startRadius(child), n);
                // at every fork the parent's r^n is the sum of its children's
                assertEquals(Math.pow(s.radius(node), n), sum, 1.0e-6 * Math.max(1.0, sum), "style " + style + " node " + node);
            }
            if (roots > 0.0) assertEquals(2.0, Math.pow(roots, 1.0 / n), 1.0e-6, "the roots together are as thick as the trunk");
            // and every branch is thinner than what it grows from
            for (int node = 1; node < s.size(); node++) assertTrue(s.startRadius(node) <= s.radius(s.parent(node)) + 1.0e-9);
        }
    }

    @Test
    void branchesOfDifferentLimbsKeepTwoBlocksApartAwayFromTheirFork() {
        for (int style : new int[]{0, 3}) {
            for (long seed = 1; seed <= 4; seed++) {
                Skeleton s = SkeletonGenerator.grow(bones(style), new Random(seed));
                for (int a = 1; a < s.size(); a++) {
                    if (s.kind(a) != Skeleton.BRANCH) continue;
                    for (int b = a + 1; b < s.size(); b++) {
                        if (s.kind(b) != Skeleton.BRANCH || s.limb(a) == s.limb(b)) continue;
                        int fork = common(s, a, b);
                        if (path(s, a, fork) <= Skeleton.NEAR_FORK + 1.5 || path(s, b, fork) <= Skeleton.NEAR_FORK + 1.5) continue;
                        double d = Math.sqrt(sq(s.x(a) - s.x(b)) + sq(s.y(a) - s.y(b)) + sq(s.z(a) - s.z(b)));
                        assertTrue(d >= 2.0 - 1.0e-9, "style " + style + " seed " + seed + " nodes " + a + " " + b + ": " + d);
                    }
                }
            }
        }
    }

    private static int common(Skeleton s, int a, int b) {
        Set<Integer> up = new HashSet<>();
        for (int node = a; node >= 0; node = s.parent(node)) up.add(node);
        for (int node = b; node >= 0; node = s.parent(node)) if (up.contains(node)) return node;
        return 0;
    }

    private static double path(Skeleton s, int node, int to) {
        double length = 0.0;
        for (int at = node; at != to && at >= 0; at = s.parent(at)) length += s.length(at);
        return length;
    }

    @Test
    void onlyTheFootIsSmoothed() {
        Skeleton s = SkeletonGenerator.grow(bones(0), new Random(3));
        Field sharp = new Field(s, 0.5, 0.0);
        Field smooth = new Field(s, 0.5, 1.5);
        // high up, at the forks, the two are the same
        for (int node = 1; node < s.size(); node++) {
            if (s.y(node) < 8.0 || s.children(node).size() < 2 || s.radius(node) < 0.5) continue;
            assertEquals(sharp.distance(s.x(node) + 0.3, s.y(node) + 0.4, s.z(node)), smooth.distance(s.x(node) + 0.3, s.y(node) + 0.4, s.z(node)), 1.0e-9);
        }
        // at the foot, between the trunk and a root, the smooth one is fuller
        boolean fuller = false;
        for (int node : s.children(0)) {
            if (s.kind(node) != Skeleton.ROOT) continue;
            double x = (s.x(node) + 0.5) / 2.0 + 0.4;
            double z = (s.z(node) + 0.5) / 2.0 + 0.4;
            if (smooth.distance(x, 0.6, z) < sharp.distance(x, 0.6, z) - 1.0e-6) fuller = true;
        }
        assertTrue(fuller);
    }

    @Test
    void everyStyleGrowsAJoinedTreeWithoutGapsOrThickening() {
        for (int style = 0; style < 4; style++) {
            for (long seed = 1; seed <= 3; seed++) {
                OrnateBuilder.Result result = OrnateBuilder.build(params(style), new Random(seed));
                Map<Long, OrnateBuilder.Wood> wood = result.wood();
                String name = "style " + style + " seed " + seed + " " + result.stats();
                assertTrue(wood.size() > 100, name);
                assertTrue(result.stats().thin() > 0, name);
                int unjoined = 0;
                for (Map.Entry<Long, Long> link : result.links().entrySet()) {
                    OrnateBuilder.Wood child = wood.get(link.getKey());
                    OrnateBuilder.Wood parent = wood.get(link.getValue());
                    int face = BlockShapes.face(OrnateBuilder.unpackX(link.getKey()) - OrnateBuilder.unpackX(link.getValue()),
                            OrnateBuilder.unpackY(link.getKey()) - OrnateBuilder.unpackY(link.getValue()), OrnateBuilder.unpackZ(link.getKey()) - OrnateBuilder.unpackZ(link.getValue()));
                    assertTrue(face >= 0, name);
                    if (parent == null) continue;
                    if (!BlockShapes.joined(parent.shape(), face, child.shape())) unjoined++;
                    // a branch never gets thicker than what it grows from (a knot is the one exception)
                    if (child.shape().kind != BlockShapes.Kind.FULL) assertTrue(child.shape().kind.rank() <= parent.shape().kind.rank(), name + " " + parent.shape() + " -> " + child.shape());
                }
                assertTrue(unjoined <= Math.max(1, result.links().size() / 100), name + ": " + unjoined + " gaps");
                for (OrnateBuilder.Wood block : wood.values()) assertTrue(block.level() >= -2 && block.level() <= 2);
                if (style == 3) assertTrue(result.leaves().isEmpty(), name);
                else assertFalse(result.leaves().isEmpty(), name);
            }
        }
    }

    @Test
    void theThinWoodFollowsTheTable() {
        int fences = 0;
        int upright = 0;
        for (long seed = 1; seed <= 3; seed++) {
            OrnateBuilder.Result result = OrnateBuilder.build(params(0), new Random(seed));
            for (Map.Entry<Long, Long> link : result.links().entrySet()) {
                OrnateBuilder.Wood child = result.wood().get(link.getKey());
                // (a block over the one it grows from, at a tip: a fence by the table)
                boolean above = OrnateBuilder.unpackY(link.getKey()) == OrnateBuilder.unpackY(link.getValue()) + 1;
                if (above && child.shape().kind == BlockShapes.Kind.FENCE) fences++;
                if (above) upright++;
            }
        }
        assertTrue(fences > upright / 4, fences + " fences of " + upright);
    }

    @Test
    void theDecorationStaysOnTheThickTrunkAndRoots() {
        for (long seed = 1; seed <= 3; seed++) {
            OrnateBuilder.Result result = OrnateBuilder.build(params(0), new Random(seed));
            int decorations = 0;
            for (Map.Entry<Long, OrnateBuilder.Wood> entry : result.wood().entrySet()) {
                OrnateBuilder.Wood block = entry.getValue();
                if (!block.decoration()) continue;
                decorations++;
                assertTrue(block.part() == Skeleton.TRUNK || block.part() == Skeleton.ROOT, block.toString());
                assertTrue(OrnateBuilder.unpackY(entry.getKey()) >= 0);
                // next to a full block of the wood it decorates
                boolean beside = false;
                for (int face = 0; face < 6; face++) {
                    int[] step = BlockShapes.STEPS[face];
                    OrnateBuilder.Wood next = result.wood().get(OrnateBuilder.pack(OrnateBuilder.unpackX(entry.getKey()) + step[0], OrnateBuilder.unpackY(entry.getKey()) + step[1],
                            OrnateBuilder.unpackZ(entry.getKey()) + step[2]));
                    if (next != null && !next.decoration() && next.shape().kind != BlockShapes.Kind.EMPTY) beside = true;
                }
                assertTrue(beside);
            }
            assertTrue(decorations > 0, "seed " + seed);
            // none without decoration
            OrnateBuilder.Params plain = new OrnateBuilder.Params(bones(0), 1.5, 0.0, 0.6, 0.3, 0.6, 135.0, 2.5, 0.3, 85.0);
            assertTrue(OrnateBuilder.build(plain, new Random(seed)).wood().values().stream().noneMatch(OrnateBuilder.Wood::decoration));
        }
    }

    @Test
    void theShadingSpreadsOverTheLevelsAndCanBeTurnedOff() {
        OrnateBuilder.Result shaded = OrnateBuilder.build(params(0), new Random(5));
        Set<Integer> levels = new HashSet<>();
        for (OrnateBuilder.Wood block : shaded.wood().values()) if (block.part() == Skeleton.TRUNK) levels.add(block.level());
        assertTrue(levels.size() >= 3, levels.toString());
        OrnateBuilder.Params flat = new OrnateBuilder.Params(bones(0), 1.5, 0.5, 0.0, 0.0, 0.0, 135.0, 2.5, 0.3, 85.0);
        assertTrue(OrnateBuilder.build(flat, new Random(5)).wood().values().stream().allMatch(block -> block.level() == 0));
    }

    @Test
    void theSameSeedMakesTheSameTree() {
        OrnateBuilder.Result a = OrnateBuilder.build(params(2), new Random(11));
        OrnateBuilder.Result b = OrnateBuilder.build(params(2), new Random(11));
        assertEquals(a.wood(), b.wood());
        assertEquals(a.leaves(), b.leaves());
    }

    @Test
    void theWalkGoesThroughFacesOnly() {
        List<double[]> cells = OrnateBuilder.walk(0.3, 0.2, 0.7, 4.6, 3.9, -2.2);
        for (int index = 1; index < cells.size(); index++) {
            double[] a = cells.get(index - 1);
            double[] b = cells.get(index);
            assertEquals(1.0, Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]), 1.0e-9);
        }
        double[] last = cells.get(cells.size() - 1);
        assertEquals(4, (int) last[0]);
        assertEquals(3, (int) last[1]);
        assertEquals(-3, (int) last[2]);
    }

    private static double sq(double value) {
        return value * value;
    }
}
