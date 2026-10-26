package com.infinityroads.batch;

import org.bukkit.Chunk;

import java.util.Objects;

/**
 * Registro inmutable que representa las coordenadas de un chunk (X, Z).
 */
public record ChunkCoord(int x, int z) {

    public long toChunkKey() {
        return Chunk.getChunkKey(x, z);
    }

    public int getOriginBlockX() {
        return x << 4;
    }

    public int getOriginBlockZ() {
        return z << 4;
    }

    @Override
    public String toString() {
        return String.format("[%d, %d]", x, z);
    }
}
