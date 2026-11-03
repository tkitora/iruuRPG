package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lobby (t190): a plaza, houses round it facing it with none covering another, paths to their doors, beds, trees, the ground filled. */
class LobbyShapeTest {

    private static TreeSettings lobby(int houses, double organic) {
        TreeSettings settings = new TreeSettings();
        settings.setKind(ProcKind.LOBBY);
        settings.set(TreeSettings.Key.LOBBY_HOUSES, houses);
        settings.set(TreeSettings.Key.LOBBY_ORGANIC, organic);
        return settings;
    }

    private static Map<Long, TreePart> build(TreeSettings settings, long seed, TreeGround ground) {
        return ProceduralShapes.build(ProcKind.LOBBY, settings, seed, new Random(seed), ground, null);
    }

    private static long count(Map<Long, TreePart> blocks, TreePart part) {
        return blocks.values().stream().filter(part::equals).count();
    }

    @Test
    void aPlazaWithAFountainOrAPedestalAndBedsAlongItsEdge() {
        TreeSettings settings = lobby(4, 0.0);
        Map<Long, TreePart> blocks = build(settings, 1L, null);
        // the paving with the grid of its tiles; the fountain (a basin round a column, water in it)
        assertEquals(TreePart.LOBBY_PATH, blocks.get(TreeVoxelizer.pack(0, 0, 0)));
        assertEquals(TreePart.LOBBY_PAVE, blocks.get(TreeVoxelizer.pack(1, 0, 1)));
        assertEquals(TreePart.LOBBY_BED, blocks.get(TreeVoxelizer.pack(0, 1, 0)));
        assertEquals(TreePart.LOBBY_WATER, blocks.get(TreeVoxelizer.pack(1, 1, 0)));
        assertTrue(count(blocks, TreePart.LOBBY_SOIL) > 0);
        settings.set(TreeSettings.Key.LOBBY_CENTER, 2);
        Map<Long, TreePart> pedestal = build(settings, 1L, null);
        assertEquals(TreePart.LOBBY_BED, pedestal.get(TreeVoxelizer.pack(0, 4, 0)));
        assertEquals(0, count(pedestal, TreePart.LOBBY_WATER));
        settings.set(TreeSettings.Key.LOBBY_BEDS, 0);
        assertEquals(0, count(build(settings, 1L, null), TreePart.LOBBY_SOIL));
    }

    @Test
    void theHousesStandRoundThePlazaNoneCoveringAnother() {
        for (double organic : new double[]{0.0, 0.5, 1.0}) {
            for (long seed = 1; seed <= 4; seed++) {
                TreeSettings settings = lobby(8, organic);
                int radius = settings.getInt(TreeSettings.Key.LOBBY_RADIUS);
                List<LobbyShape.Lot> lots = LobbyShape.layout(settings, new Random(seed));
                assertFalse(lots.isEmpty());
                List<Set<Long>> columns = new ArrayList<>();
                for (LobbyShape.Lot lot : lots) {
                    lot.settings.set(TreeSettings.Key.HOUSE_ROTATION, lot.rotation);
                    ProceduralShapes.Blocks house = new ProceduralShapes.Blocks();
                    HouseShape.build(house, lot.settings, null, null);
                    Set<Long> own = new HashSet<>();
                    for (long key : house.map.keySet()) {
                        int x = TreeVoxelizer.unpackX(key) + lot.x;
                        int z = TreeVoxelizer.unpackZ(key) + lot.z;
                        own.add(HouseShape.column(x, z));
                        // (off the plaza)
                        assertTrue(Math.hypot(x, z) > radius + 0.5, "organic " + organic + " seed " + seed + " at " + x + "," + z);
                    }
                    for (Set<Long> other : columns) {
                        for (long column : own) assertFalse(other.contains(column), "organic " + organic + " seed " + seed);
                    }
                    columns.add(own);
                }
            }
        }
        // what does not fit round a small plaza is left out
        TreeSettings small = lobby(12, 0.0);
        small.set(TreeSettings.Key.LOBBY_RADIUS, 4);
        small.set(TreeSettings.Key.LOBBY_GAP, 1);
        assertTrue(LobbyShape.layout(small, new Random(3)).size() < 12);
    }

    @Test
    void pathsTreesAndTheGround() {
        TreeSettings settings = lobby(5, 0.5);
        int radius = settings.getInt(TreeSettings.Key.LOBBY_RADIUS);
        Map<Long, TreePart> blocks = build(settings, 7L, null);
        assertTrue(outsidePaths(blocks, radius) > 10);
        assertTrue(count(blocks, TreePart.TRUNK) > 0);
        settings.set(TreeSettings.Key.LOBBY_PATHS, 0);
        settings.set(TreeSettings.Key.LOBBY_TREES, 0);
        Map<Long, TreePart> bare = build(settings, 7L, null);
        assertEquals(0, outsidePaths(bare, radius));
        assertEquals(0, count(bare, TreePart.TRUNK));
        // on ground 4 lower the plaza is filled down to it
        Map<Long, TreePart> filled = build(settings, 7L, (x, z) -> -4);
        assertEquals(TreePart.HOUSE_STONE, filled.get(TreeVoxelizer.pack(1, -1, 1)));
        assertEquals(TreePart.HOUSE_STONE, filled.get(TreeVoxelizer.pack(1, -3, 1)));
        assertFalse(filled.containsKey(TreeVoxelizer.pack(1, -4, 1)));
        assertFalse(bare.containsKey(TreeVoxelizer.pack(1, -1, 1)));
    }

    /** The paths on the ground out of the plaza (to the doors). */
    private static long outsidePaths(Map<Long, TreePart> blocks, int radius) {
        return blocks.entrySet().stream().filter(entry -> entry.getValue() == TreePart.LOBBY_PATH && TreeVoxelizer.unpackY(entry.getKey()) == 0
                && Math.hypot(TreeVoxelizer.unpackX(entry.getKey()), TreeVoxelizer.unpackZ(entry.getKey())) > radius + 1).count();
    }

    @Test
    void theSameSeedGivesTheSameLobbySmallEnoughToPreview() {
        TreeSettings settings = lobby(6, 0.5);
        Map<Long, TreePart> first = build(settings, 11L, null);
        assertEquals(first, build(settings, 11L, null));
        assertNotEquals(first, build(settings, 12L, null));
        assertTrue(first.size() < 40000, Integer.toString(first.size()));
        TreeSettings read = TreeSettings.fromMap(settings.toMap(), "lobby");
        assertEquals(ProcKind.LOBBY, read.kind());
        assertEquals(0.5, read.get(TreeSettings.Key.LOBBY_ORGANIC));
    }
}
