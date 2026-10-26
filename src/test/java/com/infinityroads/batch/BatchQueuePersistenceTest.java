package com.infinityroads.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de persistencia atómica y recuperación de queue.json")
class BatchQueuePersistenceTest {

    @Test
    @DisplayName("Guardado atómico y carga íntegra de queue.json")
    void testSaveAndLoad(@TempDir Path tempDir) throws IOException {
        Path queueFile = tempDir.resolve("queue.json");
        BatchQueuePersistence persistence = new BatchQueuePersistence(queueFile, null);

        assertFalse(persistence.load().isPresent(), "Si el archivo no existe, debe retornar Optional.empty()");

        List<ChunkEntry> entries = new ArrayList<>();
        entries.add(new ChunkEntry(0, 6));
        entries.add(new ChunkEntry(0, 7));

        BatchQueueData data = new BatchQueueData("world", 1000, entries);
        data.setState(BatchTaskState.PAUSED);

        persistence.save(data);

        assertTrue(Files.exists(queueFile), "El archivo queue.json debe existir tras guardar");
        assertFalse(Files.exists(tempDir.resolve("queue.json.tmp")), "El archivo temporal debe haber sido limpiado tras la operación atómica");

        Optional<BatchQueueData> loadedOpt = persistence.load();
        assertTrue(loadedOpt.isPresent());

        BatchQueueData loaded = loadedOpt.get();
        assertEquals("world", loaded.getWorldName());
        assertEquals(1000, loaded.getRadius());
        assertEquals(BatchTaskState.PAUSED, loaded.getState());
        assertEquals(2, loaded.getTotalCount());
        assertEquals(2, loaded.getPendingCount());
    }

    @Test
    @DisplayName("Recuperación automática tras crash durante la carga: RUNNING -> PAUSED y PROCESSING -> PENDING")
    void testCrashRecoveryOnLoad(@TempDir Path tempDir) throws IOException {
        Path queueFile = tempDir.resolve("queue.json");
        BatchQueuePersistence persistence = new BatchQueuePersistence(queueFile, null);

        List<ChunkEntry> entries = new ArrayList<>();
        ChunkEntry inFlight = new ChunkEntry(-1, -7);
        inFlight.markProcessing(); // Estaba en vuelo cuando el servidor cayó

        ChunkEntry pending = new ChunkEntry(-1, -8);

        entries.add(inFlight);
        entries.add(pending);

        BatchQueueData crashedData = new BatchQueueData("world", 500, entries);
        // Guardamos simulando que el servidor cayó con la tarea en ejecución RUNNING
        crashedData.setState(BatchTaskState.RUNNING);
        persistence.save(crashedData);

        // Simulamos un nuevo inicio del servidor cargando el archivo
        Optional<BatchQueueData> recoveredOpt = persistence.load();
        assertTrue(recoveredOpt.isPresent());

        BatchQueueData recovered = recoveredOpt.get();
        assertEquals(BatchTaskState.PAUSED, recovered.getState(), "El estado recuperado DEBE ser forzosamente PAUSED");
        assertEquals(ProcessingStatus.PENDING, recovered.getChunks().get(0).getStatus(), "El chunk en vuelo DEBE haberse reseteado a PENDING");
        assertEquals(0, recovered.getProcessingCount());
        assertEquals(2, recovered.getPendingCount());

        // Comprobamos que el archivo en disco también se haya actualizado con el estado recuperado
        String diskContent = Files.readString(queueFile);
        assertTrue(diskContent.contains("\"PAUSED\""), "El archivo en disco debe reflejar el estado corregido PAUSED");
        assertFalse(diskContent.contains("\"PROCESSING\""), "No deben quedar estados PROCESSING en disco tras la recuperación");
    }

    @Test
    @DisplayName("Eliminación segura del archivo queue.json")
    void testDeleteQueueFile(@TempDir Path tempDir) throws IOException {
        Path queueFile = tempDir.resolve("queue.json");
        BatchQueuePersistence persistence = new BatchQueuePersistence(queueFile, null);

        BatchQueueData data = new BatchQueueData("world", 500, List.of(new ChunkEntry(0, 6)));
        persistence.save(data);
        assertTrue(Files.exists(queueFile));

        boolean deleted = persistence.deleteQueueFile();
        assertTrue(deleted);
        assertFalse(Files.exists(queueFile));
    }
}
