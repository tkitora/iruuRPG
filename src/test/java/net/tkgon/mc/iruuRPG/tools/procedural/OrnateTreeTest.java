package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.ornate.OrnateBuilder;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t282: the ornate tree in the tool: its blocks by part with their looks, the palette of light and dark blocks, the settings and the menu. */
class OrnateTreeTest {

    private static TreeSettings ornate(int style) {
        TreeSettings settings = new TreeSettings();
        settings.setAlgorithm("ornate");
        settings.set(TreeSettings.Key.ORN_STYLE, style);
        return settings;
    }

    @Test
    void theOrnateTreeIsAnAlgorithmOfTheTool() {
        assertTrue(TreeSettings.ALGORITHMS.contains("ornate"));
        TreeSettings settings = ornate(0);
        assertEquals("ornate", settings.algorithm());
        Map<Long, TreePart> blocks = TreeGenerator.build(settings, 3L, null);
        assertTrue(blocks instanceof OrnateTree.Blocks);
        // the menu's pages fit (14 numbers a page, 7 on the leaf page under the gradient)
        assertTrue(ProceduralMenu.treeKeys("ornate", ProceduralMenu.Page.TRUNK).length <= 14);
        assertTrue(ProceduralMenu.treeKeys("ornate", ProceduralMenu.Page.BRANCH).length <= 14);
        assertTrue(ProceduralMenu.treeKeys("ornate", ProceduralMenu.Page.LEAF).length <= 7);
        for (int style = 0; style <= 3; style++) assertTrue(!OrnateTree.styleName(style).isEmpty());
    }

    @Test
    void everyBlockOfWoodHasItsLookAndItsPartMatchesItsShape() {
        for (int style = 0; style <= 3; style++) {
            OrnateTree.Blocks blocks = OrnateTree.build(ornate(style), new Random(2));
            for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
                TreePart part = entry.getValue();
                OrnateTree.Look look = blocks.looks.get(entry.getKey());
                if (part.shade() >= 0) {
                    assertTrue(look == null, "a leaf has no look");
                    continue;
                }
                assertNotNull(look, part.toString());
                WoodPalette.Form expected = switch (part) {
                    case BRANCH_STAIR -> WoodPalette.Form.STAIRS;
                    case BRANCH_SLAB -> WoodPalette.Form.SLAB;
                    case BRANCH_FENCE -> WoodPalette.Form.FENCE;
                    case BRANCH_WALL -> WoodPalette.Form.WALL;
                    case BRANCH_TRAPDOOR -> WoodPalette.Form.TRAPDOOR;
                    case BRANCH_GATE -> WoodPalette.Form.GATE;
                    default -> WoodPalette.Form.LOG;
                };
                assertSame(expected, look.form(), part.toString());
                if (look.form() == WoodPalette.Form.LOG) assertTrue(look.props().startsWith("axis="), look.props());
            }
        }
    }

    @Test
    void thePaletteGoesDarkerAndLighterNearTheColour() {
        assertSame(Material.OAK_LOG, WoodPalette.pick(WoodPalette.Form.LOG, Material.OAK_LOG, 0, 7.0, null));
        double oak = WoodPalette.entry(Material.OAK_LOG).l();
        Material dark = WoodPalette.pick(WoodPalette.Form.LOG, Material.OAK_LOG, -2, 7.0, null);
        Material light = WoodPalette.pick(WoodPalette.Form.LOG, Material.OAK_LOG, 2, 7.0, null);
        assertTrue(WoodPalette.entry(dark).l() < oak, dark.name());
        // (oak is the lightest brown bark: lighter than it, the palette has nothing nearer than oak itself)
        assertTrue(WoodPalette.entry(light).l() >= oak, light.name());
        double spruce = WoodPalette.entry(Material.SPRUCE_LOG).l();
        assertTrue(WoodPalette.entry(WoodPalette.pick(WoodPalette.Form.LOG, Material.SPRUCE_LOG, 2, 7.0, null)).l() > spruce);
        // a log stays a bark log (not a stripped one, not a wood)
        assertTrue(dark.name().endsWith("_LOG") && !dark.name().startsWith("STRIPPED_"), dark.name());
        // no shading: the block itself
        assertSame(Material.OAK_LOG, WoodPalette.pick(WoodPalette.Form.LOG, Material.OAK_LOG, 2, 0.0, null));
        // there are no walls of wood: the wall near oak bark is mud bricks
        assertSame(Material.MUD_BRICK_WALL, WoodPalette.pick(WoodPalette.Form.WALL, Material.COBBLESTONE_WALL, 0, 7.0, WoodPalette.colour(Material.OAK_LOG)));
        // matched to dark oak bark, the stairs are dark oak's
        assertSame(Material.DARK_OAK_STAIRS, WoodPalette.pick(WoodPalette.Form.STAIRS, Material.OAK_STAIRS, 0, 7.0, WoodPalette.colour(Material.DARK_OAK_LOG)));
        // a block that is not in the palette stays as it is
        assertSame(Material.GLASS, WoodPalette.pick(WoodPalette.Form.STAIRS, Material.GLASS, 2, 7.0, null));
        // the palette of a shape runs from dark to light
        List<WoodPalette.Entry> stairs = WoodPalette.of(WoodPalette.Form.STAIRS);
        for (int index = 1; index < stairs.size(); index++) assertTrue(stairs.get(index - 1).l() <= stairs.get(index).l());
        // a wall has no wood, a fence of wood is wood
        assertTrue(WoodPalette.of(WoodPalette.Form.WALL).stream().noneMatch(WoodPalette.Entry::wood));
        assertTrue(WoodPalette.entry(Material.OAK_FENCE).wood());
    }

    @Test
    void theOrnateKeysAreTheTools() {
        assertEquals(TreeVoxelizer.pack(-5, 17, 300), OrnateBuilder.pack(-5, 17, 300));
        assertEquals("orn-style", TreeSettings.Key.ORN_STYLE.id());
        assertTrue(TreeSettings.Key.ORN_MATCH.toggle());
        TreeSettings settings = ornate(1);
        settings.set(TreeSettings.Key.ORN_TRUNK, 3.25);
        TreeSettings back = TreeSettings.fromMap(settings.toMap(), "x");
        assertEquals("ornate", back.algorithm());
        assertEquals(1, back.getInt(TreeSettings.Key.ORN_STYLE));
        assertEquals(3.25, back.get(TreeSettings.Key.ORN_TRUNK), 1.0e-9);
    }
}
