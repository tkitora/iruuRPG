package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The silhouette trees (the generator the user sent, as an algorithm): every shape grows, its blocks become the tool's parts, the samples read. */
class SilhouetteTreeTest {

    private static TreeSettings tree(int shape) {
        TreeSettings settings = new TreeSettings();
        settings.setAlgorithm("silhouette");
        settings.set(TreeSettings.Key.SIL_SHAPE, shape);
        return settings;
    }

    private static long leaves(Map<Long, TreePart> blocks) {
        return blocks.values().stream().filter(part -> part.shade() >= 0).count();
    }

    @Test
    void everyShapeGrowsWoodAndLeavesTheSameFromTheSameSeed() {
        for (int shape = 0; shape < SilhouetteTree.SHAPES.size(); shape++) {
            Map<Long, TreePart> blocks = TreeGenerator.build(tree(shape), 5L, null);
            String what = SilhouetteTree.SHAPES.get(shape) + " " + blocks.size();
            assertEquals(blocks, TreeGenerator.build(tree(shape), 5L, null), what);
            assertTrue(blocks.values().stream().anyMatch(part -> part.blockOf() == TreePart.TRUNK), what);
            // (as the generator makes them: a conifer of its own height is a slim one, with few leaves)
            assertTrue(leaves(blocks) > 20, what);
            assertTrue(blocks.size() < 20000, what);
            // nothing under the ground it stands on
            assertTrue(blocks.keySet().stream().allMatch(key -> TreeVoxelizer.unpackY(key) >= 0), what);
        }
        // a higher tree has more blocks; more leaf asked for, more leaves
        TreeSettings tall = tree(0);
        tall.set(TreeSettings.Key.SIL_HEIGHT, 20);
        assertTrue(TreeGenerator.build(tall, 5L, null).size() > TreeGenerator.build(tree(0), 5L, null).size());
        TreeSettings full = tree(0);
        full.set(TreeSettings.Key.SIL_LEAF, 1.0);
        TreeSettings lean = tree(0);
        lean.set(TreeSettings.Key.SIL_LEAF, -1.0);
        assertTrue(leaves(TreeGenerator.build(full, 5L, null)) > leaves(TreeGenerator.build(lean, 5L, null)));
    }

    @Test
    void theBlocksTheGeneratorNamesBecomeParts() {
        assertEquals(TreePart.TRUNK, SilhouetteTree.part("log[axis=y]", 1));
        assertEquals(TreePart.TRUNK_X, SilhouetteTree.part("log[axis=x]", 1));
        assertEquals(TreePart.TRUNK_Z, SilhouetteTree.part("log[axis=z]", 1));
        assertEquals(TreePart.BRANCH, SilhouetteTree.part("wood[axis=y]", 1));
        assertEquals(TreePart.BRANCH_SLAB_TOP, SilhouetteTree.part("slab[type=top]", 1));
        assertEquals(TreePart.BRANCH_SLAB, SilhouetteTree.part("slab[type=bottom]", 1));
        assertEquals(TreePart.BRANCH_STAIR_TOP_EAST, SilhouetteTree.part("stairs[facing=east,half=top,shape=straight]", 1));
        assertEquals(TreePart.BRANCH_STAIR, SilhouetteTree.part("stairs[facing=north,half=bottom,shape=straight]", 1));
        assertEquals(TreePart.BRANCH_WALL, SilhouetteTree.part("wall[north=low,south=none,east=none,west=none,up=true,waterlogged=false]", 1));
        assertEquals(TreePart.BRANCH_FENCE, SilhouetteTree.part("fence[north=true,south=false,east=false,west=false,waterlogged=false]", 1));
        // the brightest tier at the top of the gradient, the darkest at the bottom
        assertEquals(TreePart.leaf(2), SilhouetteTree.part("leaf:0", 3));
        assertEquals(TreePart.leaf(0), SilhouetteTree.part("leaf:3", 3));
        assertEquals(TreePart.LEAF, SilhouetteTree.part("leaf:1", 1));
        assertNull(SilhouetteTree.part("something", 1));
        // the stairs and the lying logs are put in the block of their first part
        assertEquals(TreePart.BRANCH_STAIR, TreePart.BRANCH_STAIR_TOP_WEST.blockOf());
        assertEquals(TreePart.TRUNK, TreePart.TRUNK_Z.blockOf());
    }

    @Test
    void theSamplesReadAndGrow() {
        for (String name : SilhouetteTree.SHAPES) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/trees/silhouette_" + name + ".yml"));
            Map<String, Object> plain = new LinkedHashMap<>();
            for (String key : yaml.getConfigurationSection("settings").getKeys(false)) {
                Object value = yaml.get("settings." + key);
                plain.put(key, value instanceof ConfigurationSection section ? section.getValues(true) : value);
            }
            TreeSettings settings = TreeSettings.fromMap(plain, name);
            assertEquals("silhouette", settings.algorithm(), name);
            assertEquals(SilhouetteTree.SHAPES.indexOf(name), settings.getInt(TreeSettings.Key.SIL_SHAPE), name);
            assertTrue(settings.leafShades() >= 3, name);
            assertTrue(leaves(TreeGenerator.build(settings, 3L, null)) > 40, name);
        }
    }
}
