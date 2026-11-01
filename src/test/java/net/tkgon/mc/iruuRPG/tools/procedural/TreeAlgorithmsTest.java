package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeAlgorithmsTest {

    private static TreeSettings tree(String algorithm) {
        TreeSettings settings = new TreeSettings();
        settings.setAlgorithm(algorithm);
        assertEquals(algorithm, settings.algorithm());
        return settings;
    }

    private static Map<TreePart, Integer> count(Map<Long, TreePart> blocks) {
        Map<TreePart, Integer> counts = new EnumMap<>(TreePart.class);
        for (TreePart part : blocks.values()) counts.merge(part, 1, Integer::sum);
        return counts;
    }

    @Test
    void everyAlgorithmGrowsTheSameTreeFromTheSameSeed() {
        for (String algorithm : TreeSettings.ALGORITHMS) {
            TreeSettings settings = tree(algorithm);
            Map<Long, TreePart> a = TreeGenerator.build(settings, 9L, null);
            assertEquals(a, TreeGenerator.build(settings, 9L, null), algorithm);
            Map<TreePart, Integer> parts = count(a);
            assertTrue(parts.getOrDefault(TreePart.TRUNK, 0) > 20, algorithm + " " + parts);
            assertTrue(parts.getOrDefault(TreePart.LEAF, 0) > 50, algorithm + " " + parts);
        }
        assertTrue(count(TreeGenerator.build(tree("canopy"), 9L, null)).containsKey(TreePart.BRANCH));
        assertTrue(count(TreeGenerator.build(tree("canopy"), 9L, null)).containsKey(TreePart.ROOT));
        assertTrue(count(TreeGenerator.build(tree("tiered"), 9L, null)).containsKey(TreePart.ROOT));
        assertFalse(count(TreeGenerator.build(tree("palm"), 9L, null)).containsKey(TreePart.BRANCH));
    }

    @Test
    void snowOnlyWhenItIsOn() {
        TreeSettings settings = tree("tiered");
        assertFalse(count(TreeGenerator.build(settings, 3L, null)).containsKey(TreePart.SNOW));
        settings.set(TreeSettings.Key.TIER_SNOW, 1);
        assertTrue(settings.on(TreeSettings.Key.TIER_SNOW));
        assertTrue(count(TreeGenerator.build(settings, 3L, null)).getOrDefault(TreePart.SNOW, 0) > 10);
    }

    @Test
    void betaLeavesAreNoFurtherThanFourStepsFromALog() {
        TreeSettings settings = tree("canopy");
        settings.set(TreeSettings.Key.CANOPY_LEAF_SIZE, 4.0);
        settings.set(TreeSettings.Key.CANOPY_BETA_LEAVES, 1);
        Map<Long, TreePart> blocks = TreeGenerator.build(settings, 5L, null);
        Set<Long> logs = blocks.entrySet().stream().filter(e -> e.getValue().shade() < 0).map(Map.Entry::getKey).collect(Collectors.toSet());
        int leaves = 0;
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            if (entry.getValue().shade() < 0) continue;
            leaves++;
            int x = TreeVoxelizer.unpackX(entry.getKey()), y = TreeVoxelizer.unpackY(entry.getKey()), z = TreeVoxelizer.unpackZ(entry.getKey());
            boolean near = false;
            for (long log : logs) {
                if (Math.abs(TreeVoxelizer.unpackX(log) - x) + Math.abs(TreeVoxelizer.unpackY(log) - y) + Math.abs(TreeVoxelizer.unpackZ(log) - z) <= 4) {
                    near = true;
                    break;
                }
            }
            assertTrue(near, "a leaf far from the logs");
        }
        settings.set(TreeSettings.Key.CANOPY_BETA_LEAVES, 0);
        assertTrue(count(TreeGenerator.build(settings, 5L, null)).get(TreePart.LEAF) > leaves, "beta leaves drop some leaves");
    }

    @Test
    void willowVinesHangAndTheDeepestSettingStillEnds() {
        TreeSettings settings = tree("canopy");
        int plain = count(TreeGenerator.build(settings, 4L, null)).get(TreePart.LEAF);
        settings.set(TreeSettings.Key.CANOPY_WILLOW, 1);
        assertTrue(count(TreeGenerator.build(settings, 4L, null)).get(TreePart.LEAF) > plain);
        // 5 branches a node, 6 deep: 19531 branches, grown from a stack (no recursion)
        settings.set(TreeSettings.Key.CANOPY_DEPTH, 6);
        settings.set(TreeSettings.Key.CANOPY_BRANCHES, 5);
        settings.set(TreeSettings.Key.CANOPY_WILLOW, 0);
        assertTrue(TreeGenerator.build(settings, 4L, null).size() > 1000);
    }

    @Test
    void aPalmThatSplitsHasTwoCrowns() {
        TreeSettings settings = tree("palm");
        int one = count(TreeGenerator.build(settings, 6L, null)).get(TreePart.LEAF);
        settings.set(TreeSettings.Key.PALM_SPLIT_CHANCE, 1.0);
        int two = count(TreeGenerator.build(settings, 6L, null)).get(TreePart.LEAF);
        assertTrue(two > one * 1.5, one + " " + two);
        settings.set(TreeSettings.Key.PALM_ROOT_REACH, 2.0);
        assertTrue(count(TreeGenerator.build(settings, 6L, null)).containsKey(TreePart.ROOT));
    }

    @Test
    void theLeafGradientRunsUpEachClump() {
        TreeSettings settings = tree("canopy");
        settings.set(TreeSettings.Key.CANOPY_LEAF_SIZE, 3.0);
        settings.setLeafGradient(List.of("azalea_leaves", "oak_leaves", "flowering_azalea_leaves"));
        Map<TreePart, Integer> parts = count(TreeGenerator.build(settings, 2L, null));
        assertTrue(parts.containsKey(TreePart.LEAF) && parts.containsKey(TreePart.LEAF_2) && parts.containsKey(TreePart.LEAF_3), parts.toString());
        assertFalse(parts.containsKey(TreePart.LEAF_4));
    }

    @Test
    void blocksCanCarryStates() {
        assertEquals("minecraft:oak_log[axis=x]", TreeSettings.blockName("oak_log[axis=x]"));
        assertEquals("SPRUCE_LEAVES", TreeSettings.blockName("minecraft:spruce_leaves"));
        assertNull(TreeSettings.blockName("oak_log[axis=x"));
        assertNull(TreeSettings.blockName("air"));
        assertEquals(Material.OAK_LOG, TreeSettings.materialOf("minecraft:oak_log[axis=x]"));
        TreeSettings settings = tree("canopy");
        settings.setBlock(TreePart.BRANCH, "birch_log[axis=z]");
        assertEquals("minecraft:birch_log[axis=z]", TreeSettings.fromMap(settings.toMap(), "x").block(TreePart.BRANCH));
        assertTrue(TreeSettings.Key.CANOPY_WILLOW.toggle());
        assertFalse(TreeSettings.Key.CANOPY_DEPTH.toggle());
    }

    @Test
    void theSampleTreesLoadAndFitThePlacingLimits() throws IOException {
        File folder = new File("src/main/resources/trees");
        List<String> names = Files.readAllLines(new File(folder, "samples.txt").toPath()).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        assertTrue(names.size() >= 9, names.toString());
        for (String name : names) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(folder, name + ".yml"));
            assertNotNull(yaml.getConfigurationSection("settings"), name);
            TreeSettings settings = TreeSettings.fromMap(new LinkedHashMap<>(yaml.getConfigurationSection("settings").getValues(false)), name);
            assertTrue(settings.kind().tree(), name);
            assertEquals(yaml.getString("settings.algorithm"), settings.algorithm(), name);
            for (TreePart part : TreePart.values()) {
                String block = settings.block(part);
                if (block != null) assertNotNull(TreeSettings.materialOf(block), name + " " + block);
            }
            assertEquals(yaml.getStringList("settings.leaf-gradient").size(), settings.leafGradient().size(), name);
            for (long seed = 1; seed <= 3; seed++) {
                int size = TreeGenerator.build(settings, seed, null).size();
                // (the placing limit of the server is 60000 blocks, the preview's 40000)
                assertTrue(size > 150 && size < 40000, name + " seed " + seed + ": " + size);
            }
        }
    }
}
