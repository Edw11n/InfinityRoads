package com.infinityroads.batch;

import org.bukkit.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de ChunkCoord")
class ChunkCoordTest {

    @Test
    @DisplayName("Cálculo de origen de bloques y chunk key")
    void testChunkCoordBasics() {
        ChunkCoord coord = new ChunkCoord(5, -8);

        assertEquals(5, coord.x());
        assertEquals(-8, coord.z());
        assertEquals(5 * 16, coord.getOriginBlockX());
        assertEquals(-8 * 16, coord.getOriginBlockZ());
        assertEquals(Chunk.getChunkKey(5, -8), coord.toChunkKey());
        assertEquals("[5, -8]", coord.toString());
    }

    @Test
    @DisplayName("Igualdad y hashcode en registro inmutable")
    void testEquality() {
        ChunkCoord c1 = new ChunkCoord(10, 20);
        ChunkCoord c2 = new ChunkCoord(10, 20);
        ChunkCoord c3 = new ChunkCoord(10, 21);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
    }
}
