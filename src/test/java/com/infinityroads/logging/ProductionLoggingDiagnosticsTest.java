package com.infinityroads.logging;

import com.infinityroads.batch.BatchQueueData;
import com.infinityroads.batch.BatchQueuePersistence;
import com.infinityroads.batch.ChunkEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas de Diagnóstico y Contexto de Logs de Producción (Fase 5)")
class ProductionLoggingDiagnosticsTest {

    private static class TestLogHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }

    @Test
    @DisplayName("Fallo de persistencia de la cola registra SEVERE con causa completa y lanza IOException")
    void testPersistenceFailureLogging(@TempDir Path tempDir) {
        Logger testLogger = Logger.getLogger("TestLogger-PersistenceFailure");
        testLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        testLogger.setLevel(Level.ALL);
        testLogger.addHandler(handler);

        // Archivo apuntando a una ruta imposible / de dispositivo inválido
        Path invalidPath = Path.of("Z:\\ruta_imposible_inexistente_12345\\sub\\queue.json");
        BatchQueuePersistence persistence = new BatchQueuePersistence(invalidPath, testLogger);
        BatchQueueData data = new BatchQueueData("world", 500, List.of(new ChunkEntry(0, 0)));

        assertThrows(IOException.class, () -> persistence.save(data));
    }

    @Test
    @DisplayName("Archivo queue.json corrupto o ilegible registra SEVERE sin crashear el servidor")
    void testCorruptedQueueFileLogging(@TempDir Path tempDir) throws IOException {
        Logger testLogger = Logger.getLogger("TestLogger-CorruptedQueue");
        testLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        testLogger.setLevel(Level.ALL);
        testLogger.addHandler(handler);

        Path queuePath = tempDir.resolve("queue.json");
        Files.writeString(queuePath, "{ corrupt json data !!! non-parsable");

        // Pasamos el directorio padre 'tempDir.toFile()'
        BatchQueuePersistence persistence = new BatchQueuePersistence(tempDir.toFile(), testLogger);
        var loaded = persistence.load();

        assertTrue(loaded.isEmpty());
        assertTrue(handler.records.stream().anyMatch(r -> r.getLevel() == Level.SEVERE && r.getMessage().contains("[Persistence]")));
    }

    @Test
    @DisplayName("Reintento de chunks registra WARNING en intentos <= maxRetries y SEVERE al agotar reintentos")
    void testChunkRetryAndExhaustionLogging() {
        Logger testLogger = Logger.getLogger("TestLogger-ChunkRetry");
        testLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        testLogger.setLevel(Level.ALL);
        testLogger.addHandler(handler);

        RoadLogger roadLogger = new RoadLogger(testLogger);
        ChunkEntry entry = new ChunkEntry(-70, 0);
        int maxRetries = 2;
        String worldName = "world";

        // Simular intento 1 (recuperable -> WARNING)
        entry.recordFailure("Fallo de chunk I/O temporal", maxRetries);
        roadLogger.warning(
                String.format("[Batch] Error recuperable al procesar chunk [world: %s, chunk: [%d, %d]] (Intento %d/%d): %s",
                        worldName, entry.getX(), entry.getZ(), entry.getAttempts(), maxRetries, "Fallo de chunk I/O temporal")
        );

        // Simular intento 2 (recuperable -> WARNING)
        entry.recordFailure("Fallo de chunk I/O persistente", maxRetries);
        roadLogger.warning(
                String.format("[Batch] Error recuperable al procesar chunk [world: %s, chunk: [%d, %d]] (Intento %d/%d): %s",
                        worldName, entry.getX(), entry.getZ(), entry.getAttempts(), maxRetries, "Fallo de chunk I/O persistente")
        );

        // Simular agotamiento (intento 3 -> SEVERE)
        entry.recordFailure("Fallo definitivo de chunk", maxRetries);
        roadLogger.severe(
                String.format("[Batch] Fallo definitivo en chunk [world: %s, chunk: [%d, %d]] tras agotar %d reintentos: %s",
                        worldName, entry.getX(), entry.getZ(), maxRetries, "Fallo definitivo de chunk")
        );

        assertEquals(3, handler.records.size());
        assertEquals(Level.WARNING, handler.records.get(0).getLevel());
        assertTrue(handler.records.get(0).getMessage().contains("Intento 1/2"));

        assertEquals(Level.WARNING, handler.records.get(1).getLevel());
        assertTrue(handler.records.get(1).getMessage().contains("Intento 2/2"));

        assertEquals(Level.SEVERE, handler.records.get(2).getLevel());
        assertTrue(handler.records.get(2).getMessage().contains("Fallo definitivo"));
        assertTrue(handler.records.get(2).getMessage().contains("agotar 2 reintentos"));
    }
}
