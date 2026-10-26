package com.infinityroads.generator;

import com.infinityroads.InfinityRoadsPlugin;
import com.infinityroads.geometry.RoadGeometry;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Escuchador de eventos de ciclo de vida de mundos y chunks.
 *
 * GARANTÍAS ARQUITECTÓNICAS ESTRICTAS:
 * 1. Chunks preexistentes (isNewChunk() == false) NUNCA son modificados ni procesados automáticamente.
 * 2. Cero doble generación: si el BlockPopulator ya pobló el chunk en la fase de LimitedRegion,
 *    onChunkLoad se limita a sellar el PersistentDataContainer (PDC) sin volver a mutar bloques.
 * 3. Idempotencia absoluta: si el PDC ya tiene road_state == COMPLETED, se ignora de inmediato,
 *    protegiendo cualquier construcción o modificación posterior realizada por jugadores o administradores.
 * 4. Registro seguro e idempotente del BlockPopulator en el ciclo de vida del servidor Paper.
 */
public class ChunkGenerationListener implements Listener {

    private final InfinityRoadsPlugin plugin;
    private final String targetWorldName;
    private final RoadGenerator generator;
    private final RoadBlockPopulator populator;

    public ChunkGenerationListener(
            @NotNull InfinityRoadsPlugin plugin,
            @NotNull RoadGenerator generator,
            @NotNull RoadBlockPopulator populator
    ) {
        this.plugin = Objects.requireNonNull(plugin, "El plugin no puede ser nulo");
        this.targetWorldName = plugin.getTargetWorldName();
        this.generator = Objects.requireNonNull(generator, "El generador no puede ser nulo");
        this.populator = Objects.requireNonNull(populator, "El poblador no puede ser nulo");
    }

    public ChunkGenerationListener(
            @NotNull String targetWorldName,
            @NotNull RoadGenerator generator,
            @NotNull RoadBlockPopulator populator
    ) {
        this.plugin = null;
        this.targetWorldName = Objects.requireNonNull(targetWorldName, "El nombre del mundo no puede ser nulo");
        this.generator = Objects.requireNonNull(generator, "El generador no puede ser nulo");
        this.populator = Objects.requireNonNull(populator, "El poblador no puede ser nulo");
    }

    /**
     * Intercepta la carga de chunks vírgenes recién generados.
     * Sella el PDC si el chunk ya fue poblado por LimitedRegion, o genera in-chunk si el populator fue omitido.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent event) {
        // REQUISITO INNEGOCIABLE: Si no es un chunk nuevo, ignorar inmediatamente.
        // Los chunks preexistentes jamás se procesan automáticamente.
        if (!event.isNewChunk()) {
            return;
        }

        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();

        if (!world.getName().equalsIgnoreCase(targetWorldName)) {
            return;
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        if (!RoadGeometry.chunkIntersectsRoad(chunkX, chunkZ)) {
            return;
        }

        try {
            // Idempotencia absoluta: si ya está sellado en PDC, proteger construcciones y no tocar
            if (generator.isChunkCompleted(chunk)) {
                return;
            }

            // Caso 1: El chunk fue generado en esta sesión por BlockPopulator (LimitedRegion)
            // Evita doble generación: sellar PDC y no volver a mutar bloques
            if (generator.consumeInFlightCompleted(chunkX, chunkZ)) {
                generator.markChunkCompleted(chunk);
                return;
            }

            // Caso 2: El pavimento ya está físicamente presente en el chunk (sesión previa / populator externo)
            if (generator.isPavementFullyPresent(chunk)) {
                generator.markChunkCompleted(chunk);
                return;
            }

            // Caso 3: Chunk nuevo sin poblar por BlockPopulator -> generar y sellar
            generator.generateInChunk(chunk);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, String.format(
                        "[Generator] Error crítico en ChunkGenerationListener.onChunkLoad [world: %s, chunk: [%d, %d]]: %s",
                        world.getName(), chunkX, chunkZ, t.getMessage()
                ), t);
            }
        }
    }

    /**
     * Registra el BlockPopulator en cuanto el mundo objetivo se inicializa.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldInit(WorldInitEvent event) {
        registerPopulatorIfNeeded(event.getWorld());
    }

    /**
     * Registra el BlockPopulator cuando el mundo objetivo termina de cargarse.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(WorldLoadEvent event) {
        registerPopulatorIfNeeded(event.getWorld());
    }

    /**
     * Añade de forma segura e idempotente el poblador a la lista de populators del mundo objetivo.
     */
    public void registerPopulatorIfNeeded(World world) {
        if (!world.getName().equalsIgnoreCase(targetWorldName)) {
            return;
        }

        if (!world.getPopulators().contains(populator)) {
            world.getPopulators().add(populator);
            if (plugin != null) {
                plugin.getLogger().info(String.format("[Generator] RoadBlockPopulator registrado exitosamente en el mundo: %s", world.getName()));
            }
        }
    }
}
