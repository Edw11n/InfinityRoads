package com.infinityroads.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Estructura de datos completa y persistible de la cola de procesamiento por lotes de chunks.
 */
public class BatchQueueData {

    private int version = 1;
    private String worldName;
    private int radius;
    private BatchTaskState state;
    private long createdAt;
    private long updatedAt;
    private List<ChunkEntry> chunks;

    public BatchQueueData() {
        this.worldName = "world";
        this.radius = 0;
        this.state = BatchTaskState.IDLE;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.chunks = new ArrayList<>();
    }

    public BatchQueueData(String worldName, int radius, List<ChunkEntry> chunks) {
        this.version = 1;
        this.worldName = Objects.requireNonNull(worldName, "worldName no puede ser nulo");
        this.radius = radius;
        this.state = BatchTaskState.PAUSED;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.chunks = new ArrayList<>(Objects.requireNonNull(chunks, "chunks no puede ser nulo"));
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public BatchTaskState getState() {
        return state;
    }

    public void setState(BatchTaskState state) {
        this.state = Objects.requireNonNull(state, "state no puede ser nulo");
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ChunkEntry> getChunks() {
        return chunks;
    }

    public void setChunks(List<ChunkEntry> chunks) {
        this.chunks = chunks != null ? chunks : new ArrayList<>();
    }

    public int getTotalCount() {
        return chunks.size();
    }

    public int getPendingCount() {
        int count = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.PENDING) {
                count++;
            }
        }
        return count;
    }

    public int getProcessingCount() {
        int count = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.PROCESSING) {
                count++;
            }
        }
        return count;
    }

    public int getCompletedCount() {
        int count = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    public int getSkippedCount() {
        int count = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.SKIPPED) {
                count++;
            }
        }
        return count;
    }

    public int getFailedCount() {
        int count = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.FAILED) {
                count++;
            }
        }
        return count;
    }

    public int getProcessedCount() {
        return getCompletedCount() + getSkippedCount();
    }

    public Optional<ChunkEntry> getNextPendingChunk() {
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.PENDING) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Protocolo de recuperación de fallos y reinicios del servidor.
     * Restablece cualquier chunk en estado PROCESSING a PENDING e incrementa el contador de actualización.
     * Si el estado global era RUNNING, lo restablece forzosamente a PAUSED.
     * Garantiza que el servidor jamás reanude el procesamiento automáticamente al arrancar.
     *
     * @return Número de chunks en vuelo que fueron restablecidos a PENDING.
     */
    public int resetInFlightChunks() {
        int resetCount = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.PROCESSING) {
                entry.setStatus(ProcessingStatus.PENDING);
                resetCount++;
            }
        }
        if (this.state == BatchTaskState.RUNNING) {
            this.state = BatchTaskState.PAUSED;
        }
        this.updatedAt = System.currentTimeMillis();
        return resetCount;
    }

    /**
     * Restablece todos los chunks en estado FAILED a PENDING para permitir su reintento con /ir chunks retry.
     *
     * @return Número de chunks fallidos restablecidos.
     */
    public int resetFailedChunks() {
        int resetCount = 0;
        for (ChunkEntry entry : chunks) {
            if (entry.getStatus() == ProcessingStatus.FAILED) {
                entry.resetForRetry();
                resetCount++;
            }
        }
        if (this.state == BatchTaskState.COMPLETED && resetCount > 0) {
            this.state = BatchTaskState.PAUSED;
        }
        this.updatedAt = System.currentTimeMillis();
        return resetCount;
    }

    public boolean isAllDone() {
        return getPendingCount() == 0 && getProcessingCount() == 0;
    }
}
