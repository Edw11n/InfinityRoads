package com.infinityroads.batch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor de persistencia atómica para la cola de procesamiento por lotes de chunks.
 * Almacena el estado en queue.json usando escritura atómica (.tmp -> rename) y asegura
 * la recuperación tras caídas restableciendo chunks PROCESSING a PENDING y estado RUNNING a PAUSED.
 */
public class BatchQueuePersistence {

    private final Path queueFilePath;
    private final Path tempFilePath;
    private final Gson gson;
    private final Logger logger;

    public BatchQueuePersistence(File dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder no puede ser nulo");
        this.queueFilePath = dataFolder.toPath().resolve("queue.json");
        this.tempFilePath = dataFolder.toPath().resolve("queue.json.tmp");
        this.logger = logger;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public BatchQueuePersistence(Path queueFilePath, Logger logger) {
        this.queueFilePath = Objects.requireNonNull(queueFilePath, "queueFilePath no puede ser nulo");
        this.tempFilePath = queueFilePath.resolveSibling(queueFilePath.getFileName() + ".tmp");
        this.logger = logger;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    public Path getQueueFilePath() {
        return queueFilePath;
    }

    /**
     * Guarda atómicamente la estructura de datos en queue.json.
     * Escribe primero en un archivo temporal (.tmp) y luego realiza un movimiento atómico.
     *
     * @param data Estructura de la cola a persistir.
     * @throws IOException Si ocurre un error de E/S.
     */
    public synchronized void save(BatchQueueData data) throws IOException {
        Objects.requireNonNull(data, "data no puede ser nulo");
        data.setUpdatedAt(System.currentTimeMillis());

        Path parent = queueFilePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String json = gson.toJson(data);

        // 1. Escribir completamente en archivo temporal
        try (BufferedWriter writer = Files.newBufferedWriter(
                tempFilePath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(json);
            writer.flush();
        }

        // 2. Reemplazo del archivo destino con tolerancia completa para Windows y POSIX
        boolean replaced = false;
        try {
            Files.move(tempFilePath, queueFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            replaced = true;
        } catch (Exception e1) {
            if (logger != null) {
                logger.fine("[Persistence] ATOMIC_MOVE denegado o no soportado (" + e1.getMessage() + "), intentando REPLACE_EXISTING...");
            }
        }

        if (!replaced) {
            try {
                Files.move(tempFilePath, queueFilePath, StandardCopyOption.REPLACE_EXISTING);
                replaced = true;
            } catch (Exception e2) {
                if (logger != null) {
                    logger.warning("[Persistence] Reemplazo estándar falló (" + e2.getMessage() + "), recurriendo a copia y borrado...");
                }
                try {
                    Files.copy(tempFilePath, queueFilePath, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(tempFilePath);
                    replaced = true;
                } catch (Exception e3) {
                    if (logger != null) {
                        logger.log(Level.SEVERE, "[Persistence] Fallo crítico al persistir queue.json en disco: " + e3.getMessage(), e3);
                    }
                    if (e3 instanceof IOException ioe) {
                        throw ioe;
                    }
                    throw new IOException("Error crítico al persistir queue.json", e3);
                }
            }
        }
    }

    /**
     * Carga el estado de la cola desde queue.json si existe.
     * Aplica inmediatamente el protocolo de recuperación ante caídas:
     * - Restablece chunks PROCESSING a PENDING.
     * - Si el estado global era RUNNING, lo cambia a PAUSED.
     * - Guarda los cambios en disco si hubo correcciones para mantener consistencia.
     *
     * @return Optional con los datos cargados o Optional.empty() si el archivo no existe.
     */
    public synchronized Optional<BatchQueueData> load() {
        if (!Files.exists(queueFilePath)) {
            return Optional.empty();
        }

        BatchQueueData data;
        try (BufferedReader reader = Files.newBufferedReader(queueFilePath, StandardCharsets.UTF_8)) {
            data = gson.fromJson(reader, BatchQueueData.class);
        } catch (Exception e) {
            if (logger != null) {
                logger.log(Level.SEVERE, "[Persistence] Error al leer o deserializar queue.json: " + e.getMessage(), e);
            }
            return Optional.empty();
        }

        if (data == null) {
            return Optional.empty();
        }

        // Protocolo de recuperación de fallos / reinicios
        // Ejecutado tras cerrar el reader para liberar los bloqueos de archivo en sistemas Windows
        boolean hadRunningState = data.getState() == BatchTaskState.RUNNING;
        int resetInFlight = data.resetInFlightChunks();

        if (hadRunningState || resetInFlight > 0) {
            if (logger != null) {
                logger.warning(String.format(
                        "[Persistence] Protocolo de recuperación activado para queue.json: %d chunks en vuelo devueltos a PENDING. Estado forzado a PAUSED.",
                        resetInFlight
                ));
            }
            try {
                save(data);
            } catch (IOException e) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "[Persistence] Error al guardar queue.json durante recuperación: " + e.getMessage(), e);
                }
            }
        }

        return Optional.of(data);
    }

    /**
     * Elimina el archivo queue.json si existe.
     *
     * @return true si se eliminó con éxito o no existía; false si falló la eliminación.
     */
    public synchronized boolean deleteQueueFile() {
        try {
            Files.deleteIfExists(tempFilePath);
            return Files.deleteIfExists(queueFilePath);
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "[Persistence] No se pudo eliminar queue.json: " + e.getMessage(), e);
            }
            return false;
        }
    }
}
