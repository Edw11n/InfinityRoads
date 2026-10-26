package com.infinityroads.geometry;

import java.util.Optional;

/**
 * Módulo matemático puro que define la geometría, volúmenes de protección,
 * intersecciones de chunks y límites espaciales de InfinityRoads.
 *
 * RESTRICCIÓN ARQUITECTÓNICA: Esta clase es de lógica pura y no posee ninguna
 * dependencia de Bukkit, Paper ni NMS. Es 100% determinista, thread-safe y testeable en JUnit 5.
 */
public final class RoadGeometry {

    // Límites inclusivos del cuadrado de exclusión del spawn
    public static final int SPAWN_MIN_X = -100;
    public static final int SPAWN_MAX_X = 100;
    public static final int SPAWN_MIN_Z = -100;
    public static final int SPAWN_MAX_Z = 100;

    // Parámetros transversales del corredor (11 bloques en total: -5 a +5 respecto al eje)
    public static final int ROAD_WIDTH = 11;
    public static final int ROAD_HALF_WIDTH = 5;

    // Cota fija e inamovible de la superficie superior del pavimento
    public static final int PAVEMENT_Y = 62;

    // Gálibo mínimo requerido para circulación libre (Y = 63 hasta Y = 67 inclusive)
    public static final int MIN_CLEARANCE_HEIGHT = 5;

    // Coordenadas exactas de inicio exterior de los caminos (primer bloque fuera del spawn)
    public static final int NORTH_START_Z = -101; // Avanza hacia Z negativo
    public static final int SOUTH_START_Z = 101;  // Avanza hacia Z positivo
    public static final int EAST_START_X = 101;   // Avanza hacia X positivo
    public static final int WEST_START_X = -101;  // Avanza hacia X negativo

    // Rango válido de coordenadas de chunk para evitar desbordamiento aritmético en desplazamientos (<< 4)
    public static final int MIN_VALID_CHUNK = Integer.MIN_VALUE >> 4; // -134,217,728
    public static final int MAX_VALID_CHUNK = Integer.MAX_VALUE >> 4; //  134,217,727

    private RoadGeometry() {
        // Constructor privado: clase utilitaria estática
    }

    /**
     * Determina si una coordenada de bloque horizontal (X, Z) cae dentro del spawn protegido inclusivo.
     * Cuadrado inclusivo: X ∈ [-100, 100] y Z ∈ [-100, 100].
     */
    public static boolean isInsideSpawn(int x, int z) {
        return x >= SPAWN_MIN_X && x <= SPAWN_MAX_X && z >= SPAWN_MIN_Z && z <= SPAWN_MAX_Z;
    }

    /**
     * Determina la dirección cardinal del camino al que pertenece una coordenada (X, Z).
     * Retorna null si la coordenada está dentro del spawn o fuera de cualquier corredor.
     */
    public static RoadDirection getRoadDirection(int x, int z) {
        if (isInsideSpawn(x, z)) {
            return null;
        }

        // Corredor Norte: X ∈ [-5, 5], Z <= -101
        if (x >= -ROAD_HALF_WIDTH && x <= ROAD_HALF_WIDTH && z <= NORTH_START_Z) {
            return RoadDirection.NORTH;
        }

        // Corredor Sur: X ∈ [-5, 5], Z >= 101
        if (x >= -ROAD_HALF_WIDTH && x <= ROAD_HALF_WIDTH && z >= SOUTH_START_Z) {
            return RoadDirection.SOUTH;
        }

        // Corredor Este: Z ∈ [-5, 5], X >= 101
        if (z >= -ROAD_HALF_WIDTH && z <= ROAD_HALF_WIDTH && x >= EAST_START_X) {
            return RoadDirection.EAST;
        }

        // Corredor Oeste: Z ∈ [-5, 5], X <= -101
        if (z >= -ROAD_HALF_WIDTH && z <= ROAD_HALF_WIDTH && x <= WEST_START_X) {
            return RoadDirection.WEST;
        }

        return null;
    }

    /**
     * Retorna verdadero si la coordenada (X, Z) forma parte de cualquiera de los cuatro corredores de 11 bloques.
     */
    public static boolean isInsideCorridor(int x, int z) {
        return getRoadDirection(x, z) != null;
    }

    /**
     * Determina si un bloque en (X, Y, Z) pertenece al Volumen Protegido de InfinityRoads frente a acciones de jugadores.
     * Cubre toda la columna vertical válida del mundo: Y ∈ [minHeight, maxHeightExclusive - 1].
     *
     * @param x Coordenada global X
     * @param y Coordenada global Y
     * @param z Coordenada global Z
     * @param minHeight Cota mínima válida del mundo (world.getMinHeight(), inclusiva)
     * @param maxHeightExclusive Cota máxima del mundo (world.getMaxHeight(), exclusiva)
     */
    public static boolean isProtectedVolume(int x, int y, int z, int minHeight, int maxHeightExclusive) {
        if (y < minHeight || y >= maxHeightExclusive) {
            return false;
        }
        return isInsideCorridor(x, z);
    }

    /**
     * Determina si la coordenada corresponde exactamente al bloque de pavimento en Y = 62.
     */
    public static boolean isPavement(int x, int y, int z) {
        return y == PAVEMENT_Y && isInsideCorridor(x, z);
    }

    /**
     * Determina si la coordenada corresponde al espacio de despeje aéreo sobre el pavimento (Y >= 63 hasta maxHeight - 1).
     */
    public static boolean isClearanceSpace(int x, int y, int z, int maxHeightExclusive) {
        return y > PAVEMENT_Y && y < maxHeightExclusive && isInsideCorridor(x, z);
    }

    /**
     * Determina si la coordenada corresponde al espacio de despeje aéreo sobre el pavimento,
     * validando simultáneamente cotas mínimas y máximas dinámicas.
     */
    public static boolean isClearanceSpace(int x, int y, int z, int minHeight, int maxHeightExclusive) {
        if (y < minHeight || y >= maxHeightExclusive || y <= PAVEMENT_Y) {
            return false;
        }
        return isInsideCorridor(x, z);
    }

    /**
     * Determina si una coordenada de bloque es MODIFICABLE POR EL GENERADOR durante la construcción de la carretera.
     * REGLA ESTRICTA: El generador solo puede modificar bloques en Y >= 62.
     * TODO BLOQUE EN Y < 62 ES ESTRICTAMENTE INTOCABLE POR EL GENERADOR.
     */
    public static boolean isGeneratorModifiable(int x, int y, int z, int maxHeightExclusive) {
        if (y < PAVEMENT_Y || y >= maxHeightExclusive) {
            return false;
        }
        return isInsideCorridor(x, z);
    }

    /**
     * Determina si una coordenada de bloque es MODIFICABLE POR EL GENERADOR durante la construcción de la carretera,
     * validando límites dinámicos del mundo.
     * REGLA ESTRICTA: El generador solo puede modificar bloques en Y >= 62 y dentro del mundo [minHeight, maxHeightExclusive).
     */
    public static boolean isGeneratorModifiable(int x, int y, int z, int minHeight, int maxHeightExclusive) {
        if (y < minHeight || y < PAVEMENT_Y || y >= maxHeightExclusive) {
            return false;
        }
        return isInsideCorridor(x, z);
    }

    /**
     * Determina si una coordenada horizontal (X, Z) se encuentra inmediatamente contigua al corredor (a distancia 1 bloque).
     * Corresponde exactamente a las franjas de borde en ±6 fuera del spawn.
     * Estas coordenadas están 100% libres de protección para que los jugadores edifiquen.
     */
    public static boolean isDirectlyAdjacent(int x, int z) {
        if (isInsideSpawn(x, z)) {
            return false;
        }

        // Adyacente a los corredores Norte o Sur (ancho en X: [-5, 5])
        if ((x == -ROAD_HALF_WIDTH - 1 || x == ROAD_HALF_WIDTH + 1) && (z <= NORTH_START_Z || z >= SOUTH_START_Z)) {
            return true;
        }

        // Adyacente a los corredores Este u Oeste (ancho en Z: [-5, 5])
        if ((z == -ROAD_HALF_WIDTH - 1 || z == ROAD_HALF_WIDTH + 1) && (x <= WEST_START_X || x >= EAST_START_X)) {
            return true;
        }

        return false;
    }

    /**
     * Comprobación rápida O(1) para determinar si un chunk (chunkX, chunkZ) intersecta alguno de los cuatro corredores.
     * Seguro contra desbordamientos aritméticos con coordenadas de enteros extremos.
     */
    public static boolean chunkIntersectsRoad(int chunkX, int chunkZ) {
        if (chunkX < MIN_VALID_CHUNK || chunkX > MAX_VALID_CHUNK ||
            chunkZ < MIN_VALID_CHUNK || chunkZ > MAX_VALID_CHUNK) {
            return false;
        }

        int minChunkX = chunkX << 4;
        int maxChunkX = minChunkX + 15;
        int minChunkZ = chunkZ << 4;
        int maxChunkZ = minChunkZ + 15;

        // Corredor Norte: X ∈ [-5, 5], Z <= -101
        if (maxChunkX >= -ROAD_HALF_WIDTH && minChunkX <= ROAD_HALF_WIDTH && minChunkZ <= NORTH_START_Z) {
            return true;
        }

        // Corredor Sur: X ∈ [-5, 5], Z >= 101
        if (maxChunkX >= -ROAD_HALF_WIDTH && minChunkX <= ROAD_HALF_WIDTH && maxChunkZ >= SOUTH_START_Z) {
            return true;
        }

        // Corredor Este: Z ∈ [-5, 5], X >= 101
        if (maxChunkZ >= -ROAD_HALF_WIDTH && minChunkZ <= ROAD_HALF_WIDTH && maxChunkX >= EAST_START_X) {
            return true;
        }

        // Corredor Oeste: Z ∈ [-5, 5], X <= -101
        if (maxChunkZ >= -ROAD_HALF_WIDTH && minChunkZ <= ROAD_HALF_WIDTH && minChunkX <= WEST_START_X) {
            return true;
        }

        return false;
    }

    /**
     * Evalúa un chunk y retorna los detalles de la intersección con la carretera correspondiente,
     * o un Optional vacío si el chunk no contiene ningún tramo de carretera fuera del spawn.
     * Seguro contra desbordamientos aritméticos con coordenadas de enteros extremos.
     */
    public static Optional<ChunkIntersection> evaluateChunk(int chunkX, int chunkZ) {
        if (chunkX < MIN_VALID_CHUNK || chunkX > MAX_VALID_CHUNK ||
            chunkZ < MIN_VALID_CHUNK || chunkZ > MAX_VALID_CHUNK) {
            return Optional.empty();
        }

        int minChunkX = chunkX << 4;
        int maxChunkX = minChunkX + 15;
        int minChunkZ = chunkZ << 4;
        int maxChunkZ = minChunkZ + 15;

        // 1. Corredor Norte: X ∈ [-5, 5], Z <= -101
        if (maxChunkX >= -ROAD_HALF_WIDTH && minChunkX <= ROAD_HALF_WIDTH && minChunkZ <= NORTH_START_Z) {
            int minX = Math.max(minChunkX, -ROAD_HALF_WIDTH);
            int maxX = Math.min(maxChunkX, ROAD_HALF_WIDTH);
            int minZ = minChunkZ;
            int maxZ = Math.min(maxChunkZ, NORTH_START_Z);
            return Optional.of(new ChunkIntersection(
                    RoadDirection.NORTH,
                    minX, maxX, minZ, maxZ,
                    minX - minChunkX, maxX - minChunkX,
                    minZ - minChunkZ, maxZ - minChunkZ
            ));
        }

        // 2. Corredor Sur: X ∈ [-5, 5], Z >= 101
        if (maxChunkX >= -ROAD_HALF_WIDTH && minChunkX <= ROAD_HALF_WIDTH && maxChunkZ >= SOUTH_START_Z) {
            int minX = Math.max(minChunkX, -ROAD_HALF_WIDTH);
            int maxX = Math.min(maxChunkX, ROAD_HALF_WIDTH);
            int minZ = Math.max(minChunkZ, SOUTH_START_Z);
            int maxZ = maxChunkZ;
            return Optional.of(new ChunkIntersection(
                    RoadDirection.SOUTH,
                    minX, maxX, minZ, maxZ,
                    minX - minChunkX, maxX - minChunkX,
                    minZ - minChunkZ, maxZ - minChunkZ
            ));
        }

        // 3. Corredor Este: Z ∈ [-5, 5], X >= 101
        if (maxChunkZ >= -ROAD_HALF_WIDTH && minChunkZ <= ROAD_HALF_WIDTH && maxChunkX >= EAST_START_X) {
            int minX = Math.max(minChunkX, EAST_START_X);
            int maxX = maxChunkX;
            int minZ = Math.max(minChunkZ, -ROAD_HALF_WIDTH);
            int maxZ = Math.min(maxChunkZ, ROAD_HALF_WIDTH);
            return Optional.of(new ChunkIntersection(
                    RoadDirection.EAST,
                    minX, maxX, minZ, maxZ,
                    minX - minChunkX, maxX - minChunkX,
                    minZ - minChunkZ, maxZ - minChunkZ
            ));
        }

        // 4. Corredor Oeste: Z ∈ [-5, 5], X <= -101
        if (maxChunkZ >= -ROAD_HALF_WIDTH && minChunkZ <= ROAD_HALF_WIDTH && minChunkX <= WEST_START_X) {
            int minX = minChunkX;
            int maxX = Math.min(maxChunkX, WEST_START_X);
            int minZ = Math.max(minChunkZ, -ROAD_HALF_WIDTH);
            int maxZ = Math.min(maxChunkZ, ROAD_HALF_WIDTH);
            return Optional.of(new ChunkIntersection(
                    RoadDirection.WEST,
                    minX, maxX, minZ, maxZ,
                    minX - minChunkX, maxX - minChunkX,
                    minZ - minChunkZ, maxZ - minChunkZ
            ));
        }

        return Optional.empty();
    }
}
