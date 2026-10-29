package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NavigationSwitchTest {

    /** Stone up to y 63, air above. */
    private static World world() {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn((int) call.getArgument(1) <= 63 ? Material.STONE : Material.AIR);
            return block;
        });
        return world;
    }

    /** A host that only records the changes of the way it moves and where it was sent. */
    private static final class Host implements BehaviorHost {
        final Mob mob;
        final Location at;
        boolean flying;
        Location sentTo;

        Host(World world, double y) {
            at = new Location(world, 0.5, y, 0.5);
            mob = mock(Mob.class);
            when(mob.getWorld()).thenReturn(world);
            when(mob.getLocation()).thenAnswer(call -> at.clone());
        }

        @Override public Mob mob() { return mob; }
        @Override public int tick() { return 0; }
        @Override public Player findTarget(double range) { return null; }
        @Override public boolean valid(Player player, double range) { return false; }
        @Override public boolean moveTo(Location to, double speed) { sentTo = to; return true; }
        @Override public void stopMoving() { }
        @Override public boolean hasPath() { return sentTo != null; }
        @Override public void face(Location to) { }
        @Override public void attack(Player target, double rate) { }
        @Override public int skillCount() { return 0; }
        @Override public boolean startSkill(int index, Player target) { return false; }
        @Override public boolean skillRunning() { return false; }
        @Override public boolean shoot(Player target, double rate) { return false; }
        @Override public double hpRatio() { return 1.0; }
        @Override public boolean canSee(Player player) { return false; }
        @Override public Location home() { return at; }
        @Override public int crowdNear(Location to, double radius) { return 0; }
        @Override public boolean flying() { return flying; }
        @Override public boolean setFlying(boolean fly) { flying = fly; return true; }
    }

    private static Ctx ctx(Host host) {
        return new Ctx(host, new Blackboard(), Map.of());
    }

    @Test
    void takesOffAndRises() {
        Host host = new Host(world(), 64.0);
        Ctx c = ctx(host);
        Tasks.SetNavigation task = new Tasks.SetNavigation(true, 4.0, true, 1.0, 80);
        assertEquals(Status.RUNNING, task.run(c));
        assertTrue(host.flying);
        assertEquals(68.0, host.sentTo.getY(), 1.0E-9);
        host.at.setY(67.6);
        c.now = 10;
        assertEquals(Status.SUCCESS, task.run(c));
        // already flying: nothing to do
        assertEquals(Status.SUCCESS, new Tasks.SetNavigation(true, 4.0, true, 1.0, 80).run(c));
    }

    @Test
    void landsBeforeItWalks() {
        Host host = new Host(world(), 70.0);
        host.flying = true;
        Ctx c = ctx(host);
        Tasks.SetNavigation task = new Tasks.SetNavigation(false, 0.0, true, 1.0, 80);
        assertEquals(Status.RUNNING, task.run(c));
        assertTrue(host.flying);
        assertEquals(64.0, host.sentTo.getY(), 1.0E-9);
        host.at.setY(64.2);
        c.now = 10;
        assertEquals(Status.SUCCESS, task.run(c));
        assertFalse(host.flying);
    }

    @Test
    void fallsWithoutLandingAndGivesUpOnTime() {
        Host host = new Host(world(), 70.0);
        host.flying = true;
        assertEquals(Status.SUCCESS, new Tasks.SetNavigation(false, 0.0, false, 1.0, 80).run(ctx(host)));
        assertFalse(host.flying);

        host.flying = true;
        Ctx c = ctx(host);
        Tasks.SetNavigation task = new Tasks.SetNavigation(false, 0.0, true, 1.0, 20);
        assertEquals(Status.RUNNING, task.run(c));
        c.now = 21;
        assertEquals(Status.SUCCESS, task.run(c));
        assertFalse(host.flying);
    }

    @Test
    void aWalkerThatTakesOffGetsARoomAndItsPaces() {
        Map<String, Object> section = Map.of(
                "navigation", Map.of("mode", "walk", "fly-speed", 7, "walk-speed", 2.2),
                "fsm", Map.of("initial", "ground", "states", Map.of(
                        "ground", Map.of("tree", Map.of("type", "wait", "seconds", 1)),
                        "air", Map.of("enter", Map.of("type", "set-navigation", "mode", "fly", "rise", 5), "tree", Map.of("type", "wait", "seconds", 1)))));
        List<String> errors = new ArrayList<>();
        Behavior behavior = BehaviorParser.parse("bat", section, errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertNotNull(behavior);
        assertFalse(behavior.fly);
        assertNotNull(behavior.volume);
        assertEquals(7.0, behavior.flySpeed, 1.0E-9);
        assertEquals(2.2, behavior.walkSpeed, 1.0E-9);

        errors.clear();
        BehaviorParser.parse("bat", Map.of("navigation", Map.of("walk-speed", 50), "tree", Map.of("type", "set-navigation", "mode", "swim")), errors);
        assertEquals(2, errors.size(), errors.toString());
    }
}
