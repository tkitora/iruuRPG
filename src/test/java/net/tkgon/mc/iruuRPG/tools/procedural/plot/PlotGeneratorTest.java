package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t313) The building of lots: regular plans, shared sides, heights by lot, walls, panels, roofs, parts, checks, the kept lots and the patterns. */
class PlotGeneratorTest {

    private static Plan two(int floorsA, int floorsB) {
        Plan plan = new Plan();
        plan.siteWidth = 33;
        plan.siteDepth = 21;
        plan.module = 4;
        Plan.Lot a = new Plan.Lot("main", Plan.Shape.RECT, 4, 4, 13, 9, floorsA);
        a.floors.get(0).door = 2;
        Plan.Lot b = new Plan.Lot("wing", Plan.Shape.RECT, 16, 4, 9, 9, floorsB);
        plan.lots.add(a);
        plan.lots.add(b);
        return plan;
    }

    @Test
    void lotsAreMadeRegularOnTheModulesGridInsideTheSite() {
        Plan plan = new Plan();
        plan.siteWidth = 25;
        plan.siteDepth = 17;
        plan.module = 4;
        plan.lots.add(new Plan.Lot("main", Plan.Shape.RECT, 3, 5, 12, 10, 2));
        plan.lots.add(new Plan.Lot("big", Plan.Shape.RECT, 20, 0, 40, 9, 1));
        Layout layout = Layout.of(plan);
        Plan.Lot main = layout.plan.lots.get(0);
        assertEquals(4, main.x);
        assertEquals(4, main.z);
        assertEquals(13, main.w);
        assertEquals(9, main.d);
        Plan.Lot big = layout.plan.lots.get(1);
        assertEquals(1, (big.w - 1) % 4 == 0 ? 1 : 0, "n·m+1: " + big.w);
        assertTrue(big.x + big.w <= plan.siteWidth, big.x + "+" + big.w);
        assertFalse(layout.notes.isEmpty());
        // (the plan given is not changed)
        assertEquals(3, plan.lots.get(0).x);
    }

    @Test
    void sharedSidesMakeTheGraphOfTheLots() {
        Layout layout = Layout.of(two(2, 1));
        assertEquals(java.util.Set.of(1), layout.neighbours(0));
        Layout.Shared east = null;
        for (Layout.Shared shared : layout.shared) if (shared.lot() == 0 && shared.other() == 1 && shared.side() == 1) east = shared;
        assertNotNull(east);
        assertEquals(1, east.side());
        assertEquals(0, east.from());
        assertEquals(8, east.to());
    }

    @Test
    void eachLotsHeightsAreItsOwn() {
        Layout before = Layout.of(two(2, 1));
        Plan plan = two(2, 1);
        plan.lots.get(1).floors.add(new Plan.Floor());
        Layout after = Layout.of(plan);
        assertEquals(before.lots.get(0).eave, after.lots.get(0).eave);
        assertEquals(before.lots.get(1).eave + 5, after.lots.get(1).eave);
        assertEquals(before.lots.get(0).bottom[1], after.lots.get(0).bottom[1]);
    }

    @Test
    void aSharedWallIsInsideWhereBothHaveStoreysAndOutsideAboveTheLowerOne() {
        PlotGenerator.Result result = PlotGenerator.build(two(2, 1), Kit.builtin(), 1L, Ground.FLAT);
        Grid grid = result.grid();
        Layout.LotGeo main = result.layout().lots.get(0);
        Layout.LotGeo wing = result.layout().lots.get(1);
        // (the shared line x = 16: no glass and no door at the ground storey, the frame of the upper storey of the main block)
        int x = 16;
        boolean upperWall = false;
        for (int z = 5; z <= 11; z++) {
            for (int y = wing.y0 + 1; y < wing.eave - 1; y++) {
                Cell cell = grid.get(x, y, z);
                assertTrue(cell == null || cell.role().roof() || cell.role() == Role.FLOOR, "inside at " + x + "," + y + "," + z + ": " + cell);
            }
            for (int y = main.bottom[1]; y < main.eave; y++) if (grid.get(x, y, z) != null) upperWall = true;
        }
        assertTrue(upperWall);
    }

    @Test
    void panelsFillTheWallExactlyAndTheSameBothWays() {
        int[] widths = {1, 2, 3, 4, 5, 6, 7};
        for (int n = 1; n < 40; n++) {
            List<Walls.Slot> slots = Walls.slots(1, n, 3, widths);
            int total = -1;
            for (Walls.Slot slot : slots) total += slot.width() + 1;
            assertEquals(n, total, "n " + n);
            for (int i = 0; i < slots.size(); i++) assertEquals(slots.get(i).width(), slots.get(slots.size() - 1 - i).width(), "n " + n);
        }
        // (12 = 3 panels of 3 and their 2 posts... a post between each two)
        assertEquals(List.of(3, 3, 3), Walls.slots(1, 11, 3, widths).stream().map(Walls.Slot::width).toList());
        // (widths that cannot fill it: plain panels stretch at the ends)
        List<Walls.Slot> stretched = Walls.slots(1, 13, 3, new int[]{5});
        assertTrue(stretched.stream().anyMatch(Walls.Slot::stretched));
        int total = -1;
        for (Walls.Slot slot : stretched) total += slot.width() + 1;
        assertEquals(13, total);
    }

    @Test
    void roofsJoinWithValleysOrStopAgainstAHigherWall() {
        // the same eaves, the same group: one group, the wing's ridge runs into the main block's roof
        Plan cross = two(2, 2);
        cross.lots.get(1).x = 8;
        cross.lots.get(1).z = 12;
        PlotGenerator.Result joined = PlotGenerator.build(cross, Kit.builtin(), 1L, Ground.FLAT);
        assertTrue(joined.problems().isEmpty(), joined.problems().toString());
        // a lower lot: a shed against the main block's wall, under its eaves, with flashing along the wall
        Plan lean = two(2, 1);
        lean.lots.get(1).roof.type = Plan.RoofType.SHED;
        PlotGenerator.Result leaning = PlotGenerator.build(lean, Kit.builtin(), 1L, Ground.FLAT);
        assertTrue(leaning.problems().isEmpty(), leaning.problems().toString());
        int mainEave = leaning.layout().lots.get(0).eave;
        boolean flashing = false;
        for (Map.Entry<Long, Cell> entry : leaning.grid().cells().entrySet()) {
            if (leaning.grid().ownerOf(entry.getKey()) != 1 || !entry.getValue().role().roof()) continue;
            assertTrue(Grid.y(entry.getKey()) < mainEave, "the lean-to's roof under the main eaves");
            if (entry.getValue().role() == Role.FLASHING) flashing = true;
        }
        assertTrue(flashing);
    }

    @Test
    void theSamplePlansAreBuiltWithoutProblems() {
        for (int i = 0; i < SamplePlans.count(); i++) {
            PlotGenerator.Result result = PlotGenerator.build(SamplePlans.plan(i), Kit.builtin(), 1L, Ground.FLAT);
            assertTrue(result.problems().isEmpty(), SamplePlans.name(i) + ": " + result.problems());
            assertEquals(0, Checks.floating(result.grid(), Ground.FLAT), SamplePlans.name(i));
            assertTrue(result.grid().size() > 500, SamplePlans.name(i));
        }
    }

    @Test
    void aChangedLotMakesAgainOnlyItselfAndTheLotsBesideIt() {
        Plan plan = two(2, 1);
        plan.lots.add(new Plan.Lot("far", Plan.Shape.RECT, 4, 12, 9, 5, 1));
        plan.siteDepth = 21;
        PlotGenerator.Cache cache = new PlotGenerator.Cache();
        Kit kit = Kit.builtin();
        assertEquals(3, PlotGenerator.build(plan, kit, 1L, Ground.FLAT, cache).rebuilt());
        assertEquals(0, PlotGenerator.build(plan, kit, 1L, Ground.FLAT, cache).rebuilt());
        // (the wing: its neighbour is the main block, not "far")
        plan.lots.get(1).floors.add(new Plan.Floor());
        assertEquals(2, PlotGenerator.build(plan, kit, 1L, Ground.FLAT, cache).rebuilt());
    }

    @Test
    void everyPatternMakesBuildingsThatPassTheirChecks() {
        Kit kit = Kit.builtin();
        for (Patterns.Pattern pattern : Patterns.Pattern.values()) {
            for (long seed = 1; seed <= 4; seed++) {
                Patterns.Design design = Patterns.design(pattern, new Patterns.Rules(), kit, seed, 3, 3, Ground.FLAT);
                assertNotNull(design, pattern + " " + seed);
                assertTrue(design.result().grid().size() > 300, pattern + " " + seed);
                assertTrue(design.result().grid().size() < 40000, pattern + " " + seed);
                assertEquals(0, Checks.floating(design.result().grid(), Ground.FLAT), pattern + " " + seed);
            }
        }
        // (each time a new seed: different buildings)
        Patterns.Design a = Patterns.design(null, new Patterns.Rules(), kit, 11L, 2, 2, Ground.FLAT);
        Patterns.Design b = Patterns.design(null, new Patterns.Rules(), kit, 12L, 2, 2, Ground.FLAT);
        assertFalse(a.plan().lotsToList().equals(b.plan().lotsToList()));
    }

    @Test
    void lotsAndPartsGoToTheYamlAndBack() {
        Plan plan = SamplePlans.plan(3);
        List<Map<String, Object>> list = plan.lotsToList();
        List<Plan.Lot> back = Plan.lotsFromList(list);
        assertEquals(plan.lots.size(), back.size());
        for (int i = 0; i < back.size(); i++) assertEquals(plan.lots.get(i).toMap(), back.get(i).toMap());
        for (Kit.Part part : Kit.builtin().all()) {
            Kit.Part again = Kit.Part.fromMap(part.toMap());
            assertNotNull(again, part.id);
            assertEquals(part.toMap(), again.toMap(), part.id);
        }
        assertNull(Kit.Part.fromMap(Map.of("category", "wall")));
    }

    @Test
    void aNineSliceRepeatsItsMiddle() {
        assertEquals(List.of(0, 1, 2, 1, 2, 3), java.util.Arrays.stream(Painter.map(4, 6, new int[]{1, 2})).boxed().toList());
        assertEquals(List.of(0, 1, 2), java.util.Arrays.stream(Painter.map(3, 3, new int[]{-1, -1})).boxed().toList());
    }
}
