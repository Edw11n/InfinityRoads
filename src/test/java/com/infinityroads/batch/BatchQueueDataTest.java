package com.infinityroads.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de BatchQueueData y ChunkEntry")
class BatchQueueDataTest {

    @Test
    @DisplayName("Conteo preciso de estados de chunks en la cola")
    void testQueueCounters() {
        List<ChunkEntry> entries = new ArrayList<>();

        ChunkEntry c1 = new ChunkEntry(0, 6);
        c1.setStatus(ProcessingStatus.PENDING);

        ChunkEntry c2 = new ChunkEntry(0, 7);
        c2.setStatus(ProcessingStatus.PROCESSING);

        ChunkEntry c3 = new ChunkEntry(0, 8);
        c3.markCompleted();

        ChunkEntry c4 = new ChunkEntry(0, 9);
        c4.markSkipped();

        ChunkEntry c5 = new ChunkEntry(0, 10);
        c5.recordFailure("Error de prueba", 1); // falla de inmediato con maxRetries = 1

        entries.add(c1);
        entries.add(c2);
        entries.add(c3);
        entries.add(c4);
        entries.add(c5);

        BatchQueueData data = new BatchQueueData("world", 1000, entries);

        assertEquals(5, data.getTotalCount());
        assertEquals(1, data.getPendingCount());
        assertEquals(1, data.getProcessingCount());
        assertEquals(1, data.getCompletedCount());
        assertEquals(1, data.getSkippedCount());
        assertEquals(1, data.getFailedCount());
        assertEquals(2, data.getProcessedCount());
        assertFalse(data.isAllDone());

        assertTrue(data.getNextPendingChunk().isPresent());
        assertEquals(c1, data.getNextPendingChunk().get());
    }

    @Test
    @DisplayName("Protocolo de recuperación tras caída: restablece PROCESSING a PENDING y RUNNING a PAUSED")
    void testCrashRecoveryProtocol() {
        List<ChunkEntry> entries = new ArrayList<>();

        ChunkEntry inFlight = new ChunkEntry(-1, -7);
        inFlight.markProcessing();

        ChunkEntry completed = new ChunkEntry(0, -7);
        completed.markCompleted();

        ChunkEntry pending = new ChunkEntry(-1, -8);

        entries.add(inFlight);
        entries.add(completed);
        entries.add(pending);

        BatchQueueData data = new BatchQueueData("world", 500, entries);
        data.setState(BatchTaskState.RUNNING);

        // Ejecutar protocolo de recuperación
        int resetCount = data.resetInFlightChunks();

        assertEquals(1, resetCount);
        assertEquals(ProcessingStatus.PENDING, inFlight.getStatus(), "El chunk en vuelo debe haber vuelto a PENDING");
        assertEquals(ProcessingStatus.COMPLETED, completed.getStatus(), "El chunk completado no debe mutar");
        assertEquals(ProcessingStatus.PENDING, pending.getStatus());
        assertEquals(BatchTaskState.PAUSED, data.getState(), "El estado global debe haberse forzado a PAUSED");
        assertEquals(2, data.getPendingCount());
        assertEquals(0, data.getProcessingCount());
    }

    @Test
    @DisplayName("Reintento de chunks fallidos mediante resetFailedChunks")
    void testResetFailedChunks() {
        List<ChunkEntry> entries = new ArrayList<>();

        ChunkEntry failed1 = new ChunkEntry(10, 0);
        failed1.recordFailure("Timeout", 1);

        ChunkEntry failed2 = new ChunkEntry(11, 0);
        failed2.recordFailure("OOM", 1);

        ChunkEntry completed = new ChunkEntry(12, 0);
        completed.markCompleted();

        entries.add(failed1);
        entries.add(failed2);
        entries.add(completed);

        BatchQueueData data = new BatchQueueData("world", 500, entries);
        data.setState(BatchTaskState.COMPLETED);

        int reset = data.resetFailedChunks();

        assertEquals(2, reset);
        assertEquals(ProcessingStatus.PENDING, failed1.getStatus());
        assertEquals(0, failed1.getAttempts());
        assertNull(failed1.getLastError());
        assertEquals(ProcessingStatus.PENDING, failed2.getStatus());
        assertEquals(BatchTaskState.PAUSED, data.getState(), "Si estaba COMPLETED, al restablecer fallos pasa a PAUSED");
    }

    @Test
    @DisplayName("ChunkEntry: política de reintentos escalonados hasta alcanzar maxRetries")
    void testChunkEntryRetries() {
        ChunkEntry entry = new ChunkEntry(0, 15);
        assertEquals(0, entry.getAttempts());
        assertEquals(ProcessingStatus.PENDING, entry.getStatus());

        // Intento 1 con maxRetries = 3 -> vuelve a PENDING para reintento
        entry.recordFailure("Fallo 1", 3);
        assertEquals(1, entry.getAttempts());
        assertEquals(ProcessingStatus.PENDING, entry.getStatus());
        assertEquals("Fallo 1", entry.getLastError());

        // Intento 2
        entry.recordFailure("Fallo 2", 3);
        assertEquals(2, entry.getAttempts());
        assertEquals(ProcessingStatus.PENDING, entry.getStatus());

        // Intento 3 -> alcanza el límite y pasa a FAILED
        entry.recordFailure("Fallo 3", 3);
        assertEquals(3, entry.getAttempts());
        assertEquals(ProcessingStatus.FAILED, entry.getStatus());
        assertEquals("Fallo 3", entry.getLastError());
    }
}
