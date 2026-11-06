package net.tkgon.mc.iruuRPG.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t277: a model whose feet are in a wall (or sunk into soul sand) takes its light from a block near that lets light in, so it is not drawn black. */
class ModelLightTest {

    /** The blocks that let no light in, as "dx,dy,dz" from the feet. */
    private static ModelInstance.Open closed(Set<String> walls) {
        return (dx, dy, dz) -> !walls.contains(dx + "," + dy + "," + dz);
    }

    @Test
    void feetInTheOpenKeepTheLightOfTheFeet() {
        assertArrayEquals(new int[] {0, 0, 0}, ModelInstance.lightFrom(2, closed(Set.of())));
    }

    @Test
    void feetSunkInABlockTakeTheLightOverIt() {
        assertArrayEquals(new int[] {0, 1, 0}, ModelInstance.lightFrom(2, closed(Set.of("0,0,0"))));
    }

    @Test
    void aModelInAWallUpToItsTopTakesTheLightOverItsTop() {
        assertArrayEquals(new int[] {0, 3, 0}, ModelInstance.lightFrom(3, closed(Set.of("0,0,0", "0,1,0", "0,2,0"))));
    }

    @Test
    void aModelBuriedInAColumnTakesTheLightBesideIt() {
        // the column is wall up to the top and over it; only one side of the middle is open
        Set<String> walls = new java.util.HashSet<>(Set.of("0,0,0", "0,1,0", "0,2,0", "0,3,0", "0,4,0", "1,0,0", "-1,0,0", "0,0,1", "0,0,-1", "1,2,0", "-1,2,0", "0,2,1"));
        assertArrayEquals(new int[] {0, 2, -1}, ModelInstance.lightFrom(4, closed(walls)));
    }

    @Test
    void aModelWithNoOpenBlockNearKeepsTheLightItHad() {
        assertNull(ModelInstance.lightFrom(2, (dx, dy, dz) -> false));
    }

    @Test
    void theSearchGoesNoHigherThanEightBlocks() {
        int[] highest = {0};
        ModelInstance.lightFrom(40, (dx, dy, dz) -> {
            highest[0] = Math.max(highest[0], dy);
            return false;
        });
        assertTrue(highest[0] <= 8);
    }

    @Test
    void wallsAndSoulSandLetNoLightInSlabsAndGlassDo() {
        assertTrue(Material.STONE.isOccluding());
        assertTrue(Material.SOUL_SAND.isOccluding());
        assertTrue(Material.MUD.isOccluding());
        assertFalse(Material.AIR.isOccluding());
        assertFalse(Material.STONE_SLAB.isOccluding());
        assertFalse(Material.GLASS.isOccluding());
        assertFalse(Material.OAK_LEAVES.isOccluding());
    }
}
