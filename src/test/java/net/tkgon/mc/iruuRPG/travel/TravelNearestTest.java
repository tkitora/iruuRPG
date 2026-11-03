package net.tkgon.mc.iruuRPG.travel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TravelNearestTest {

    private static TravelService.Waypoint point(String id, String world, double x, double z) {
        return new TravelService.Waypoint(id, id, world, x, 64.0, z, 0.0f);
    }

    private final List<TravelService.Waypoint> points = List.of(point("a", "world", 0, 0), point("b", "world", 100, 0), point("c", "world", 1000, 0), point("d", "nether", 5, 5));

    @Test
    void picksTheNearestFoundPoint() {
        assertEquals("b", TravelService.nearest(points, Set.of("a", "b", "c"), "world", 70, 64, 0).id());
    }

    @Test
    void skipsPointsNotFound() {
        assertEquals("c", TravelService.nearest(points, Set.of("c"), "world", 0, 64, 0).id());
    }

    @Test
    void staysInTheWorldOfTheDeath() {
        assertEquals("d", TravelService.nearest(points, Set.of("a", "d"), "nether", 0, 64, 0).id());
        assertNull(TravelService.nearest(points, Set.of("a", "b"), "end", 0, 64, 0));
    }

    @Test
    void noneFoundMeansNull() {
        assertNull(TravelService.nearest(points, Set.of(), "world", 0, 64, 0));
    }
}
