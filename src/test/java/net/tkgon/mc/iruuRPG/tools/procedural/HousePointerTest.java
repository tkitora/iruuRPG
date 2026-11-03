package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t246) The part wand: the block in sight far off, a part put and stretched where it points, the part being edited in glass in the preview. */
class HousePointerTest {

    private static TreeSettings house(int width, int depth, int floors) {
        TreeSettings settings = new TreeSettings();
        settings.setKind(ProcKind.HOUSE);
        settings.set(TreeSettings.Key.HOUSE_WIDTH, width);
        settings.set(TreeSettings.Key.HOUSE_DEPTH, depth);
        settings.set(TreeSettings.Key.HOUSE_FLOORS, floors);
        settings.set(TreeSettings.Key.HOUSE_STOREY, 4);
        settings.set(TreeSettings.Key.HOUSE_JETTY, 0);
        return settings;
    }

    @Test
    void theRayFindsTheFirstBlockFarOffAndThePreviewBeforeTheWorld() {
        // the ground at y 0 everywhere; a block of the preview at (40, 1, 0)
        HousePointer.Solid ground = (x, y, z) -> y <= 0;
        HousePointer.Solid shown = (x, y, z) -> x == 40 && y == 1 && z == 0;
        // looking along +x, a little down, from 2 over the ground: the preview's block first (it stands on the ground in the way)
        HousePointer.Hit hit = HousePointer.ray(0.5, 2.5, 0.5, 1.0, -0.035, 0.0, 96.0, shown, ground);
        assertNotNull(hit);
        assertTrue(hit.preview());
        assertEquals(40, hit.x());
        assertEquals(39, hit.beforeX());
        // without it, the ground far off; the block before it is the air over it
        HousePointer.Hit far = HousePointer.ray(0.5, 2.5, 0.5, 1.0, -0.035, 0.0, 96.0, null, ground);
        assertNotNull(far);
        assertEquals(0, far.y());
        assertEquals(1, far.beforeY());
        assertEquals(43, far.x());
        // out of reach, and nothing at all
        assertNull(HousePointer.ray(0.5, 2.5, 0.5, 1.0, -0.035, 0.0, 30.0, null, ground));
        assertNull(HousePointer.ray(0.5, 2.5, 0.5, 0.0, 1.0, 0.0, 96.0, null, ground));
    }

    @Test
    void aPointIsReadFromEachSideAsTheSideMakesIt() {
        HouseShape.House house = new HouseShape.House(house(11, 9, 2));
        Random random = new Random(5);
        for (int side = 0; side < 4; side++) {
            HouseShape.Side frame = house.side(side, 0);
            for (int n = 0; n < 20; n++) {
                int u = random.nextInt(21) - 10;
                int v = random.nextInt(21) - 10;
                double[] uv = HousePointer.uv(frame, frame.a(u, v), frame.b(u, v));
                assertEquals(u, (int) uv[0]);
                assertEquals(v, (int) uv[1]);
            }
        }
        // the side a point is nearest: out in front, to the right, behind, to the left (the house's a from 0 to 10, b from 0 to 8, the front at b 8)
        assertEquals(0, HousePointer.nearestSide(house, 5, 14, 0));
        assertEquals(1, HousePointer.nearestSide(house, 16, 4, 0));
        assertEquals(2, HousePointer.nearestSide(house, 5, -6, 0));
        assertEquals(3, HousePointer.nearestSide(house, -5, 4, 0));
        // inside, the wall nearest
        assertEquals(1, HousePointer.nearestSide(house, 9, 4, 0));
    }

    @Test
    void aPartGoesToTheSideNearestThePointAsItsKindJoinsTheHouse() {
        HouseShape.House house = new HouseShape.House(house(11, 9, 2));
        // a tower with its middle on the point, 3 out in front of the front wall, 2 right of its middle
        HouseModule tower = new HouseModule(HouseModule.Type.TOWER);
        HousePointer.put(house, tower, 5 + 2, 0, 8 + 3);
        assertEquals(0, tower.side);
        assertEquals(2, tower.along);
        assertEquals(3, tower.offset + (1 + tower.width) / 2);
        assertEquals(0, tower.level);
        // pointed at the second storey: a turret from it
        HousePointer.put(house, tower, 5, 5, 8 + 1);
        assertEquals(1, tower.level);
        // a volume far out to the right still touches the wall (its near wall on the wall line or inside the house)
        HouseModule volume = new HouseModule(HouseModule.Type.VOLUME);
        HousePointer.put(house, volume, 10 + 12, 0, 4);
        assertEquals(1, volume.side);
        assertTrue(volume.offset <= 0 && volume.offset > -volume.depth, volume.toMap().toString());
        // a chimney against the back wall, or inside the house when pointed inside
        HouseModule chimney = new HouseModule(HouseModule.Type.CHIMNEY);
        HousePointer.put(house, chimney, 5, 0, -2);
        assertEquals(2, chimney.side);
        assertEquals(0, chimney.offset);
        HousePointer.put(house, chimney, 5, 3, 1);
        assertTrue(chimney.offset < 0);
        // a balcony at the storey pointed at, on the left
        HouseModule balcony = new HouseModule(HouseModule.Type.BALCONY);
        HousePointer.put(house, balcony, -1, 5, 4);
        assertEquals(3, balcony.side);
        assertEquals(1, balcony.level);
        assertEquals(0, balcony.offset);
    }

    @Test
    void stretchingKeepsTheFarEndAndReachesThePoint() {
        HouseShape.House house = new HouseShape.House(house(11, 9, 2));
        HouseModule volume = new HouseModule(HouseModule.Type.VOLUME);
        volume.side = 0;
        volume.along = 0;
        volume.width = 5;
        volume.depth = 5;
        volume.offset = -1;
        // its low end stays at -2; out to u 6 and 7 out from the wall: 9 wide, its rows from -1 to 7
        HousePointer.stretch(house, volume, 5 + 6, 0, 8 + 7);
        assertEquals(9, volume.width);
        assertEquals(2, volume.along);
        assertEquals(9, volume.depth);
        // the other way: its high end (6) stays
        HousePointer.stretch(house, volume, 5 - 4, 0, 8 + 7);
        assertEquals(11, volume.width);
        assertEquals(1, volume.along);
        // a tower keeps its middle and is twice as wide as the way to the point
        HouseModule tower = new HouseModule(HouseModule.Type.TOWER);
        tower.side = 0;
        tower.along = 0;
        tower.width = 5;
        tower.offset = 0;
        double middle = tower.offset + 3.0;
        HousePointer.stretch(house, tower, 5 + 4, 0, 8 + middle);
        assertEquals(9, tower.width);
        assertEquals(middle, tower.offset + (1 + tower.width) / 2.0);
        // the base about its middle, odd
        TreeSettings settings = house(11, 9, 2);
        HousePointer.stretchBase(settings, 5 + 8, 4 - 3);
        assertEquals(17, settings.getInt(TreeSettings.Key.HOUSE_WIDTH));
        assertEquals(7, settings.getInt(TreeSettings.Key.HOUSE_DEPTH));
    }

    @Test
    void thePartBeingEditedIsShownInGlassAndEachBlockKnowsItsPart() {
        TreeSettings settings = house(11, 9, 2);
        HouseModule tower = new HouseModule(HouseModule.Type.TOWER);
        HouseModule volume = new HouseModule(HouseModule.Type.VOLUME);
        volume.side = 1;
        volume.height = 2;
        settings.modules().add(tower);
        settings.modules().add(volume);
        Map<Long, TreePart> plain = ProceduralShapes.build(ProcKind.HOUSE, settings, 1L, new Random(1), null, null);
        Map<Long, Integer> owners = new HashMap<>();
        Map<Long, TreePart> shown = ProceduralShapes.house(settings, null, null, 0, owners);
        // the same blocks, those of the tower in glass, nothing else changed
        assertEquals(plain.keySet(), shown.keySet());
        long glass = 0;
        for (Map.Entry<Long, TreePart> entry : shown.entrySet()) {
            if (entry.getValue() == TreePart.HOUSE_SELECTED) glass++;
            else assertEquals(plain.get(entry.getKey()), entry.getValue());
        }
        assertTrue(glass > 20, Long.toString(glass));
        // both parts own blocks; with nothing chosen and no owners asked for, the house is as it always was
        assertEquals(Set.of(0, 1), new java.util.HashSet<>(owners.values()));
        assertEquals(plain, ProceduralShapes.house(settings, null, null, -1, null));
        // a part turned off owns nothing and is not shown
        tower.on = false;
        Map<Long, Integer> without = new HashMap<>();
        Map<Long, TreePart> off = ProceduralShapes.house(settings, null, null, 0, without);
        assertTrue(off.values().stream().noneMatch(TreePart.HOUSE_SELECTED::equals));
        assertEquals(Set.of(1), new java.util.HashSet<>(without.values()));
    }
}
