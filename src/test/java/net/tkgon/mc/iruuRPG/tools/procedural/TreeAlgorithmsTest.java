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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            // (t252: a thin tree's trunk is one block thick; t258: and short, it soon splits)
            // (the silhouette trees: a small round tree of 11 by default, its logs partly lying)
            assertTrue(parts.getOrDefault(TreePart.TRUNK, 0) > (algorithm.equals("thin") ? 3 : algorithm.equals("silhouette") ? 8 : 20), algorithm + " " + parts);
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

    @Test
    void theBranchesOfATierStartAtSeveralHeights() {
        // (t242) no stagger: all at the tier's height, no random number drawn
        java.util.Random random = new java.util.Random(3);
        assertTrue(java.util.Arrays.stream(TieredTree.heights(random, 12, 4, 5, 0.0, 8, 27)).allMatch(y -> y == 12));
        assertTrue(random.nextInt() == new java.util.Random(3).nextInt());
        // spread over the band round it, more than one height, kept above the stump and under the top
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            int[] heights = TieredTree.heights(new java.util.Random(seed), 12, 4, 5, 0.7, 8, 27);
            java.util.Set<Integer> tier = new java.util.HashSet<>();
            for (int y : heights) {
                assertTrue(y >= 11 && y <= 13, y + " seed " + seed);
                tier.add(y);
            }
            assertTrue(tier.size() >= 2, "seed " + seed);
            seen.addAll(tier);
            // at the stump the band is moved up, not cut: no tier bunched on the stump
            int[] low = TieredTree.heights(new java.util.Random(seed), 8, 4, 5, 0.7, 8, 27);
            assertTrue(java.util.Arrays.stream(low).filter(y -> y == 8).count() <= 2, java.util.Arrays.toString(low));
        }
        // (a band of 4 x 0.7 = 2.8 round 12: 11 to 13)
        assertEquals(java.util.Set.of(11, 12, 13), seen);
    }

    @Test
    void aThinTreeHasAThinTrunkAndBranchesOfWallsFencesAndSlabs() {
        // (t252) one block thick, its branches in posts and halves, leaves over the tips
        for (long seed = 1; seed <= 5; seed++) {
            Map<Long, TreePart> blocks = TreeGenerator.build(tree("thin"), seed, null);
            Map<TreePart, Integer> counts = count(blocks);
            String what = "seed " + seed + " " + counts;
            for (TreePart part : new TreePart[]{TreePart.TRUNK, TreePart.BRANCH, TreePart.BRANCH_WALL, TreePart.BRANCH_FENCE, TreePart.BRANCH_SLAB, TreePart.BRANCH_SLAB_TOP}) {
                assertTrue(counts.getOrDefault(part, 0) > 0, part + " " + what);
            }
            assertTrue(blocks.values().stream().filter(part -> part.shade() >= 0).count() > 100, what);
            // the trunk low down: one block a layer (two where it steps over)
            for (int y = 0; y <= 3; y++) {
                int layer = y;
                long trunk = blocks.entrySet().stream().filter(entry -> entry.getValue() == TreePart.TRUNK && TreeVoxelizer.unpackY(entry.getKey()) == layer).count();
                assertTrue(trunk >= 1 && trunk <= 2, "y " + y + " " + trunk + " " + what);
            }
        }
    }

    @Test
    void theLeavesOfAThinTreeKeepARoomRoundTheWood() {
        // (t252) the more room asked for, the fewer leaves touch the branches (those on the tips stay, the clumps hang on them)
        long[] touching = new long[2];
        for (int run = 0; run < 2; run++) {
            TreeSettings settings = tree("thin");
            settings.set(TreeSettings.Key.THIN_CLEARANCE, run == 0 ? 0.0 : 2.0);
            Map<Long, TreePart> blocks = TreeGenerator.build(settings, 3L, null);
            for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
                if (entry.getValue().shade() < 0) continue;
                int x = TreeVoxelizer.unpackX(entry.getKey());
                int y = TreeVoxelizer.unpackY(entry.getKey());
                int z = TreeVoxelizer.unpackZ(entry.getKey());
                for (int[] step : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    TreePart next = blocks.get(TreeVoxelizer.pack(x + step[0], y + step[1], z + step[2]));
                    if (next != null && next.shade() < 0) {
                        touching[run]++;
                        break;
                    }
                }
            }
        }
        assertTrue(touching[1] * 5 < touching[0] * 4, touching[0] + " / " + touching[1]);
    }

    @Test
    void fencesAndWallsOfAThinTreeJoinTheWoodNextToThem() {
        // a post between two logs east and west, a fence to the north, a leaf to the south
        Map<Long, TreePart> blocks = new java.util.HashMap<>();
        blocks.put(TreeVoxelizer.pack(1, 0, 0), TreePart.TRUNK);
        blocks.put(TreeVoxelizer.pack(-1, 0, 0), TreePart.BRANCH);
        blocks.put(TreeVoxelizer.pack(0, 0, 1), TreePart.LEAF);
        boolean[] joined = ThinTree.joined(blocks, 0, 0, 0);
        assertArrayEquals(new boolean[]{false, true, false, true}, joined);
        // straight through with nothing over it: no post; with wood over it: a post
        assertFalse(ThinTree.post(blocks, joined, 0, 0, 0));
        blocks.put(TreeVoxelizer.pack(0, 1, 0), TreePart.BRANCH_FENCE);
        assertTrue(ThinTree.post(blocks, joined, 0, 0, 0));
        blocks.put(TreeVoxelizer.pack(0, 0, -1), TreePart.BRANCH_FENCE);
        assertTrue(ThinTree.joined(blocks, 0, 0, 0)[0]);
    }

    @Test
    void aThinTreeSplitsSoonThinsOutAndIsDressedWithStairs() {
        // (t258) the trunk splits at its height: more than one block of wood a layer just over it; stairs at the foot (the foot is heavy)
        TreeSettings settings = tree("thin");
        int split = settings.getInt(TreeSettings.Key.THIN_HEIGHT);
        int forked = 0;
        int footed = 0;
        for (long seed = 1; seed <= 8; seed++) {
            Map<Long, TreePart> blocks = TreeGenerator.build(settings, seed, null);
            long over = blocks.entrySet().stream().filter(entry -> entry.getValue().shade() < 0 && TreeVoxelizer.unpackY(entry.getKey()) == split + 2).count();
            if (over >= 2) forked++;
            if (blocks.entrySet().stream().anyMatch(entry -> entry.getValue().blockOf() == TreePart.BRANCH_STAIR && TreeVoxelizer.unpackY(entry.getKey()) == 0)) footed++;
            // the further out, the thinner: no log far up the crown, fences only beyond the walls
            double logs = blocks.entrySet().stream().filter(entry -> entry.getValue() == TreePart.TRUNK || entry.getValue() == TreePart.BRANCH)
                    .mapToInt(entry -> TreeVoxelizer.unpackY(entry.getKey())).average().orElse(0.0);
            double fences = blocks.entrySet().stream().filter(entry -> entry.getValue() == TreePart.BRANCH_FENCE)
                    .mapToInt(entry -> TreeVoxelizer.unpackY(entry.getKey())).average().orElse(0.0);
            assertTrue(logs < fences, "seed " + seed + " logs " + logs + " fences " + fences);
        }
        assertTrue(forked >= 6, Integer.toString(forked));
        assertTrue(footed >= 6, Integer.toString(footed));
        // no foot asked for: no stair at the foot
        settings.set(TreeSettings.Key.THIN_FOOT, 0);
        settings.set(TreeSettings.Key.THIN_STAIRS, 0);
        assertTrue(TreeGenerator.build(settings, 1L, null).values().stream().noneMatch(part -> part.blockOf() == TreePart.BRANCH_STAIR));
    }

    @Test
    void theLeavesOfAThinTreeAreLaidDarkToBrightAndNoneAlone() {
        // (t258) three shades in three passes, the brighter higher; every leaf touches a leaf or the wood
        TreeSettings settings = tree("thin");
        settings.setLeafGradient(java.util.List.of("DARK_OAK_LEAVES", "OAK_LEAVES", "AZALEA_LEAVES"));
        Map<Long, TreePart> blocks = TreeGenerator.build(settings, 4L, null);
        double[] height = new double[3];
        int[] count = new int[3];
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            int shade = entry.getValue().shade();
            if (shade < 0) continue;
            height[shade] += TreeVoxelizer.unpackY(entry.getKey());
            count[shade]++;
            int x = TreeVoxelizer.unpackX(entry.getKey());
            int y = TreeVoxelizer.unpackY(entry.getKey());
            int z = TreeVoxelizer.unpackZ(entry.getKey());
            boolean touches = false;
            for (int[] step : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                touches |= blocks.containsKey(TreeVoxelizer.pack(x + step[0], y + step[1], z + step[2]));
            }
            assertTrue(touches, "a leaf alone at " + x + "," + y + "," + z);
        }
        for (int shade = 0; shade < 3; shade++) assertTrue(count[shade] > 20, java.util.Arrays.toString(count));
        assertTrue(height[0] / count[0] < height[2] / count[2], java.util.Arrays.toString(height) + " " + java.util.Arrays.toString(count));
    }
}
