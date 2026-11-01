package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FractalTreeTest {

    private static TreeSettings fractal() {
        TreeSettings settings = new TreeSettings();
        assertEquals("fractal", settings.algorithm());
        assertTrue(settings.fractal());
        return settings;
    }

    private static double meanY(Map<Long, TreePart> blocks, TreePart part) {
        double sum = 0;
        int count = 0;
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            if (entry.getValue() != part) continue;
            sum += TreeVoxelizer.unpackY(entry.getKey());
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    @Test
    void theSameSeedGrowsTheSameTree() {
        TreeSettings settings = fractal();
        Map<Long, TreePart> a = TreeGenerator.build(settings, 42L, null);
        Map<Long, TreePart> b = TreeGenerator.build(settings, 42L, null);
        assertEquals(a, b);
        assertTrue(a.containsValue(TreePart.TRUNK));
        assertTrue(a.containsValue(TreePart.LEAF));
        assertTrue(a.size() > 200, "blocks " + a.size());
    }

    @Test
    void theTreeStaysInsideItsBoxAndIsScaledToFitNotCut() {
        TreeSettings settings = fractal();
        settings.set(TreeSettings.Key.F_TRUNK_LENGTH, 30);
        settings.set(TreeSettings.Key.HEIGHT, 20);
        settings.set(TreeSettings.Key.WIDTH, 14);
        FractalTree tree = new FractalTree(settings, new Random(7));
        tree.grow();
        tree.fit(settings.height(), settings.width() / 2.0);
        for (double[] w : tree.wood) {
            assertTrue(w[1] + w[3] <= 20.0 + 1.0E-6, "wood above the box");
            assertTrue(Math.abs(w[0]) + w[3] <= 7.0 + 1.0E-6 && Math.abs(w[2]) + w[3] <= 7.0 + 1.0E-6, "wood outside the box");
        }
        // (a wide box: the height decides, and the tree comes up to it, not cut off far below)
        settings.set(TreeSettings.Key.WIDTH, 60);
        Map<Long, TreePart> blocks = TreeGenerator.build(settings, 7L, null);
        int top = blocks.keySet().stream().mapToInt(TreeVoxelizer::unpackY).max().orElse(0);
        assertTrue(top < 20 && top >= 16, "top " + top);
    }

    @Test
    void aHugeSettingIsBoundedByTheBudgetAndNeedsNoRecursion() {
        TreeSettings settings = fractal();
        settings.set(TreeSettings.Key.F_LEVELS, 8);
        settings.set(TreeSettings.Key.F_BRANCHES, 6);
        settings.set(TreeSettings.Key.F_FOLIAGE_LEVELS, 4);
        settings.set(TreeSettings.Key.F_FOLIAGE_BRANCHES, 6);
        settings.set(TreeSettings.Key.HEIGHT, 80);
        settings.set(TreeSettings.Key.WIDTH, 60);
        FractalTree tree = new FractalTree(settings, new Random(3));
        tree.grow();
        assertTrue(tree.branches() <= FractalTree.MAX_BRANCHES, "branches " + tree.branches());
        assertTrue(tree.twigs() <= FractalTree.MAX_TWIGS, "twigs " + tree.twigs());
        // (6 a node: 1 + 6 + ... + 6^4 = 1555 fit, 6^5 more would not; the tree stays whole and even, not one deep corner)
        assertEquals(4, tree.mainLevels());
        assertEquals(1555, tree.branches());
        assertEquals(1, tree.foliageLevels());   // 20000 twigs over 1296 tips: 6 each, not 6 + 36
        assertEquals(3, FractalTree.levelsWithin(8, 2, 1, 15));
        assertEquals(8, FractalTree.levelsWithin(8, 1, 1, 100));
    }

    @Test
    void gravityMakesTheBranchesHang() {
        TreeSettings up = fractal();
        up.set(TreeSettings.Key.F_GRAVITY, -0.1);
        up.set(TreeSettings.Key.LEAF_RADIUS, 0);
        up.set(TreeSettings.Key.HEIGHT, 80);
        up.set(TreeSettings.Key.WIDTH, 60);
        TreeSettings down = up.copy();
        down.set(TreeSettings.Key.F_GRAVITY, 0.25);
        double rising = meanY(TreeGenerator.build(up, 5L, null), TreePart.TWIG);
        double hanging = meanY(TreeGenerator.build(down, 5L, null), TreePart.TWIG);
        assertTrue(hanging < rising - 1.0, "twigs at " + hanging + " with gravity, " + rising + " without");
    }

    @Test
    void theLeafGradientRunsFromTheBottomOfAClumpToItsTop() {
        TreeSettings settings = fractal();
        settings.setLeafGradient(List.of("azalea_leaves", "oak_leaves", "flowering_azalea_leaves"));
        assertEquals(3, settings.leafShades());
        Map<Long, TreePart> blocks = TreeGenerator.build(settings, 11L, null);
        double low = meanY(blocks, TreePart.LEAF);
        double middle = meanY(blocks, TreePart.LEAF_2);
        double high = meanY(blocks, TreePart.LEAF_3);
        assertTrue(low < middle && middle < high, low + " " + middle + " " + high);
        assertTrue(!blocks.containsValue(TreePart.LEAF_4));
        assertEquals(TreePart.LEAF, TreePart.leafAt(0.0, 3, 0.0));
        assertEquals(TreePart.LEAF_3, TreePart.leafAt(1.0, 3, 0.0));
        assertEquals(TreePart.LEAF, TreePart.leafAt(0.9, 1, 0.0));
    }

    @Test
    void theChosenBlocksAreSavedWithTheSettings() {
        TreeSettings settings = fractal();
        settings.setBlock(TreePart.TRUNK, "spruce_log");
        settings.setBlock(TreePart.TWIG, "not_a_block");
        settings.setLeafGradient(List.of("potted_fern", "azalea_leaves", "fern", "short_grass", "stick"));
        assertEquals("SPRUCE_LOG", settings.block(TreePart.TRUNK));
        assertNull(settings.block(TreePart.TWIG));
        // (a stick is no block and is left out; a potted fern is a block though no item)
        assertEquals(List.of("POTTED_FERN", "AZALEA_LEAVES", "FERN", "SHORT_GRASS"), settings.leafGradient());
        TreeSettings loaded = TreeSettings.fromMap(settings.toMap(), "x");
        assertEquals("SPRUCE_LOG", loaded.block(TreePart.TRUNK));
        assertEquals(settings.leafGradient(), loaded.leafGradient());
        TreeSettings copy = settings.copy();
        copy.removeLeafShade(0);
        assertEquals(4, settings.leafGradient().size());
        assertEquals(3, copy.leafGradient().size());
    }
}
