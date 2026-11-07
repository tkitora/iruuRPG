package net.tkgon.mc.iruuRPG.tools.procedural.building;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t295: the building tool's plans, walls, roofs, parts, patterns and checks. */
class BuildingGeneratorTest {

    @Test
    void aSideOfBaysHasAPostEveryModuleAndAtItsCorners() {
        Footprint plan = Footprint.rect(0, 0, 3 * 4 + 1, 2 * 4 + 1);
        Ring ring = Ring.ground(plan, 4, 8);
        for (int x = 0; x <= 12; x++) {
            Ring.Spot spot = ring.spot(x, 0);
            assertNotNull(spot, "x " + x);
            assertEquals(x % 4 == 0, spot.post(), "x " + x);
        }
        // the bays between: 3 cells, their middle is the window's
        Ring.Spot bay = ring.spot(2, 0);
        assertEquals(3.0, bay.span(), 1.0E-9);
        assertTrue(bay.within(1));
        assertFalse(ring.spot(1, 0).within(1));
        // a jettied storey keeps the posts where they were (moved out with it) and has its own corners
        Ring above = Ring.above(ring, plan.expand(1), 8);
        assertTrue(above.spot(-1, -1).post());
        assertTrue(above.spot(4, -1).post());
        assertTrue(above.spot(8, -1).post());
        assertFalse(above.spot(0, -1).post());
    }

    @Test
    void theUnionOfRectanglesIsTracedRoundItsWallLine() {
        Footprint l = Footprint.union(List.of(new int[]{0, 0, 12, 8}, new int[]{0, 4, 8, 16}));
        assertEquals(6, l.vertices().size());
        for (Footprint.Edge edge : l.edges()) assertEquals(0, edge.length() % 4, edge.toString());
        assertEquals(1, l.depth(0, 0));
        assertEquals(1, l.depth(12, 8));
        assertEquals(1, l.depth(8, 12));
        assertEquals(0, l.depth(12, 12));
        // the inner corner's cell is on the wall line (it touches the outside at its corner)
        assertEquals(1, l.depth(8, 8));
        // grown a block: one more each way
        Footprint grown = l.expand(1);
        assertEquals(1, grown.depth(-1, -1));
        assertEquals(1, grown.depth(13, 9));
    }

    @Test
    void cutCornersOctagonsAndCirclesHaveTheirShapes() {
        Footprint cut = Footprint.chamfer(0, 0, 13, 9, 2);
        assertEquals(0, cut.depth(0, 0));
        assertEquals(1, cut.depth(2, 0));
        assertEquals(1, cut.depth(0, 2));
        Footprint grown = cut.expand(1);
        assertEquals(1, grown.depth(1, -1));
        Footprint octagon = Footprint.octagon(0, 0, 5, 2);
        assertEquals(0, octagon.depth(0, 0));
        assertEquals(1, octagon.depth(2, 0));
        assertEquals(1, octagon.depth(6, 0));
        Footprint circle = Footprint.circle(0, 0, 5);
        assertEquals(1, circle.depth(5, 0));
        assertEquals(1, circle.depth(-2, 5));
        assertEquals(0, circle.depth(3, 5));
        // the table is symmetric and grows by a block a radius
        for (int r = 2; r < Footprint.MAX_RADIUS; r++) {
            Footprint a = Footprint.circle(0, 0, r);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) assertEquals(a.contains(x, z), a.contains(-x, z));
            assertTrue(a.area() < Footprint.circle(0, 0, r + 1).area());
        }
    }

    @Test
    void theSlopesAreLaidAsBuildersLayThem() {
        // 1:1 a stair a block, 2:1 a full block and a stair, 1:2 a slab then a stair
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 0.5, Roofs.step(i + 0.25, 1.0), 1.0E-9);
            assertEquals(i + 1.0, Roofs.step(i + 0.75, 1.0), 1.0E-9);
            assertEquals(2 * i + 1.5, Roofs.step(i + 0.25, 2.0), 1.0E-9);
            assertEquals(2 * i + 2.0, Roofs.step(i + 0.75, 2.0), 1.0E-9);
        }
        assertEquals(0.5, Roofs.step(0.25, 0.5), 1.0E-9);
        assertEquals(0.5, Roofs.step(0.75, 0.5), 1.0E-9);
        assertEquals(0.5, Roofs.step(1.25, 0.5), 1.0E-9);
        assertEquals(1.0, Roofs.step(1.75, 0.5), 1.0E-9);
        assertEquals(1.5, Roofs.step(2.25, 0.5), 1.0E-9);
        // so a gable at 1:1 is stairs facing up the slope, and at 2:1 a stair on a full block
        for (int pitch : new int[]{1, 2}) {
            BuildParams p = new BuildParams();
            p.baysWide = 3;
            p.baysDeep = 2;
            p.pitch = pitch;
            p.ridge = 1;
            p.overhang = 1;
            p.chimney = p.dormer = p.bayWindow = p.balcony = p.turret = p.porch = 0;
            p.plinth = 0;
            BuildingGenerator.Result result = BuildingGenerator.generate(p, 1L, Ground.FLAT);
            SemanticVoxelGrid g = result.grid();
            // the front slope's top blocks, from the eaves (z = -6) in
            List<Cell> tops = new ArrayList<>();
            for (int z = -5; z <= -2; z++) {
                int[] column = g.column(-1, z, 0, 80);
                Cell top = g.get(-1, column[1], z);
                tops.add(top);
                // (the outer row is the eaves' edge: an upside-down stair and a log under its top)
                if (pitch == 2 && z > -5) assertTrue(g.get(-1, column[1] - 1, z).form() == Cell.Form.FULL || g.get(-1, column[1] - 1, z).form() == Cell.Form.LOG, "z " + z);
            }
            for (Cell top : tops) {
                assertEquals(Cell.Form.STAIRS, top.form(), tops.toString());
                // (its high side toward the ridge: south, +z)
                assertEquals(2, top.facing(), tops.toString());
            }
        }
    }

    @Test
    void everyPatternMakesACheckedBuilding() {
        for (int pattern = 0; pattern <= 6; pattern++) {
            int passed = 0;
            for (long seed = 1; seed <= 3; seed++) {
                BuildParams p = new BuildParams();
                p.pattern = pattern;
                BuildingGenerator.Result result = BuildingGenerator.generate(p, seed, Ground.FLAT);
                assertTrue(result.grid().size() > 500, "pattern " + pattern + " seed " + seed);
                assertTrue(result.grid().size() < 40000, "pattern " + pattern + " seed " + seed + ": " + result.grid().size());
                if (result.problems().isEmpty()) passed++;
                // nothing floats, whatever else
                assertFalse(result.problems().stream().anyMatch(problem -> problem.startsWith("浮いた")), pattern + " " + seed + " " + result.problems());
            }
            assertTrue(passed >= 2, "pattern " + pattern + ": " + passed + " of 3 passed");
        }
    }

    @Test
    void theSameSeedMakesTheSameBuilding() {
        for (int pattern : new int[]{0, 1, 5}) {
            BuildParams p = new BuildParams();
            p.pattern = pattern;
            Map<Long, Cell> a = BuildingGenerator.generate(p, 42L, Ground.FLAT).grid().cells();
            Map<Long, Cell> b = BuildingGenerator.generate(p, 42L, Ground.FLAT).grid().cells();
            assertEquals(a, b);
        }
    }

    @Test
    void thePodiumGoesDownToTheGroundAndItsLedgeIsStairsFacingIn() {
        BuildParams p = new BuildParams();
        p.porch = 0;
        p.turret = 0;
        Ground slope = (x, z) -> x < 0 ? -4 : -1;
        SemanticVoxelGrid g = BuildingGenerator.generate(p, 3L, slope).grid();
        // (the podium's ledge west of the walls, over the low ground: stone down to it)
        int[] column = g.column(-5, -1, -10, 10);
        assertNotNull(column);
        assertEquals(-3, column[0]);
        Cell top = g.get(-5, column[1], -1);
        assertEquals(Role.PLINTH, top.role());
        assertEquals(Cell.Form.STAIRS, top.form());
        assertEquals(1, top.facing());
    }

    @Test
    void theStairsOfACornerTakeTheGamesCornerShape() {
        SemanticVoxelGrid g = new SemanticVoxelGrid();
        // a ring of stairs facing in round (0..2, 0..2): the corner (0, 0) faces south, its neighbours east of it face south and south of it face east
        g.set(1, 0, 0, Cell.stairs(Role.PLINTH, 2, false));
        g.set(0, 0, 1, Cell.stairs(Role.PLINTH, 1, false));
        g.set(0, 0, 0, Cell.stairs(Role.PLINTH, 2, false));
        g.finish();
        assertEquals(3, g.get(0, 0, 0).shape());
    }

    @Test
    void aCellTurnsWithTheBuilding() {
        Cell stair = Cell.stairs(Role.ROOF, 0, false, 0);
        assertEquals(1, stair.turned(1).facing());
        assertEquals(3, stair.turned(3).facing());
        assertEquals(2, Cell.log(Role.BEAM, 0).turned(1).axis());
        assertEquals(1, Cell.log(Role.POST, 1).turned(1).axis());
        Cell wall = Cell.wall(Role.RIDGE).withJoins(0b0101);
        assertEquals(0b1010, wall.turned(1).joins());
        assertEquals("facing=east,half=bottom,shape=straight", stair.turned(1).props());
    }

    @Test
    void theChecksFindAOneBlockStepAndAFloatingBlock() {
        BuildParams p = new BuildParams();
        Building b = Archetypes.manual(p, new Random(1));
        BuildingGenerator.Made made = BuildingGenerator.make(b, Ground.FLAT, new Random(1));
        made.grid().set(0, 90, 0, Cell.full(Role.ROOF));
        assertEquals(1, BuildingGenerator.floating(made.grid()));
        // a little piece in the air is swept away before the checks
        BuildingGenerator.sweep(made.grid());
        assertEquals(0, BuildingGenerator.floating(made.grid()));
    }
}
