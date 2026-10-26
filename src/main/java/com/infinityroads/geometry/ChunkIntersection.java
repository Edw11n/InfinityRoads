package com.infinityroads.geometry;

import java.util.Objects;

/**
 * Registro inmutable que describe la intersección de un chunk con una carretera cardinal.
 * Totalmente desacoplado de Bukkit/Paper.
 *
 * @param direction Dirección cardinal del corredor que intersecta.
 * @param minBlockX Coordenada global mínima de X dentro del chunk que pertenece al corredor.
 * @param maxBlockX Coordenada global máxima de X dentro del chunk que pertenece al corredor.
 * @param minBlockZ Coordenada global mínima de Z dentro del chunk que pertenece al corredor.
 * @param maxBlockZ Coordenada global máxima de Z dentro del chunk que pertenece al corredor.
 * @param minLocalX Coordenada local mínima de X [0..15] dentro del chunk.
 * @param maxLocalX Coordenada local máxima de X [0..15] dentro del chunk.
 * @param minLocalZ Coordenada local mínima de Z [0..15] dentro del chunk.
 * @param maxLocalZ Coordenada local máxima de Z [0..15] dentro del chunk.
 */
public record ChunkIntersection(
        RoadDirection direction,
        int minBlockX,
        int maxBlockX,
        int minBlockZ,
        int maxBlockZ,
        int minLocalX,
        int maxLocalX,
        int minLocalZ,
        int maxLocalZ
) {
    public ChunkIntersection {
        Objects.requireNonNull(direction, "La dirección no puede ser nula");

        // 1. Orden de límites globales
        if (minBlockX > maxBlockX) {
            throw new IllegalArgumentException(
                    "Límites globales de X invertidos: minBlockX=" + minBlockX + " > maxBlockX=" + maxBlockX);
        }
        if (minBlockZ > maxBlockZ) {
            throw new IllegalArgumentException(
                    "Límites globales de Z invertidos: minBlockZ=" + minBlockZ + " > maxBlockZ=" + maxBlockZ);
        }

        // 2. Orden de límites locales
        if (minLocalX > maxLocalX) {
            throw new IllegalArgumentException(
                    "Límites locales de X invertidos: minLocalX=" + minLocalX + " > maxLocalX=" + maxLocalX);
        }
        if (minLocalZ > maxLocalZ) {
            throw new IllegalArgumentException(
                    "Límites locales de Z invertidos: minLocalZ=" + minLocalZ + " > maxLocalZ=" + maxLocalZ);
        }

        // 3. Rango estricto de coordenadas locales [0..15]
        if (minLocalX < 0 || maxLocalX > 15 || minLocalZ < 0 || maxLocalZ > 15) {
            throw new IllegalArgumentException(String.format(
                    "Coordenadas locales fuera del rango [0..15]: localX=[%d, %d], localZ=[%d, %d]",
                    minLocalX, maxLocalX, minLocalZ, maxLocalZ));
        }

        // 4. Coherencia de un único chunk: min y max deben pertenecer exactamente al mismo chunk
        if ((minBlockX >> 4) != (maxBlockX >> 4)) {
            throw new IllegalArgumentException(String.format(
                    "Los límites globales en X abarcan múltiples chunks: minBlockX=%d (chunk %d) vs maxBlockX=%d (chunk %d)",
                    minBlockX, minBlockX >> 4, maxBlockX, maxBlockX >> 4));
        }
        if ((minBlockZ >> 4) != (maxBlockZ >> 4)) {
            throw new IllegalArgumentException(String.format(
                    "Los límites globales en Z abarcan múltiples chunks: minBlockZ=%d (chunk %d) vs maxBlockZ=%d (chunk %d)",
                    minBlockZ, minBlockZ >> 4, maxBlockZ, maxBlockZ >> 4));
        }

        // 5. Correspondencia matemática exacta entre envergadura global y local
        int spanGlobalX = maxBlockX - minBlockX;
        int spanLocalX = maxLocalX - minLocalX;
        if (spanGlobalX != spanLocalX) {
            throw new IllegalArgumentException(String.format(
                    "Discrepancia en longitud X: global=%d vs local=%d", spanGlobalX, spanLocalX));
        }

        int spanGlobalZ = maxBlockZ - minBlockZ;
        int spanLocalZ = maxLocalZ - minLocalZ;
        if (spanGlobalZ != spanLocalZ) {
            throw new IllegalArgumentException(String.format(
                    "Discrepancia en longitud Z: global=%d vs local=%d", spanGlobalZ, spanLocalZ));
        }

        // 6. Correspondencia exacta de módulo local (x & 15 == localX para todo entero en Java)
        if ((minBlockX & 15) != minLocalX || (maxBlockX & 15) != maxLocalX) {
            throw new IllegalArgumentException(String.format(
                    "Desalineación entre coordenadas globales y locales en X: global=[%d, %d] -> esperado=[%d, %d] vs recibido=[%d, %d]",
                    minBlockX, maxBlockX, minBlockX & 15, maxBlockX & 15, minLocalX, maxLocalX));
        }
        if ((minBlockZ & 15) != minLocalZ || (maxBlockZ & 15) != maxLocalZ) {
            throw new IllegalArgumentException(String.format(
                    "Desalineación entre coordenadas globales y locales en Z: global=[%d, %d] -> esperado=[%d, %d] vs recibido=[%d, %d]",
                    minBlockZ, maxBlockZ, minBlockZ & 15, maxBlockZ & 15, minLocalZ, maxLocalZ));
        }
    }

    /**
     * Retorna la coordenada de chunk X calculada de forma unívoca a partir de minBlockX.
     */
    public int chunkX() {
        return minBlockX >> 4;
    }

    /**
     * Retorna la coordenada de chunk Z calculada de forma unívoca a partir de minBlockZ.
     */
    public int chunkZ() {
        return minBlockZ >> 4;
    }

    /**
     * Retorna la cantidad de bloques de ancho en el eje X cubiertos en este chunk [1..16].
     */
    public int getSpanX() {
        return maxBlockX - minBlockX + 1;
    }

    /**
     * Retorna la cantidad de bloques de largo en el eje Z cubiertos en este chunk [1..16].
     */
    public int getSpanZ() {
        return maxBlockZ - minBlockZ + 1;
    }

    /**
     * Retorna la cantidad exacta de columnas verticales de bloque (X, Z) que el corredor ocupa en este chunk.
     * Como cada dimensión está estrictamente en [1..16], el resultado está acotado en [1..256] sin riesgo de desbordamiento.
     */
    public int getColumnsCount() {
        return getSpanX() * getSpanZ();
    }
}
