package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DungeonBgmTest {

    private static DungeonDefinition dungeon(String route, List<String> boss) {
        return new DungeonDefinition("cave", "Cave", List.of(), 1, 900, 3, 0L, 0L,
                new DungeonDefinition.Point(0.5, 65.0, 0.5, 0.0f, 0.0f), List.of(), null, null, 300,
                DungeonDefinition.DeathMode.REVIVE, 4, null, null, null, null, List.of(), route, boss);
    }

    @Test
    void aDungeonWithoutMusicIsAsItWas() {
        DungeonDefinition definition = dungeon(null, List.of());
        assertFalse(definition.hasBgm());
        assertNull(definition.bgmRoute());
        assertTrue(definition.bgmBoss().isEmpty());
    }

    @Test
    void theBossSongsAreThreeAtMostAndKeptTogetherAtTheFront() {
        DungeonDefinition definition = dungeon("  Route_Song ", java.util.Arrays.asList("A", null, "  ", "B", "C", "D"));
        assertEquals("route_song", definition.bgmRoute());
        assertEquals(List.of("a", "b", "c"), definition.bgmBoss());
        assertTrue(definition.hasBgm());
        assertFalse(dungeon(" ", List.of()).hasBgm());
        assertTrue(dungeon(null, List.of("x")).hasBgm());
    }

    @Test
    void everyChangeKeepsTheMusic() {
        DungeonDefinition definition = dungeon("route", List.of("one", "two"));
        DungeonDefinition.Point point = new DungeonDefinition.Point(1, 2, 3, 0, 0);
        for (DungeonDefinition changed : List.of(definition.withEntry(point), definition.withSpawns(List.of()), definition.withBoss("boss"), definition.withExit(point),
                definition.withRewardRoom(point), definition.withChest(point), definition.withBossTrigger(point), definition.withBossRoom(point), definition.withRoadChests(List.of()))) {
            assertEquals("route", changed.bgmRoute());
            assertEquals(List.of("one", "two"), changed.bgmBoss());
        }
        DungeonDefinition music = definition.withBgm(null, List.of("only"));
        assertNull(music.bgmRoute());
        assertEquals(List.of("only"), music.bgmBoss());
        assertEquals("Cave", music.name());
    }

    @Test
    void theBossSongOfAPhase() {
        // no phase (1): the first song; the next phase, the next song; more phases than songs: the last one stays
        assertEquals(0, DungeonMusic.songIndex(3, 1));
        assertEquals(1, DungeonMusic.songIndex(3, 2));
        assertEquals(2, DungeonMusic.songIndex(3, 3));
        assertEquals(2, DungeonMusic.songIndex(3, 7));
        assertEquals(0, DungeonMusic.songIndex(1, 3), "one song: it plays on");
        assertEquals(1, DungeonMusic.songIndex(2, 3));
        assertEquals(0, DungeonMusic.songIndex(3, 0), "phase 0 or less is the first song");
        assertEquals(0, DungeonMusic.songIndex(0, 2));
    }

    @Test
    void theMusicIsKeptInTheFileOfTheDungeon(@TempDir File folder) throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        File dungeons = new File(folder, "dungeons");
        assertTrue(dungeons.mkdirs());
        Files.writeString(new File(dungeons, "cave.yml").toPath(), """
                id: cave
                name: "Cave"
                spawns:
                - mob: cave_boss
                  x: 1.0
                  y: 65.0
                  z: 1.0
                boss: cave_boss
                bgm:
                  route: atlas_dungeon
                  boss:
                  - atlas_vindicator
                  - second
                """);
        DungeonRegistry registry = new DungeonRegistry(plugin);
        registry.reload();
        DungeonDefinition read = registry.find("cave").orElseThrow();
        assertEquals("atlas_dungeon", read.bgmRoute());
        assertEquals(List.of("atlas_vindicator", "second"), read.bgmBoss());

        // changed in the editor: written, and read back by the next start
        registry.setBgm(read, "other_route", List.of("one"));
        DungeonRegistry again = new DungeonRegistry(plugin);
        again.reload();
        DungeonDefinition saved = again.find("cave").orElseThrow();
        assertEquals("other_route", saved.bgmRoute());
        assertEquals(List.of("one"), saved.bgmBoss());
        assertEquals("cave_boss", saved.boss(), "the rest of the file is as it was");
        assertEquals(1, saved.spawns().size());

        // all songs taken away: the section is gone from the file
        again.setBgm(saved, null, List.of());
        DungeonRegistry third = new DungeonRegistry(plugin);
        third.reload();
        assertFalse(third.find("cave").orElseThrow().hasBgm());
        assertFalse(Files.readString(new File(dungeons, "cave.yml").toPath()).contains("bgm"));
    }

    @Test
    void aDungeonFileWithoutAMusicSectionIsRead() throws IOException {
        // (the file that comes with the plugin has none)
        JavaPlugin plugin = mock(JavaPlugin.class);
        File folder = Files.createTempDirectory("dungeon-bgm").toFile();
        when(plugin.getDataFolder()).thenReturn(folder);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        File dungeons = new File(folder, "dungeons");
        assertTrue(dungeons.mkdirs());
        Files.writeString(new File(dungeons, "plain.yml").toPath(), "id: plain\nname: Plain\n");
        DungeonRegistry registry = new DungeonRegistry(plugin);
        registry.reload();
        assertNotNull(registry.find("plain").orElse(null));
        assertFalse(registry.find("plain").orElseThrow().hasBgm());
    }
}
