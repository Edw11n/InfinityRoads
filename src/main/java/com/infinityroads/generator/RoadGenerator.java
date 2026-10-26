package com.infinityroads.generator;

import com.infinityroads.geometry.ChunkIntersection;
import com.infinityroads.geometry.RoadDirection;
import com.infinityroads.geometry.RoadGeometry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor central de generación procedural de carreteras para InfinityRoads.
 * Confinado estrictamente a cotas Y >= 62.
 *
 * REGLAS ARQUITECTÓNICAS OBLIGATORIAS:
 * 1. Modifica exclusivamente bloques con Y >= 62.
 * 2. Cero modificaciones, inspecciones destructivas o reemplazos en Y < 62.
 * 3. Pavimento flotante continuo a Y = 62: prohibido generar pilares, cimientos o soportes.
 * 4. Despeje vertical completo desde Y = 63 hasta maxHeight - 1 (sin depender de HeightMap).
 * 5. Confinamiento de mutaciones al chunk central esperado (sin escapar a chunks adyacentes).
 * 6. Idempotencia garantizada: una carretera completada no se regenera ni sobreescribe construcciones de jugadores.
 * 7. Seguridad concurrente para generación multihilo en Paper.
 */
public class RoadGenerator {

    public static final String NAMESPACE = "infinityroads";
    public static final String PDC_STATE_KEY = "road_state";
    public static final String PDC_STATE_COMPLETED = "COMPLETED";

    private final NamespacedKey stateKey;
    private final java.util.logging.Logger logger;

    /**
     * Registro concurrente en memoria de chunks generados con éxito durante la fase de LimitedRegion.
     * Permite a ChunkGenerationListener saber con certeza que el chunk fue generado por BlockPopulator
     * para sellar el PDC sin ejecutar una segunda pasada destructiva de generación.
     */
    private final Set<Long> inFlightGeneratedChunks = ConcurrentHashMap.newKeySet();

    public RoadGenerator() {
        this.stateKey = new NamespacedKey(NAMESPACE, PDC_STATE_KEY);
        this.logger = null;
    }

    public RoadGenerator(NamespacedKey stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey, "La clave de estado no puede ser nula");
        this.logger = null;
    }

    public RoadGenerator(Plugin plugin) {
        Objects.requireNonNull(plugin, "El plugin no puede ser nulo");
        this.stateKey = new NamespacedKey(plugin, PDC_STATE_KEY);
        this.logger = plugin.getLogger();
    }

    public NamespacedKey getStateKey() {
        return stateKey;
    }

    public void markInFlightCompleted(int chunkX, int chunkZ) {
        inFlightGeneratedChunks.add(Chunk.getChunkKey(chunkX, chunkZ));
    }

    public boolean consumeInFlightCompleted(int chunkX, int chunkZ) {
        return inFlightGeneratedChunks.remove(Chunk.getChunkKey(chunkX, chunkZ));
    }

    public boolean isInFlightCompleted(int chunkX, int chunkZ) {
        return inFlightGeneratedChunks.contains(Chunk.getChunkKey(chunkX, chunkZ));
    }

    public void clearInFlightCache() {
        inFlightGeneratedChunks.clear();
    }

    /**
     * Aplica la generación procedural de carretera dentro de una región limitada durante la fase de población de chunks.
     * Confinado estrictamente al chunk central (chunkX, chunkZ) sin acceder a World ni a chunks vecinos.
     *
     * @param worldInfo Información del mundo (altura máxima y mínima).
     * @param chunkX Coordenada del chunk en el eje X.
     * @param chunkZ Coordenada del chunk en el eje Z.
     * @param limitedRegion Región acotada donde se aplican las mutaciones de bloque.
     * @return true si el chunk intersectó una carretera y se aplicó la generación; false en caso contrario.
     */
    public boolean generateInRegion(WorldInfo worldInfo, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        try {
            Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(chunkX, chunkZ);
            if (opt.isEmpty()) {
                return false;
            }

            ChunkIntersection intersection = opt.get();
            validateChunkBounds(chunkX, chunkZ, intersection);

            RoadDirection direction = intersection.direction();
            Material pavementMaterial = Material.valueOf(direction.getConcreteMaterial());

            int maxHeight = worldInfo.getMaxHeight();
            int topClearanceY = maxHeight - 1;

            int expectedMinX = chunkX << 4;
            int expectedMaxX = expectedMinX + 15;
            int expectedMinZ = chunkZ << 4;
            int expectedMaxZ = expectedMinZ + 15;

            for (int x = intersection.minBlockX(); x <= intersection.maxBlockX(); x++) {
                // Confinamiento estricto al chunk esperado
                if (x < expectedMinX || x > expectedMaxX) {
                    continue;
                }
                for (int z = intersection.minBlockZ(); z <= intersection.maxBlockZ(); z++) {
                    if (z < expectedMinZ || z > expectedMaxZ) {
                        continue;
                    }

                    // 1. Pavimento fijo a cota Y = 62
                    if (limitedRegion.isInRegion(x, RoadGeometry.PAVEMENT_Y, z)) {
                        Material current = limitedRegion.getType(x, RoadGeometry.PAVEMENT_Y, z);
                        if (current != pavementMaterial) {
                            limitedRegion.setType(x, RoadGeometry.PAVEMENT_Y, z, pavementMaterial);
                        }
                    }

                    // 2. Despeje vertical continuo e incondicional desde Y = 63 hasta maxHeight - 1
                    for (int y = RoadGeometry.PAVEMENT_Y + 1; y <= topClearanceY; y++) {
                        if (limitedRegion.isInRegion(x, y, z)) {
                            Material current = limitedRegion.getType(x, y, z);
                            if (!isAirBlock(current)) {
                                limitedRegion.setType(x, y, z, Material.AIR);
                            }
                        }
                    }

                    // REGLA ESTRICTA: Cero mutaciones, inspecciones destructivas o rellenos en Y < 62.
                }
            }

            // Se marca como completado en memoria únicamente al finalizar el procesamiento sin excepciones
            markInFlightCompleted(chunkX, chunkZ);
            return true;
        } catch (Throwable t) {
            if (logger != null) {
                logger.log(java.util.logging.Level.SEVERE, String.format(
                        "[Generator] Error crítico al generar carretera en LimitedRegion [world: %s, chunk: [%d, %d]]: %s",
                        worldInfo.getName(), chunkX, chunkZ, t.getMessage()
                ), t);
            }
            throw t;
        }
    }

    /**
     * Aplica la generación procedural de carretera directamente sobre un chunk cargado en el mundo.
     * Utiliza acceso local al chunk para garantizar que jamás se muten bloques de otros chunks.
     *
     * @param chunk Chunk de Bukkit donde se aplicará la generación.
     * @return true si el chunk intersectó una carretera y se aplicó la generación; false en caso contrario.
     */
    public boolean generateInChunk(Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Idempotencia: si ya está sellado en PDC, no alterar nada
        if (isChunkCompleted(chunk)) {
            return false;
        }

        try {
            Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(chunkX, chunkZ);
            if (opt.isEmpty()) {
                return false;
            }

            ChunkIntersection intersection = opt.get();
            validateChunkBounds(chunkX, chunkZ, intersection);

            RoadDirection direction = intersection.direction();
            Material pavementMaterial = Material.valueOf(direction.getConcreteMaterial());

            int maxHeight = chunk.getWorld().getMaxHeight();
            int topClearanceY = maxHeight - 1;

            int chunkOriginX = chunkX << 4;
            int chunkOriginZ = chunkZ << 4;

            for (int x = intersection.minBlockX(); x <= intersection.maxBlockX(); x++) {
                int localX = x - chunkOriginX;
                if (localX < 0 || localX > 15) {
                    throw new IllegalStateException("Coordenada local X fuera de rango: " + localX);
                }

                for (int z = intersection.minBlockZ(); z <= intersection.maxBlockZ(); z++) {
                    int localZ = z - chunkOriginZ;
                    if (localZ < 0 || localZ > 15) {
                        throw new IllegalStateException("Coordenada local Z fuera de rango: " + localZ);
                    }

                    // 1. Pavimento fijo a cota Y = 62 vía acceso local de Chunk
                    Block pavementBlock = chunk.getBlock(localX, RoadGeometry.PAVEMENT_Y, localZ);
                    if (pavementBlock.getType() != pavementMaterial) {
                        pavementBlock.setType(pavementMaterial, false);
                    }

                    // 2. Despeje vertical continuo e incondicional desde Y = 63 hasta maxHeight - 1
                    for (int y = RoadGeometry.PAVEMENT_Y + 1; y <= topClearanceY; y++) {
                        Block block = chunk.getBlock(localX, y, localZ);
                        if (!isAirBlock(block.getType())) {
                            block.setType(Material.AIR, false);
                        }
                    }

                    // REGLA ESTRICTA: Cero mutaciones en Y < 62.
                }
            }

            // Marcar estado en PDC e in-flight únicamente tras completar el 100% de bloques sin error
            markChunkCompleted(chunk);
            markInFlightCompleted(chunkX, chunkZ);

            return true;
        } catch (Throwable t) {
            if (logger != null) {
                logger.log(java.util.logging.Level.SEVERE, String.format(
                        "[Generator] Error crítico al generar carretera en [world: %s, chunk: [%d, %d]]: %s",
                        chunk.getWorld().getName(), chunkX, chunkZ, t.getMessage()
                ), t);
            }
            throw t;
        }
    }

    /**
     * Comprueba si todos los bloques de pavimento de la carretera en el chunk ya están presentes.
     */
    public boolean isPavementFullyPresent(Chunk chunk) {
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(chunkX, chunkZ);
        if (opt.isEmpty()) {
            return false;
        }

        ChunkIntersection intersection = opt.get();
        Material expected = Material.valueOf(intersection.direction().getConcreteMaterial());
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;

        for (int x = intersection.minBlockX(); x <= intersection.maxBlockX(); x++) {
            int localX = x - originX;
            for (int z = intersection.minBlockZ(); z <= intersection.maxBlockZ(); z++) {
                int localZ = z - originZ;
                Block block = chunk.getBlock(localX, RoadGeometry.PAVEMENT_Y, localZ);
                if (block.getType() != expected) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Valida que las coordenadas calculadas por ChunkIntersection pertenezcan rigurosamente al chunk especificado.
     */
    private void validateChunkBounds(int chunkX, int chunkZ, ChunkIntersection intersection) {
        int minX = chunkX << 4;
        int maxX = minX + 15;
        int minZ = chunkZ << 4;
        int maxZ = minZ + 15;

        if (intersection.minBlockX() < minX || intersection.maxBlockX() > maxX ||
            intersection.minBlockZ() < minZ || intersection.maxBlockZ() > maxZ) {
            throw new IllegalStateException(String.format(
                "ChunkIntersection [%d..%d, %d..%d] desborda los límites del chunk (%d, %d) [%d..%d, %d..%d]",
                intersection.minBlockX(), intersection.maxBlockX(),
                intersection.minBlockZ(), intersection.maxBlockZ(),
                chunkX, chunkZ, minX, maxX, minZ, maxZ
            ));
        }
    }

    /**
     * Determina si un material corresponde a aire sin requerir la inicialización del registro de Bukkit.
     */
    public static boolean isAirBlock(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    /**
     * Marca un chunk en su PersistentDataContainer como completado por InfinityRoads.
     */
    public void markChunkCompleted(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        pdc.set(stateKey, PersistentDataType.STRING, PDC_STATE_COMPLETED);
    }

    /**
     * Verifica si un chunk ya fue procesado y completado previamente.
     */
    public boolean isChunkCompleted(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        return PDC_STATE_COMPLETED.equals(pdc.get(stateKey, PersistentDataType.STRING));
    }
}
