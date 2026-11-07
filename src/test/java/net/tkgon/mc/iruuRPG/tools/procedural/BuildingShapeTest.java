package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.building.Cell;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t295: the building in the procedural tool: the blocks of a role's family, the turn toward the player, the menu's pages, the .schem. */
class BuildingShapeTest {

    @Test
    void aRolesShapesAreItsBlocksFamily() {
        assertEquals(Material.DEEPSLATE_TILE_STAIRS, BuildingShape.form(Material.DEEPSLATE_TILES, Cell.Form.STAIRS, null));
        assertEquals(Material.DEEPSLATE_TILE_SLAB, BuildingShape.form(Material.DEEPSLATE_TILES, Cell.Form.SLAB, null));
        assertEquals(Material.STONE_BRICK_STAIRS, BuildingShape.form(Material.STONE_BRICKS, Cell.Form.STAIRS, null));
        assertEquals(Material.STONE_BRICK_WALL, BuildingShape.form(Material.STONE_BRICKS, Cell.Form.WALL, null));
        assertEquals(Material.BRICK_STAIRS, BuildingShape.form(Material.BRICKS, Cell.Form.STAIRS, null));
        assertEquals(Material.SPRUCE_STAIRS, BuildingShape.form(Material.SPRUCE_PLANKS, Cell.Form.STAIRS, null));
        assertEquals(Material.SPRUCE_LOG, BuildingShape.form(Material.SPRUCE_PLANKS, Cell.Form.LOG, null));
        assertEquals(Material.DARK_OAK_FENCE, BuildingShape.form(Material.DARK_OAK_PLANKS, Cell.Form.FENCE, null));
        assertEquals(Material.DARK_OAK_TRAPDOOR, BuildingShape.form(Material.DARK_OAK_PLANKS, Cell.Form.TRAPDOOR, null));
        assertEquals(Material.OAK_STAIRS, BuildingShape.form(Material.STRIPPED_OAK_LOG, Cell.Form.STAIRS, null));
        assertEquals(Material.QUARTZ_STAIRS, BuildingShape.form(Material.QUARTZ_BLOCK, Cell.Form.STAIRS, null));
        assertEquals(Material.QUARTZ_BLOCK, BuildingShape.form(Material.QUARTZ_STAIRS, Cell.Form.FULL, null));
        assertEquals(Material.SPRUCE_LOG, BuildingShape.form(Material.SPRUCE_LOG, Cell.Form.LOG, null));
        // (a block with no stairs: the default's stairs keep the shape; no default either: the block itself)
        assertEquals(Material.DEEPSLATE_TILE_STAIRS, BuildingShape.form(Material.WHITE_CONCRETE, Cell.Form.STAIRS, Material.DEEPSLATE_TILES));
        assertEquals(Material.WHITE_CONCRETE, BuildingShape.form(Material.WHITE_CONCRETE, Cell.Form.STAIRS, null));
        assertEquals(Material.WHITE_WOOL, BuildingShape.form(Material.WHITE_WOOL, Cell.Form.LOG, null));
        assertEquals(Material.LIGHTNING_ROD, BuildingShape.form(Material.STONE, Cell.Form.ROD, null));
        // (t302: a wool's stairs are those of the colour nearest it: red wool, a reddish stair; white, a light one)
        Material red = BuildingShape.form(Material.RED_WOOL, Cell.Form.STAIRS, null);
        assertTrue(red.name().endsWith("_STAIRS"), red.name());
        assertTrue(red.name().contains("BRICK") || red.name().contains("GRANITE") || red.name().contains("MANGROVE") || red.name().contains("ACACIA") || red.name().contains("RED"), red.name());
        assertTrue(BuildingShape.form(Material.WHITE_WOOL, Cell.Form.SLAB, null).name().endsWith("_SLAB"));
        assertEquals(Material.WHITE_WOOL, BuildingShape.form(Material.WHITE_WOOL, Cell.Form.FULL, null));
    }

    @Test
    void theFrontFacesThePlayer() {
        // (the player to the south: no turn; east: the front +z turned to +x)
        assertEquals(0, BuildingShape.turns(new org.bukkit.util.Vector(0, 0, 1), 0));
        int east = BuildingShape.turns(new org.bukkit.util.Vector(1, 0, 0), 0);
        int[] front = BuildingShape.turn(0, 5, east);
        assertEquals(5, front[0]);
        assertEquals(0, front[1]);
        assertEquals(1, BuildingShape.turns(new org.bukkit.util.Vector(0, 0, 1), 1));
    }

    @Test
    void theBuildingHasItsOwnPagesAndItsBlocksCarryTheirStates() {
        TreeSettings settings = new TreeSettings();
        settings.setKind(ProcKind.BUILDING);
        // (t302: the building of the house base's numbers has the house base's pages and its own)
        assertTrue(ProceduralMenu.Page.B_FORM.shownFor(settings));
        assertTrue(ProceduralMenu.Page.B_AUTO.shownFor(settings));
        assertTrue(ProceduralMenu.Page.ROOF.shownFor(settings));
        assertTrue(ProceduralMenu.Page.MODULES.shownFor(settings));
        assertFalse(ProceduralMenu.Page.TRUNK.shownFor(settings));
        // every page fits its numbers (the patterns' on the wide grid)
        for (ProceduralMenu.Page page : ProceduralMenu.Page.values()) assertTrue(page.keysFor(settings).length <= (page == ProceduralMenu.Page.B_AUTO ? 21 : 14), page.name());
        // the settings survive the yaml
        settings.set(TreeSettings.Key.BLD_PATTERN, 3);
        assertFalse(ProceduralMenu.Page.MODULES.shownFor(settings));
        BuildingShape.applyPattern(settings, 3);
        TreeSettings back = TreeSettings.fromMap(settings.toMap(), "b");
        assertEquals(ProcKind.BUILDING, back.kind());
        assertEquals(3, back.getInt(TreeSettings.Key.BLD_PATTERN));
        assertEquals(1.0, back.get(TreeSettings.Key.BLD_TURRET), 1.0E-9);
        // a building is its parts, each block with its shape
        BuildingShape.Blocks blocks = BuildingShape.build(settings, 7L, null, null);
        assertTrue(blocks.size() > 1000);
        assertEquals(blocks.size(), blocks.cells.size());
        assertTrue(blocks.containsValue(TreePart.BLD_ROOF));
        assertTrue(blocks.cells.values().stream().anyMatch(cell -> cell.form() == Cell.Form.STAIRS));
        assertFalse(BuildingShape.report(blocks).isEmpty());
    }

    @Test
    void aSchematicHasItsBoxPaletteAndBlocks() throws IOException {
        Map<Long, String> blocks = new HashMap<>();
        blocks.put(TreeVoxelizer.pack(0, 0, 0), "minecraft:stone");
        blocks.put(TreeVoxelizer.pack(2, 1, -1), "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        byte[] bytes = SchematicWriter.write(blocks);
        DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)));
        assertEquals(10, in.readByte());
        byte[] name = new byte[in.readShort()];
        in.readFully(name);
        assertEquals("Schematic", new String(name, java.nio.charset.StandardCharsets.UTF_8));
        String text = new String(new GZIPInputStream(new ByteArrayInputStream(bytes)).readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("minecraft:air"));
        assertTrue(text.contains("minecraft:oak_stairs[facing=north"));
        assertTrue(text.contains("WEOffsetX"));
        // the box: 3 wide, 2 high, 2 long: 12 blocks, one byte each
        assertTrue(text.contains("BlockData"));
    }
}
