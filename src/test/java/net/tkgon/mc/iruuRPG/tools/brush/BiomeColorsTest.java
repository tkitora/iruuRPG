package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The colours of the biomes and the gradients made from two of them (no server needed). */
class BiomeColorsTest {

    @Test
    void theOverridesOfTheGameAreKept() {
        // the colours the biomes set themselves in the game's data (OverworldBiomes)
        assertEquals(0x90814D, BiomeColors.rgb(Biome.BADLANDS, BiomeColors.Kind.GRASS));
        assertEquals(0x9E814D, BiomeColors.rgb(Biome.BADLANDS, BiomeColors.Kind.FOLIAGE));
        assertEquals(0xB6DB61, BiomeColors.rgb(Biome.CHERRY_GROVE, BiomeColors.Kind.GRASS));
        assertEquals(0x6A7039, BiomeColors.rgb(Biome.SWAMP, BiomeColors.Kind.FOLIAGE));
        assertEquals(0x617B64, BiomeColors.rgb(Biome.SWAMP, BiomeColors.Kind.WATER));
        assertEquals(0x43D5EE, BiomeColors.rgb(Biome.WARM_OCEAN, BiomeColors.Kind.WATER));
    }

    @Test
    void theNetherAndTheEndCannotBePainted() {
        assertFalse(BiomeColors.known(Biome.NETHER_WASTES));
        assertFalse(BiomeColors.known(Biome.THE_END));
        assertFalse(BiomeColors.known(Biome.CUSTOM));
        assertTrue(BiomeColors.known(Biome.PLAINS));
        for (Biome biome : BiomeColors.all(BiomeColors.Kind.GRASS)) assertFalse(BiomeColors.label(biome).contains("_"), "a Japanese name for " + biome);
    }

    @Test
    void aRampGoesFromOneColourToTheOtherThroughLandBiomes() {
        List<Biome> ramp = BiomeColors.ramp(Biome.CHERRY_GROVE, Biome.SWAMP, 4, BiomeColors.Kind.GRASS);
        assertEquals(Biome.CHERRY_GROVE, ramp.get(0));
        assertEquals(Biome.SWAMP, ramp.get(ramp.size() - 1));
        assertTrue(ramp.size() >= 4, ramp.toString());
        int end = BiomeColors.rgb(Biome.SWAMP, BiomeColors.Kind.GRASS);
        double previous = Double.MAX_VALUE;
        for (Biome biome : ramp) {
            assertTrue(BiomeColors.sort(biome).equals("陸"), "only land in between: " + ramp);
            // each step is nearer the last colour
            double d = BlockColors.colorDistance(BiomeColors.rgb(biome, BiomeColors.Kind.GRASS), end);
            assertTrue(d <= previous, ramp.toString());
            previous = d;
        }
        // no colour twice in a row
        for (int index = 1; index < ramp.size(); index++) {
            assertTrue(BiomeColors.rgb(ramp.get(index), BiomeColors.Kind.GRASS) != BiomeColors.rgb(ramp.get(index - 1), BiomeColors.Kind.GRASS), ramp.toString());
        }
    }

    @Test
    void noSnowInAGreenGradientUnlessAnEndIsSnowy() {
        for (Biome biome : BiomeColors.ramp(Biome.JUNGLE, Biome.TAIGA, 8, BiomeColors.Kind.GRASS)) assertFalse(BiomeColors.sort(biome).startsWith("雪"), biome.toString());
        List<Biome> cold = BiomeColors.ramp(Biome.PLAINS, Biome.SNOWY_PLAINS, 4, BiomeColors.Kind.GRASS);
        assertEquals(Biome.SNOWY_PLAINS, cold.get(cold.size() - 1));
    }

    @Test
    void theWaterKindUsesTheWaterColours() {
        List<Biome> ramp = BiomeColors.ramp(Biome.WARM_OCEAN, Biome.FROZEN_OCEAN, 3, BiomeColors.Kind.WATER);
        assertEquals(Biome.WARM_OCEAN, ramp.get(0));
        assertTrue(ramp.contains(Biome.LUKEWARM_OCEAN) || ramp.contains(Biome.DEEP_LUKEWARM_OCEAN) || ramp.contains(Biome.OCEAN), ramp.toString());
    }
}
