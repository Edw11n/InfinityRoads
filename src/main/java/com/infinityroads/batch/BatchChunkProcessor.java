package com.infinityroads.batch;

import com.infinityroads.generator.RoadGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Motor central de procesamiento asíncrono, controlado y seguro de chunks existentes.
 *
 * Características principales:
 * - Carga asíncrona mediante world.getChunkAtAsync(cx, cz).
 * - Ventana deslizante de 1 chunk simultáneo.
 * - Mutaciones de bloques estrictamente en el hilo principal del servidor.
 * - Monitoreo de TPS (umbral configurable, por defecto 19.0).
 * - Descarga automática de chunks no utilizados tras su procesamiento.
 * - Máximo de 3 reintentos en caso de fallos.
 * - Resistencia a caídas con recuperación automática en estado PAUSED.
 */
public class BatchChunkProcessor {

    private final Plugin plugin;
    private final RoadGenerator roadGenerator;
    private final BatchQueuePersistence persistence;
    private final BatchCandidateScanner scanner;
    private final Logger logger;

    private BatchQueueData currentQueue;
    private BukkitTask currentScheduledTask;
    private boolean processingInFlight = false;
    private long lastTpsWarning = 0L;
    private int chunksSinceLastSave = 0;
    private long lastSaveTimestamp = 0L;
    private int chunksSinceLastProgressLog = 0;

    // Parámetros de configuración
    private int slidingWindow = 1;
    private double tpsThreshold = 19.0;
    private boolean autoUnloadChunk = true;
    private long chunkDelayTicks = 1L;
    private int maxRetries = 3;

    public BatchChunkProcessor(
            Plugin plugin,
            RoadGenerator roadGenerator,
            BatchQueuePersistence persistence,
            BatchCandidateScanner scanner
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin no puede ser nulo");
        this.roadGenerator = Objects.requireNonNull(roadGenerator, "roadGenerator no puede ser nulo");
        this.persistence = Objects.requireNonNull(persistence, "persistence no puede ser nulo");
        this.scanner = Objects.requireNonNull(scanner, "scanner no puede ser nulo");
        this.logger = plugin.getLogger();
    }

    /**
     * Inicialización en onEnable: carga el archivo queue.json si existe
     * y aplica el protocolo de recuperación (estado forzado a PAUSED).
     */
    public synchronized void initializeOnStartup() {
        Optional<BatchQueueData> loaded = persistence.load();
        if (loaded.isPresent()) {
            this.currentQueue = loaded.get();
            logger.info(String.format(
                    "[Batch] Cola recuperada de queue.json: %d chunks (%d pendientes, %d completados, %d omitidos, %d fallidos). Estado: %s.",
                    currentQueue.getTotalCount(),
                    currentQueue.getPendingCount(),
                    currentQueue.getCompletedCount(),
                    currentQueue.getSkippedCount(),
                    currentQueue.getFailedCount(),
                    currentQueue.getState()
            ));
        } else {
            this.currentQueue = null;
        }
    }

    /**
     * Cierre seguro en onDisable: si la tarea está en ejecución, la pausa y guarda el estado.
     */
    public synchronized void shutdown() {
        cancelScheduledTask();
        if (currentQueue != null) {
            if (currentQueue.getState() == BatchTaskState.RUNNING) {
                currentQueue.setState(BatchTaskState.PAUSED);
            }
            currentQueue.resetInFlightChunks();
            try {
                persistence.save(currentQueue);
                logger.info("[Batch] Cola guardada de forma segura durante el apagado.");
            } catch (IOException e) {
                logger.log(Level.SEVERE, "[Batch] Error al guardar queue.json en shutdown: " + e.getMessage(), e);
            }
        }
    }

    public synchronized BatchQueueData getCurrentQueue() {
        return currentQueue;
    }

    public synchronized BatchTaskState getState() {
        return currentQueue != null ? currentQueue.getState() : BatchTaskState.IDLE;
    }

    public void configure(double tpsThreshold, boolean autoUnloadChunk, long chunkDelayTicks, int maxRetries) {
        this.tpsThreshold = tpsThreshold;
        this.autoUnloadChunk = autoUnloadChunk;
        this.chunkDelayTicks = chunkDelayTicks;
        this.maxRetries = maxRetries;
    }

    /**
     * Inicia un nuevo procesamiento a partir de un token de confirmación emitido por /ir chunks preview.
     */
    public synchronized boolean startFromToken(String token, CommandSender sender) {
        if (currentQueue != null && currentQueue.getState() == BatchTaskState.RUNNING) {
            sender.sendMessage("§cYa existe un procesamiento por lotes en ejecución. Usa '/ir chunks pause' o '/ir chunks status'.");
            return false;
        }

        Optional<BatchCandidateScanner.PreviewResult> optPreview = scanner.consumeToken(token);
        if (optPreview.isEmpty()) {
            sender.sendMessage("§cToken de confirmación inválido o expirado. Genera uno nuevo con '/ir chunks preview [radio]'.");
            return false;
        }

        BatchCandidateScanner.PreviewResult preview = optPreview.get();
        if (preview.totalCandidates() == 0) {
            sender.sendMessage("§eNo se encontraron chunks candidatos para procesar en el radio especificado.");
            return false;
        }

        List<ChunkEntry> entries = new ArrayList<>(preview.candidates().size());
        for (ChunkCoord coord : preview.candidates()) {
            entries.add(new ChunkEntry(coord.x(), coord.z()));
        }

        this.currentQueue = new BatchQueueData(preview.worldName(), preview.radius(), entries);
        this.currentQueue.setState(BatchTaskState.RUNNING);

        try {
            persistence.save(currentQueue);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[Batch] Error al persistir queue.json al iniciar tarea: " + e.getMessage(), e);
            sender.sendMessage("§cError de E/S al guardar la cola en disco: " + e.getMessage());
            this.currentQueue.setState(BatchTaskState.PAUSED);
            return false;
        }

        logger.info(String.format(
                "[Batch] Procesamiento por lotes INICIADO por %s para %d chunks en '%s' (Radio: %d bloques).",
                sender.getName(), preview.totalCandidates(), preview.worldName(), preview.radius()
        ));

        sender.sendMessage(String.format(
                "§aProcesamiento por lotes iniciado para %d chunks en '%s' (Radio: %d bloques).",
                preview.totalCandidates(), preview.worldName(), preview.radius()
        ));

        scheduleNextChunk();
        return true;
    }

    /**
     * Pausa manualmente el procesamiento por lotes.
     */
    public synchronized boolean pause(CommandSender sender) {
        if (currentQueue == null || currentQueue.getState() != BatchTaskState.RUNNING) {
            sender.sendMessage("§eNo hay ningún procesamiento por lotes en ejecución activa para pausar.");
            return false;
        }

        currentQueue.setState(BatchTaskState.PAUSED);
        cancelScheduledTask();
        saveSafely();

        logger.info(String.format(
                "[Batch] Procesamiento por lotes PAUSADO por %s. Progreso actual: %d/%d.",
                sender.getName(), currentQueue.getProcessedCount(), currentQueue.getTotalCount()
        ));

        sender.sendMessage(String.format(
                "§6Procesamiento por lotes PAUSADO. Chunks completados: %d/%d (%d pendientes).",
                currentQueue.getProcessedCount(), currentQueue.getTotalCount(), currentQueue.getPendingCount()
        ));
        return true;
    }

    /**
     * Reanuda un procesamiento por lotes previamente pausado.
     */
    public synchronized boolean resume(CommandSender sender) {
        if (currentQueue == null || currentQueue.getState() != BatchTaskState.PAUSED) {
            sender.sendMessage("§eNo hay ningún procesamiento por lotes pausado para reanudar.");
            return false;
        }

        if (currentQueue.isAllDone()) {
            currentQueue.setState(BatchTaskState.COMPLETED);
            saveSafely();
            sender.sendMessage("§aTodos los chunks ya fueron completados u omitidos.");
            return false;
        }

        currentQueue.setState(BatchTaskState.RUNNING);
        saveSafely();

        logger.info(String.format(
                "[Batch] Procesamiento por lotes REANUDADO por %s. Chunks pendientes: %d.",
                sender.getName(), currentQueue.getPendingCount()
        ));

        sender.sendMessage(String.format(
                "§aProcesamiento por lotes REANUDADO. Pendientes: %d chunks.",
                currentQueue.getPendingCount()
        ));

        scheduleNextChunk();
        return true;
    }

    /**
     * Cancela definitivamente el procesamiento por lotes actual.
     */
    public synchronized boolean cancel(CommandSender sender) {
        if (currentQueue == null || (currentQueue.getState() != BatchTaskState.RUNNING && currentQueue.getState() != BatchTaskState.PAUSED)) {
            sender.sendMessage("§eNo hay ningún procesamiento por lotes activo o pausado para cancelar.");
            return false;
        }

        currentQueue.setState(BatchTaskState.CANCELLED);
        cancelScheduledTask();
        currentQueue.resetInFlightChunks();
        saveSafely();

        logger.info(String.format(
                "[Batch] Procesamiento por lotes CANCELADO por %s. Chunks procesados: %d/%d.",
                sender.getName(), currentQueue.getProcessedCount(), currentQueue.getTotalCount()
        ));

        sender.sendMessage("§cProcesamiento por lotes CANCELADO.");
        return true;
    }

    /**
     * Restablece los chunks en estado FAILED a PENDING para reintentar su procesamiento.
     */
    public synchronized boolean retry(CommandSender sender) {
        if (currentQueue == null) {
            sender.sendMessage("§eNo hay ninguna cola registrada.");
            return false;
        }

        int failedCount = currentQueue.getFailedCount();
        if (failedCount == 0) {
            sender.sendMessage("§aNo hay chunks en estado fallido (FAILED) para reintentar.");
            return false;
        }

        int reset = currentQueue.resetFailedChunks();
        saveSafely();

        logger.info(String.format(
                "[Batch] Reintentando %d chunks en estado fallido (solicitado por %s).",
                reset, sender.getName()
        ));

        sender.sendMessage(String.format(
                "§aSe han restablecido %d chunks fallidos a PENDING. Usa '/ir chunks resume' para reiniciar el procesamiento.",
                reset
        ));
        return true;
    }

    /**
     * Programa el siguiente ciclo de carga y procesamiento de chunk.
     */
    private synchronized void scheduleNextChunk() {
        if (currentQueue == null || currentQueue.getState() != BatchTaskState.RUNNING) {
            return;
        }

        if (processingInFlight) {
            return;
        }

        // 1. Control de TPS en tiempo real
        double currentTps = getCurrentServerTps();
        if (currentTps < tpsThreshold) {
            long now = System.currentTimeMillis();
            if (now - lastTpsWarning > 10000L) { // advertencia cada 10s máximo
                logger.warning(String.format(
                        "[Batch] Rendimiento degradado: TPS bajo detectado (%.2f < %.2f). Estrangulando procesamiento de chunks en '%s'...",
                        currentTps, tpsThreshold, currentQueue.getWorldName()
                ));
                lastTpsWarning = now;
            }
            // Reintentar verificación en 20 ticks (1 segundo)
            currentScheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::scheduleNextChunk, 20L);
            return;
        }

        // 2. Obtener siguiente chunk pendiente
        Optional<ChunkEntry> optEntry = currentQueue.getNextPendingChunk();
        if (optEntry.isEmpty()) {
            if (currentQueue.isAllDone()) {
                currentQueue.setState(BatchTaskState.COMPLETED);
                saveSafely();
                logger.info(String.format(
                        "[Batch] ¡Procesamiento por lotes FINALIZADO con éxito en '%s'! Total: %d, Completados: %d, Omitidos: %d, Fallidos: %d.",
                        currentQueue.getWorldName(), currentQueue.getTotalCount(), currentQueue.getCompletedCount(),
                        currentQueue.getSkippedCount(), currentQueue.getFailedCount()
                ));
            }
            return;
        }

        ChunkEntry entry = optEntry.get();
        World world = Bukkit.getWorld(currentQueue.getWorldName());
        if (world == null) {
            entry.recordFailure("Mundo '" + currentQueue.getWorldName() + "' no encontrado", maxRetries);
            saveSafely();
            scheduleNextTick();
            return;
        }

        entry.markProcessing();
        processingInFlight = true;

        // Comprobar si el chunk ya estaba cargado antes de solicitarlo
        boolean wasLoadedBefore = world.isChunkLoaded(entry.getX(), entry.getZ());

        // 3. Carga asíncrona mediante Paper API
        world.getChunkAtAsync(entry.getX(), entry.getZ()).thenAccept(chunk -> {
            // 4. Las mutaciones de bloques se ejecutan obligatoriamente en el hilo principal
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    processChunkOnMainThread(chunk, entry, wasLoadedBefore);
                } catch (Throwable t) {
                    handleChunkFailure(entry, t.getMessage(), t);
                } finally {
                    processingInFlight = false;
                    savePeriodicOrForced(false);
                    scheduleNextTick();
                }
            });
        }).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                handleChunkFailure(entry, throwable != null ? throwable.getMessage() : "Error asíncrono desconocido", throwable);
                processingInFlight = false;
                savePeriodicOrForced(false);
                scheduleNextTick();
            });
            return null;
        });
    }

    /**
     * Aplica la generación procedural de forma segura en el hilo principal del servidor.
     */
    private void processChunkOnMainThread(Chunk chunk, ChunkEntry entry, boolean wasLoadedBefore) {
        try {
            // Verificar idempotencia: si ya estaba completado, marcar como omitido sin modificar nada
            if (roadGenerator.isChunkCompleted(chunk)) {
                entry.markSkipped();
                logger.fine(String.format("[Batch] Chunk [%d, %d] omitido por idempotencia (ya completado en PDC).", entry.getX(), entry.getZ()));
            } else {
                // Modifica exclusivamente Y >= 62 (pavimento a 62, aire 63..max-1)
                boolean generated = roadGenerator.generateInChunk(chunk);
                if (generated) {
                    entry.markCompleted();
                    logger.fine(String.format("[Batch] Chunk [%d, %d] generado y sellado como COMPLETED.", entry.getX(), entry.getZ()));
                } else {
                    entry.markSkipped();
                    logger.fine(String.format("[Batch] Chunk [%d, %d] omitido (sin intersección con carretera).", entry.getX(), entry.getZ()));
                }
            }

            // Descarga automática segura si el chunk no estaba cargado previamente
            if (autoUnloadChunk && !wasLoadedBefore) {
                // En Paper, chunk.unload(true) descarga y guarda el chunk liberando memoria
                chunk.unload(true);
            }
        } catch (Throwable t) {
            handleChunkFailure(entry, t.getMessage(), t);
        }
    }

    private void handleChunkFailure(ChunkEntry entry, String errorMessage, Throwable cause) {
        entry.recordFailure(errorMessage, maxRetries);
        int attempts = entry.getAttempts();
        String worldName = currentQueue != null ? currentQueue.getWorldName() : "unknown";
        if (attempts <= maxRetries) {
            logger.log(Level.WARNING, String.format(
                    "[Batch] Error recuperable al procesar chunk [world: %s, chunk: [%d, %d]] (Intento %d/%d): %s",
                    worldName, entry.getX(), entry.getZ(), attempts, maxRetries, errorMessage
            ), cause);
        } else {
            logger.log(Level.SEVERE, String.format(
                    "[Batch] Fallo definitivo en chunk [world: %s, chunk: [%d, %d]] tras agotar %d reintentos: %s",
                    worldName, entry.getX(), entry.getZ(), maxRetries, errorMessage
            ), cause);
        }
    }

    private void scheduleNextTick() {
        if (currentQueue != null && currentQueue.getState() == BatchTaskState.RUNNING) {
            chunksSinceLastProgressLog++;
            if (chunksSinceLastProgressLog >= 100) {
                int done = currentQueue.getProcessedCount();
                int total = currentQueue.getTotalCount();
                double pct = (double) done / total * 100.0;
                logger.info(String.format(
                        "[Batch] Progreso del lote: %d/%d (%.1f%%) | Completados: %d | Omitidos: %d | Fallidos: %d | TPS: %.2f",
                        done, total, pct, currentQueue.getCompletedCount(), currentQueue.getSkippedCount(), currentQueue.getFailedCount(), getCurrentServerTps()
                ));
                chunksSinceLastProgressLog = 0;
            }
            currentScheduledTask = Bukkit.getScheduler().runTaskLater(plugin, this::scheduleNextChunk, Math.max(1L, chunkDelayTicks));
        }
    }

    private void cancelScheduledTask() {
        if (currentScheduledTask != null) {
            currentScheduledTask.cancel();
            currentScheduledTask = null;
        }
    }

    private synchronized void saveSafely() {
        if (currentQueue != null) {
            try {
                persistence.save(currentQueue);
            } catch (IOException e) {
                logger.log(Level.SEVERE, String.format(
                        "[Batch] Fallo al persistir queue.json en disco [estado: %s, pendientes: %d]: %s",
                        currentQueue.getState(), currentQueue.getPendingCount(), e.getMessage()
                ), e);
            }
        }
    }

    private synchronized void savePeriodicOrForced(boolean force) {
        long now = System.currentTimeMillis();
        chunksSinceLastSave++;
        if (force || chunksSinceLastSave >= 20 || (now - lastSaveTimestamp) >= 2000L) {
            saveSafely();
            chunksSinceLastSave = 0;
            lastSaveTimestamp = now;
        }
    }

    /**
     * Obtiene el TPS actual del servidor (promedio de 1 minuto) vía Bukkit.getTPS().
     */
    public double getCurrentServerTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0) {
                return Math.min(20.0, Math.max(0.0, tps[0]));
            }
        } catch (Throwable t) {
            logger.fine("[Batch] Error al leer Bukkit.getTPS(), asumiendo 20.0: " + t.getMessage());
        }
        return 20.0;
    }
}
