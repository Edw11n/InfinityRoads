package com.infinityroads;

import com.infinityroads.batch.BatchCandidateScanner;
import com.infinityroads.batch.BatchChunkProcessor;
import com.infinityroads.batch.BatchQueuePersistence;
import com.infinityroads.command.InfinityRoadsCommand;
import com.infinityroads.generator.ChunkGenerationListener;
import com.infinityroads.generator.RoadBlockPopulator;
import com.infinityroads.generator.RoadGenerator;
import com.infinityroads.logging.RoadLogger;
import com.infinityroads.protection.FluidContainmentListener;
import com.infinityroads.protection.MaintenanceManager;
import com.infinityroads.protection.RoadProtectionListener;
import com.infinityroads.protection.RoadProtectionService;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal de entrada del plugin InfinityRoads.
 *
 * Integra:
 * - Fase 1: Geometría pura y límites espaciales (RoadGeometry).
 * - Fase 2: Generación procedural y despeje aéreo en chunks nuevos (RoadGenerator, RoadBlockPopulator).
 * - Fase 3: Protección absoluta de bloques, bypass administrativo (/ir admin on/off)
 *   y contención reactiva de fluidos (RoadProtectionService, MaintenanceManager, RoadProtectionListener, FluidContainmentListener).
 * - Fase 4: Procesamiento controlado, seguro, reanudable y eficiente de chunks preexistentes
 *   (BatchChunkProcessor, BatchCandidateScanner, BatchQueuePersistence).
 * - Fase 5: Observabilidad y logging de producción (RoadLogger).
 */
public final class InfinityRoadsPlugin extends JavaPlugin {

    private RoadLogger roadLogger;
    private String targetWorldName = "world";
    private RoadGenerator roadGenerator;
    private RoadBlockPopulator roadBlockPopulator;
    private ChunkGenerationListener chunkGenerationListener;

    private MaintenanceManager maintenanceManager;
    private RoadProtectionService protectionService;
    private RoadProtectionListener roadProtectionListener;
    private FluidContainmentListener fluidContainmentListener;

    private BatchQueuePersistence batchPersistence;
    private BatchCandidateScanner batchScanner;
    private BatchChunkProcessor batchProcessor;

    @Override
    public void onLoad() {
        this.roadLogger = new RoadLogger(getLogger(), false);
    }

    @Override
    public void onEnable() {
        if (this.roadLogger == null) {
            this.roadLogger = new RoadLogger(getLogger(), false);
        }
        roadLogger.info("[Lifecycle] Inicializando InfinityRoads v%s...", getPluginMeta().getVersion());

        saveDefaultConfig();
        loadConfiguration();

        // 1. Inicialización de Fase 2 (Generador y Poblador)
        this.roadGenerator = new RoadGenerator(this);
        this.roadBlockPopulator = new RoadBlockPopulator(this, roadGenerator);
        this.chunkGenerationListener = new ChunkGenerationListener(this, roadGenerator, roadBlockPopulator);

        // 2. Inicialización de Fase 3 (Mantenimiento, Protección y Fluidos)
        this.maintenanceManager = new MaintenanceManager();
        this.protectionService = new RoadProtectionService(targetWorldName, maintenanceManager);
        this.roadProtectionListener = new RoadProtectionListener(protectionService, roadLogger);
        this.fluidContainmentListener = new FluidContainmentListener(protectionService, roadLogger);

        // 3. Inicialización de Fase 4 (Procesamiento por lotes de chunks preexistentes)
        this.batchPersistence = new BatchQueuePersistence(getDataFolder(), getLogger());
        long tokenValidity = getConfig().getLong("batch.token-validity-seconds", 120L);
        this.batchScanner = new BatchCandidateScanner(roadGenerator, tokenValidity);
        this.batchProcessor = new BatchChunkProcessor(this, roadGenerator, batchPersistence, batchScanner);

        applyBatchConfiguration();
        this.batchProcessor.initializeOnStartup();

        // 4. Registro de escuchadores de eventos
        getServer().getPluginManager().registerEvents(chunkGenerationListener, this);
        getServer().getPluginManager().registerEvents(roadProtectionListener, this);
        getServer().getPluginManager().registerEvents(fluidContainmentListener, this);

        // 5. Registro del comando administrativo /ir
        InfinityRoadsCommand cmd = new InfinityRoadsCommand(this, maintenanceManager, batchScanner, batchProcessor);
        PluginCommand irCommand = getCommand("ir");
        if (irCommand != null) {
            irCommand.setExecutor(cmd);
            irCommand.setTabCompleter(cmd);
        } else {
            roadLogger.warning("[Lifecycle] No se pudo registrar el comando 'ir'. Verifica plugin.yml.");
        }

        // 6. Registrar populator en mundos ya cargados
        for (World world : getServer().getWorlds()) {
            chunkGenerationListener.registerPopulatorIfNeeded(world);
        }

        roadLogger.info("[Lifecycle] InfinityRoads habilitado exitosamente: 4 carreteras cardinales activas en '%s'.", targetWorldName);
    }

    @Override
    public void onDisable() {
        if (roadLogger != null) {
            roadLogger.info("[Lifecycle] Deshabilitando InfinityRoads...");
        }

        if (batchProcessor != null) {
            batchProcessor.shutdown();
        }

        if (maintenanceManager != null) {
            maintenanceManager.clearAllSessions();
        }

        if (roadBlockPopulator != null) {
            for (World world : getServer().getWorlds()) {
                world.getPopulators().remove(roadBlockPopulator);
            }
        }

        if (roadLogger != null) {
            roadLogger.info("[Lifecycle] InfinityRoads deshabilitado correctamente.");
        }
    }

    public void loadConfiguration() {
        reloadConfig();

        // Observabilidad: activación o desactivación de logs de diagnóstico
        boolean debug = getConfig().getBoolean("logging.debug", false);
        if (this.roadLogger == null) {
            this.roadLogger = new RoadLogger(getLogger(), debug);
        } else {
            this.roadLogger.setDebugEnabled(debug);
        }

        // Validación de mundo objetivo
        String configuredWorld = getConfig().getString("world-name");
        if (configuredWorld == null || configuredWorld.isBlank()) {
            roadLogger.warning("[Config] 'world-name' no está definido o está en blanco. Se utilizará el valor por defecto: 'world'.");
            this.targetWorldName = "world";
        } else {
            this.targetWorldName = configuredWorld.trim();
        }

        if (protectionService != null) {
            this.protectionService.setTargetWorldName(targetWorldName);
        }
        if (batchProcessor != null) {
            applyBatchConfiguration();
        }

        roadLogger.info("[Lifecycle] Configuración cargada. Mundo: '%s', Modo DEBUG: %b.", targetWorldName, debug);
    }

    private void applyBatchConfiguration() {
        double tpsThreshold = getConfig().getDouble("batch.tps-threshold", 19.0);
        if (tpsThreshold <= 0.0 || tpsThreshold > 20.0) {
            roadLogger.warning("[Config] Valor inválido para 'batch.tps-threshold' (%.2f). Debe estar en (0, 20]. Restableciendo a 19.0.", tpsThreshold);
            tpsThreshold = 19.0;
        }

        boolean autoUnload = getConfig().getBoolean("batch.auto-unload-chunk", true);

        long delayTicks = getConfig().getLong("batch.chunk-delay-ticks", 1L);
        if (delayTicks < 0) {
            roadLogger.warning("[Config] Valor inválido para 'batch.chunk-delay-ticks' (%d). Restableciendo a 1 tick.", delayTicks);
            delayTicks = 1L;
        }

        int maxRetries = getConfig().getInt("batch.max-retries", 3);
        if (maxRetries < 0) {
            roadLogger.warning("[Config] Valor inválido para 'batch.max-retries' (%d). Restableciendo a 3 reintentos.", maxRetries);
            maxRetries = 3;
        }

        this.batchProcessor.configure(tpsThreshold, autoUnload, delayTicks, maxRetries);
    }

    public RoadLogger getRoadLogger() {
        return roadLogger;
    }

    public String getTargetWorldName() {
        return targetWorldName;
    }

    public RoadGenerator getRoadGenerator() {
        return roadGenerator;
    }

    public RoadBlockPopulator getRoadBlockPopulator() {
        return roadBlockPopulator;
    }

    public ChunkGenerationListener getChunkGenerationListener() {
        return chunkGenerationListener;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public RoadProtectionService getProtectionService() {
        return protectionService;
    }

    public RoadProtectionListener getRoadProtectionListener() {
        return roadProtectionListener;
    }

    public FluidContainmentListener getFluidContainmentListener() {
        return fluidContainmentListener;
    }

    public BatchCandidateScanner getBatchScanner() {
        return batchScanner;
    }

    public BatchChunkProcessor getBatchProcessor() {
        return batchProcessor;
    }
}
