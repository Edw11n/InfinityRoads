package com.infinityroads.batch;

import java.util.Objects;

/**
 * Representa una entrada de trabajo para un chunk específico dentro de la cola persistente.
 */
public class ChunkEntry {

    private final int x;
    private final int z;
    private ProcessingStatus status;
    private int attempts;
    private String lastError;
    private long queuedAt;
    private long completedAt;

    public ChunkEntry(int x, int z) {
        this.x = x;
        this.z = z;
        this.status = ProcessingStatus.PENDING;
        this.attempts = 0;
        this.lastError = null;
        this.queuedAt = System.currentTimeMillis();
        this.completedAt = 0L;
    }

    public ChunkEntry(int x, int z, ProcessingStatus status, int attempts, String lastError, long queuedAt, long completedAt) {
        this.x = x;
        this.z = z;
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
        this.attempts = attempts;
        this.lastError = lastError;
        this.queuedAt = queuedAt;
        this.completedAt = completedAt;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public ChunkCoord getCoord() {
        return new ChunkCoord(x, z);
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = Objects.requireNonNull(status, "status no puede ser nulo");
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public long getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(long queuedAt) {
        this.queuedAt = queuedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public void markProcessing() {
        this.status = ProcessingStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = ProcessingStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
        this.lastError = null;
    }

    public void markSkipped() {
        this.status = ProcessingStatus.SKIPPED;
        this.completedAt = System.currentTimeMillis();
        this.lastError = null;
    }

    public void recordFailure(String error, int maxRetries) {
        this.attempts++;
        this.lastError = error;
        if (this.attempts >= maxRetries) {
            this.status = ProcessingStatus.FAILED;
        } else {
            this.status = ProcessingStatus.PENDING;
        }
    }

    public void resetForRetry() {
        if (this.status == ProcessingStatus.FAILED) {
            this.status = ProcessingStatus.PENDING;
            this.attempts = 0;
            this.lastError = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkEntry that = (ChunkEntry) o;
        return x == that.x && z == that.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, z);
    }

    @Override
    public String toString() {
        return String.format("ChunkEntry{x=%d, z=%d, status=%s, attempts=%d}", x, z, status, attempts);
    }
}
