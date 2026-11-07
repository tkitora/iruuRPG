package net.tkgon.mc.iruuRPG.tools.sea;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** t302, t304: the sea's chunks: a square round the player's, the nearest first, each once; a chunk's key both ways (negative places too). */
class SeaViewTest {

    @Test
    void theSeaIsASquareOfChunksTheNearestFirst() {
        List<Long> square = SeaView.square(-3, 5, 6);
        assertEquals(13 * 13, square.size());
        assertEquals(13 * 13, new HashSet<>(square).size());
        assertEquals(SeaView.chunk(-3, 5), square.get(0));
        // (the ring of 1 before the ring of 2)
        for (int index = 1; index < 9; index++) {
            long chunk = square.get(index);
            assertEquals(1, Math.max(Math.abs(SeaView.chunkX(chunk) + 3), Math.abs(SeaView.chunkZ(chunk) - 5)));
        }
        long last = square.get(square.size() - 1);
        assertEquals(6, Math.max(Math.abs(SeaView.chunkX(last) + 3), Math.abs(SeaView.chunkZ(last) - 5)));
        for (int[] at : new int[][]{{0, 0}, {-5, 7}, {1875000, -1875000}, {-1, -1}}) {
            long key = SeaView.chunk(at[0], at[1]);
            assertEquals(at[0], SeaView.chunkX(key));
            assertEquals(at[1], SeaView.chunkZ(key));
        }
        assertEquals(81, SeaView.DEFAULT_HEIGHT);
    }
}
