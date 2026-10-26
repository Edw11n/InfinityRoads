package com.infinityroads.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas del Sistema de Observabilidad y RoadLogger (Fase 5)")
class RoadLoggerTest {

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
    @DisplayName("DEBUG desactivado por defecto no envía mensajes de debug a nivel INFO")
    void testDebugDisabledByDefault() {
        Logger rawLogger = Logger.getLogger("TestLogger-DebugDisabled");
        rawLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        rawLogger.setLevel(Level.ALL);
        rawLogger.addHandler(handler);

        RoadLogger roadLogger = new RoadLogger(rawLogger, false);
        assertFalse(roadLogger.isDebugEnabled());

        roadLogger.debug("Mensaje de prueba en cota (%d, %d)", 100, 200);

        // Cuando debug está deshabilitado, se envía a nivel FINE, no INFO
        assertFalse(handler.records.isEmpty());
        LogRecord record = handler.records.getFirst();
        assertEquals(Level.FINE, record.getLevel());
        assertEquals("Mensaje de prueba en cota (100, 200)", record.getMessage());
    }

    @Test
    @DisplayName("DEBUG activado emite mensajes con prefijo [DEBUG] a nivel INFO")
    void testDebugEnabledLogsToInfo() {
        Logger rawLogger = Logger.getLogger("TestLogger-DebugEnabled");
        rawLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        rawLogger.setLevel(Level.ALL);
        rawLogger.addHandler(handler);

        RoadLogger roadLogger = new RoadLogger(rawLogger, true);
        assertTrue(roadLogger.isDebugEnabled());

        roadLogger.debug("Evaluando chunk [%d, %d]", -70, 0);

        assertFalse(handler.records.isEmpty());
        LogRecord record = handler.records.getFirst();
        assertEquals(Level.INFO, record.getLevel());
        assertEquals("[DEBUG] Evaluando chunk [-70, 0]", record.getMessage());
    }

    @Test
    @DisplayName("INFO, WARNING y SEVERE preservan contexto, formato y excepciones")
    void testStandardLogLevelsWithContext() {
        Logger rawLogger = Logger.getLogger("TestLogger-StandardLevels");
        rawLogger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(Level.ALL);
        rawLogger.setLevel(Level.ALL);
        rawLogger.addHandler(handler);

        RoadLogger roadLogger = new RoadLogger(rawLogger);

        // INFO
        roadLogger.info("[Lifecycle] Inicio exitoso en '%s'", "world");
        // WARNING con throwable
        RuntimeException warnEx = new RuntimeException("E/S temporal");
        roadLogger.warning("[Batch] Error recuperable en chunk [%d, %d]: %s", warnEx, -70, 0, warnEx.getMessage());
        // SEVERE con throwable
        IllegalStateException severeEx = new IllegalStateException("Corrupción de datos");
        roadLogger.severe("[Persistence] Fallo crítico al persistir: %s", severeEx, severeEx.getMessage());

        assertEquals(3, handler.records.size());

        LogRecord infoRecord = handler.records.get(0);
        assertEquals(Level.INFO, infoRecord.getLevel());
        assertEquals("[Lifecycle] Inicio exitoso en 'world'", infoRecord.getMessage());

        LogRecord warnRecord = handler.records.get(1);
        assertEquals(Level.WARNING, warnRecord.getLevel());
        assertTrue(warnRecord.getMessage().contains("chunk [-70, 0]"));
        assertEquals(warnEx, warnRecord.getThrown());

        LogRecord severeRecord = handler.records.get(2);
        assertEquals(Level.SEVERE, severeRecord.getLevel());
        assertTrue(severeRecord.getMessage().contains("Fallo crítico"));
        assertEquals(severeEx, severeRecord.getThrown());
    }
}
