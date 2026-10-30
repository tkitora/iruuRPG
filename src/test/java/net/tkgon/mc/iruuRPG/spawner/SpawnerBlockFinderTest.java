package net.tkgon.mc.iruuRPG.spawner;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which spawner blocks count as buried, and what a deleted one becomes (sides in the order down, up, north, south, east, west). */
class SpawnerBlockFinderTest {

    @Test
    void buriedMeansSolidOnEverySide() {
        assertTrue(SpawnerBlockFinder.buried(List.of(Material.STONE, Material.STONE, Material.DIRT, Material.STONE, Material.DEEPSLATE, Material.SPAWNER)));
        assertFalse(SpawnerBlockFinder.buried(List.of(Material.STONE, Material.AIR, Material.STONE, Material.STONE, Material.STONE, Material.STONE)));
        assertFalse(SpawnerBlockFinder.buried(List.of(Material.STONE, Material.CAVE_AIR, Material.STONE, Material.STONE, Material.STONE, Material.STONE)));
    }

    @Test
    void aDeletedSpawnerBecomesWhatMostOfItsSidesAre() {
        // in the ground: the ground
        assertEquals(Material.STONE, SpawnerBlockFinder.fill(List.of(Material.DIRT, Material.STONE, Material.STONE, Material.STONE, Material.DIRT, Material.GRANITE)));
        // in an open dungeon room (only the floor under it): the floor
        assertEquals(Material.MOSSY_COBBLESTONE, SpawnerBlockFinder.fill(List.of(Material.MOSSY_COBBLESTONE, Material.CAVE_AIR, Material.CAVE_AIR,
                Material.CAVE_AIR, Material.CAVE_AIR, Material.CAVE_AIR)));
        // a tie: the one below
        assertEquals(Material.DIRT, SpawnerBlockFinder.fill(List.of(Material.DIRT, Material.STONE, Material.DIRT, Material.STONE, Material.AIR, Material.AIR)));
        // nothing full round it (air, glass, another spawner, a chest): air
        assertEquals(Material.AIR, SpawnerBlockFinder.fill(List.of(Material.AIR, Material.GLASS, Material.SPAWNER, Material.CHEST, Material.WATER, Material.AIR)));
    }
}
