package net.tkgon.mc.iruuRPG.mob.behavior;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The `summon` task: read from the tree, asks the mob for its brood, succeeds when they came or the cap is reached. */
class SummonTaskTest {

    @Test
    void readsAndChecksItsSettings() {
        List<String> errors = new ArrayList<>();
        Behavior behavior = BehaviorParser.parse("brood", Map.of("tree",
                Map.of("type", "summon", "mob", "Brood_Spiderling", "count", 3, "radius", 5, "max-alive", 8)), errors);
        assertTrue(errors.isEmpty(), errors.toString());
        assertNotNull(behavior);

        errors.clear();
        BehaviorParser.parse("brood", Map.of("tree", Map.of("type", "summon", "count", 40, "radius", 0.2, "max-alive", 99)), errors);
        assertEquals(4, errors.size(), errors.toString());
    }

    @Test
    void succeedsWhenTheyCameOrTheCapIsReachedAndFailsWithoutAPlace() {
        int[] answer = {2};
        List<String> asked = new ArrayList<>();
        BehaviorHost host = new BehaviorHost() {
            @Override public org.bukkit.entity.Mob mob() { return null; }
            @Override public int tick() { return 0; }
            @Override public org.bukkit.entity.Player findTarget(double range) { return null; }
            @Override public boolean valid(org.bukkit.entity.Player player, double range) { return false; }
            @Override public boolean moveTo(org.bukkit.Location at, double speed) { return false; }
            @Override public void stopMoving() { }
            @Override public boolean hasPath() { return false; }
            @Override public void face(org.bukkit.Location at) { }
            @Override public void attack(org.bukkit.entity.Player target, double rate) { }
            @Override public int skillCount() { return 0; }
            @Override public boolean startSkill(int index, org.bukkit.entity.Player target) { return false; }
            @Override public boolean skillRunning() { return false; }
            @Override public boolean shoot(org.bukkit.entity.Player target, double rate) { return false; }
            @Override public double hpRatio() { return 1.0; }
            @Override public boolean canSee(org.bukkit.entity.Player player) { return false; }
            @Override public org.bukkit.Location home() { return null; }
            @Override public int crowdNear(org.bukkit.Location at, double radius) { return 0; }

            @Override
            public int summon(String mobId, int count, double radius, int maxAlive, org.bukkit.entity.Player target) {
                asked.add(mobId + " " + count + " " + radius + " " + maxAlive);
                return answer[0];
            }
        };
        Ctx c = new Ctx(host, new Blackboard(), Map.of());
        Tasks.Summon summon = new Tasks.Summon("brood_spiderling", 3, 5.0, 8);
        assertEquals(Status.SUCCESS, summon.run(c));
        assertEquals(List.of("brood_spiderling 3 5.0 8"), asked);
        answer[0] = 0;   // as many as allowed live already
        assertEquals(Status.SUCCESS, summon.run(c));
        answer[0] = -1;  // no such mob, or no place
        assertEquals(Status.FAILURE, summon.run(c));
    }
}
