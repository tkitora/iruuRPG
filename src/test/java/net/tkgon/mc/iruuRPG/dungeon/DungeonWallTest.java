package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonWallTest {

    private static final DungeonWall.Pos A = new DungeonWall.Pos(1, 64, 1);
    private static final DungeonWall.Pos B = new DungeonWall.Pos(1, 65, 1);

    @Test
    void chanceMobsAddOneKillEach() {
        // 10 mobs always there, 80%: 8 kills
        assertEquals(8, DungeonWall.requiredKills(10, 0, 0.8));
        // two more came by chance: 8 + 2 (not ceil(12 * 0.8) = 10 by luck of rounding)
        assertEquals(10, DungeonWall.requiredKills(12, 2, 0.8));
        assertEquals(4, DungeonWall.requiredKills(4, 1, 0.8)); // 3 always there: 3 (rounded up) + 1
    }

    @Test
    void chanceRolls() {
        assertTrue(DungeonWall.appears(100, 99));
        assertTrue(DungeonWall.appears(35, 34));
        assertFalse(DungeonWall.appears(35, 35));
        assertFalse(DungeonWall.appears(5, 50));
    }

    @Test
    void conditions() {
        DungeonWall kills = new DungeonWall(DungeonWall.Condition.KILLS, 5, -1, List.of(A));
        assertFalse(kills.met(4, 0, 4, false));
        assertTrue(kills.met(5, 0, 4, false));

        DungeonWall gather = new DungeonWall(DungeonWall.Condition.GATHER, 3, -1, List.of(A));
        assertFalse(gather.met(0, 2, 4, false));
        assertTrue(gather.met(0, 3, 4, false));
        // only two can stand: two are enough
        assertTrue(gather.met(0, 2, 2, false));
        assertFalse(gather.met(0, 0, 0, false));

        DungeonWall target = new DungeonWall(DungeonWall.Condition.TARGET, 1, 2, List.of(A));
        assertFalse(target.met(99, 4, 4, false));
        assertTrue(target.met(0, 0, 0, true));
    }

    @Test
    void blocksToggleAndTargetsFollowRemovedMobs() {
        DungeonWall wall = new DungeonWall(DungeonWall.Condition.TARGET, 1, 3, List.of(A, A));
        assertEquals(1, wall.blocks().size());
        wall = wall.toggle(B);
        assertEquals(List.of(A, B), wall.blocks());
        assertEquals(List.of(B), wall.toggle(A).blocks());

        assertEquals(2, wall.afterSpawnRemoved(0).target());
        assertEquals(-1, wall.afterSpawnRemoved(3).target());
        assertEquals(3, wall.afterSpawnRemoved(5).target());
    }

    @Test
    void savedAndReadBack(@TempDir File folder) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        File dungeons = new File(folder, "dungeons");
        assertTrue(dungeons.mkdirs());
        Files.writeString(new File(dungeons, "cave.yml").toPath(), """
                id: cave
                name: test
                boss: cave_boss
                spawns:
                  - {mob: cave_crawler, x: 1.5, y: 64.0, z: 1.5}
                  - {mob: cave_bones, x: 2.5, y: 64.0, z: 2.5, chance: 35}
                  - {mob: cave_spider, x: 3.5, y: 64.0, z: 3.5, chance: 10}
                  - {mob: cave_boss, x: 9.5, y: 64.0, z: 9.5}
                walls:
                  - {when: kills, count: 2, blocks: [[1, 64, 1], [1, 65, 1]]}
                  - {when: target, target: 3, blocks: [[5, 64, 5]]}
                  - {when: gather, count: 2, blocks: [[7, 64, 7]]}
                  - {when: sideways, blocks: []}
                """);
        DungeonRegistry registry = new DungeonRegistry(plugin);
        registry.reload();
        DungeonDefinition cave = registry.find("cave").orElseThrow();
        assertEquals(100, cave.spawns().get(0).chance());
        assertEquals(35, cave.spawns().get(1).chance());
        List<DungeonWall> walls = registry.walls("cave");
        assertEquals(4, walls.size());
        assertEquals(List.of(A, B), walls.get(0).blocks());
        assertEquals(2, walls.get(1).target()); // #3 in the file
        assertEquals(DungeonWall.Condition.GATHER, walls.get(2).when());
        assertEquals(DungeonWall.Condition.KILLS, walls.get(3).when()); // unknown: kills
        assertEquals(1, registry.wallAt("cave", new DungeonWall.Pos(5, 64, 5)));

        // removing mob #1 keeps the wall on the same mob (now #2); removing that mob unsets it
        registry.removeSpawn(cave, 0);
        assertEquals(1, registry.walls("cave").get(1).target());
        registry.reload();
        cave = registry.find("cave").orElseThrow();
        assertEquals(35, cave.spawns().get(0).chance());
        assertEquals(1, registry.walls("cave").get(1).target());
        assertEquals(List.of(A, B), registry.walls("cave").get(0).blocks());
        registry.removeSpawn(cave, 1);
        assertEquals(-1, registry.walls("cave").get(1).target());
        registry.removeWall(registry.find("cave").orElseThrow(), 0);
        registry.reload();
        assertEquals(3, registry.walls("cave").size());
        assertEquals(DungeonWall.Condition.TARGET, registry.walls("cave").get(0).when());
    }
}
