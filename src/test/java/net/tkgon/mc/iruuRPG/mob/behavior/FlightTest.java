package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlightTest {

    /** A world of air with the solid blocks of {@code solid} ("x,y,z"). */
    private static World world(Set<String> solid) {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            String key = call.getArgument(0) + "," + call.getArgument(1) + "," + call.getArgument(2);
            Block block = mock(Block.class);
            boolean wall = solid.contains(key);
            when(block.isPassable()).thenReturn(!wall);
            when(block.isLiquid()).thenReturn(false);
            return block;
        });
        return world;
    }

    private static Mob mob(World world, Location at) {
        Mob mob = mock(Mob.class);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getWidth()).thenReturn(0.6);
        when(mob.getHeight()).thenReturn(1.8);
        when(mob.isValid()).thenReturn(true);
        when(mob.getLocation()).thenAnswer(call -> at.clone());
        return mob;
    }

    @Test
    void volumeContainsAndClamps() {
        World world = world(Set.of());
        Location home = new Location(world, 0.5, 64, 0.5);
        Volume cylinder = new Volume("cylinder", 10, 6, 2);
        assertTrue(cylinder.contains(home, new Location(world, 5, 68, 5)));
        assertFalse(cylinder.contains(home, new Location(world, 5, 71, 5)));
        assertFalse(cylinder.contains(home, new Location(world, 12, 64, 0)));
        Location clamped = cylinder.clamp(home, new Location(world, 30.5, 90, 0.5));
        assertTrue(cylinder.contains(home, clamped));
        assertEquals(70.0, clamped.getY(), 1.0E-9);
        Volume sphere = new Volume("sphere", 5, 0, 0);
        assertTrue(sphere.contains(home, sphere.clamp(home, new Location(world, 20, 80, -20))));
        Volume box = new Volume("box", 4, 3, 3);
        assertTrue(box.contains(home, new Location(world, 4, 66, -3.4)));
        assertFalse(box.contains(home, new Location(world, 5, 64, 0)));
    }

    @Test
    void fliesStraightWhenNothingIsInTheWay() {
        World world = world(Set.of());
        Location start = new Location(world, 0.5, 70, 0.5);
        FlyNavigator flight = new FlyNavigator(mob(world, start), start, new Volume("cylinder", 30, 20, 10), 6.0);
        assertTrue(flight.moveTo(new Location(world, 10.5, 75, 0.5), 1.0));
        assertEquals(2, flight.remaining().size());
    }

    @Test
    void goesOverAWall() {
        // a wall across x = 5, from y 60 to 74, z -8 to 8: the way from x 0 to x 10 goes over it
        Set<String> solid = new HashSet<>();
        for (int y = 60; y <= 74; y++) for (int z = -8; z <= 8; z++) solid.add("5," + y + "," + z);
        World world = world(solid);
        Location start = new Location(world, 0.5, 64, 0.5);
        FlyNavigator flight = new FlyNavigator(mob(world, start), start, new Volume("cylinder", 30, 20, 10), 6.0);
        Location goal = new Location(world, 10.5, 64, 0.5);
        assertTrue(flight.canReach(goal));
        assertTrue(flight.moveTo(goal, 1.0));
        List<org.bukkit.util.Vector> path = new ArrayList<>(flight.remaining());
        assertTrue(path.size() >= 3, "a path with turns: " + path);
        assertEquals(10.5, path.get(path.size() - 1).getX(), 1.0E-9);
        // it never goes through the wall
        for (int index = 1; index < path.size(); index++) {
            org.bukkit.util.Vector a = path.get(index - 1);
            org.bukkit.util.Vector b = path.get(index);
            for (double t = 0; t <= 1.0; t += 0.02) {
                double x = a.getX() + (b.getX() - a.getX()) * t;
                double y = a.getY() + (b.getY() - a.getY()) * t;
                double z = a.getZ() + (b.getZ() - a.getZ()) * t;
                for (int by = (int) Math.floor(y); by <= (int) Math.floor(y + 1.79); by++) {
                    assertFalse(solid.contains((int) Math.floor(x) + "," + by + "," + (int) Math.floor(z)), "inside the wall at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void cannotLeaveItsRoomOrReachAShutBox() {
        // a goal shut in a box of stone
        Set<String> solid = new HashSet<>();
        for (int x = 9; x <= 11; x++) for (int y = 63; y <= 67; y++) for (int z = -1; z <= 1; z++) if (x != 10 || z != 0 || y < 64 || y > 65) solid.add(x + "," + y + "," + z);
        World world = world(solid);
        Location start = new Location(world, 0.5, 64, 0.5);
        FlyNavigator flight = new FlyNavigator(mob(world, start), start, new Volume("cylinder", 30, 20, 10), 6.0);
        assertFalse(flight.canReach(new Location(world, 10.5, 64, 0.5)));
        // outside the room: not reachable, and moveTo goes to its edge instead
        FlyNavigator small = new FlyNavigator(mob(world(Set.of()), start), start, new Volume("cylinder", 5, 5, 5), 6.0);
        assertFalse(small.canReach(new Location(start.getWorld(), 20.5, 64, 0.5)));
    }

    @Test
    void airSpotsStayInTheAir() {
        Set<String> solid = new HashSet<>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) solid.add(x + ",63," + z);
        World world = world(solid);
        Location start = new Location(world, 0.5, 64, 0.5);
        FlyNavigator flight = new FlyNavigator(mob(world, start), start, null, 6.0);
        Location spot = flight.airSpot(new Location(world, 1.5, 63.2, 1.5));
        assertNotNull(spot);
        assertTrue(spot.getY() >= 64.0, "moved up out of the ground: " + spot);
        assertNull(new FlyNavigator(mob(world(allSolid()), start), start, null, 6.0).airSpot(start));
    }

    private static Set<String> allSolid() {
        Set<String> solid = new HashSet<>();
        for (int x = -3; x <= 3; x++) for (int y = 60; y <= 70; y++) for (int z = -3; z <= 3; z++) solid.add(x + "," + y + "," + z);
        return solid;
    }

    @Test
    void parsesFlightSettings() {
        Map<String, Object> section = Map.of(
                "navigation", Map.of("mode", "fly", "speed", 6),
                "volume", Map.of("shape", "sphere", "radius", 12),
                "waypoints", Map.of("lap", Map.of("points", List.of(List.of(1, 2, 3), Map.of("x", 4, "y", 5, "z", 6)), "mode", "back-and-forth")),
                "eqs", Map.of("sky", Map.of("generator", Map.of("type", "sphere", "radius", 6, "layers", 3), "tests", List.of(Map.of("type", "height", "value", 4)))),
                "tree", Map.of("type", "patrol", "route", "lap"));
        List<String> errors = new ArrayList<>();
        Behavior behavior = BehaviorParser.parse("flyer", section, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertNotNull(behavior);
        assertTrue(behavior.fly);
        assertEquals(6.0, behavior.flySpeed, 1.0E-9);
        assertEquals("sphere", behavior.volume.shape());
        assertEquals(2, behavior.routes.get("lap").points().size());
        assertFalse(behavior.routes.get("lap").loop());

        List<String> bad = new ArrayList<>();
        BehaviorParser.parse("flyer", Map.of("navigation", "swim", "tree", Map.of("type", "patrol", "route", "none")), bad);
        assertEquals(2, bad.size(), bad.toString());

        // a flying mob without a volume gets one (the leash of its blackboard as the radius)
        Behavior defaulted = BehaviorParser.parse("flyer", Map.of("navigation", "fly", "blackboard", Map.of("leash", 20), "tree", Map.of("type", "wait")), new ArrayList<>());
        assertEquals(20.0, defaulted.volume.radius(), 1.0E-9);
    }
}
