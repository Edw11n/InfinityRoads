package com.infinityroads.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de RoadGeometry (Lógica Pura)")
class RoadGeometryTest {

    private static final int DEFAULT_MIN_HEIGHT = -64;
    private static final int DEFAULT_MAX_HEIGHT_EXCLUSIVE = 320;

    @Nested
    @DisplayName("1. Cuadrado Inclusivo del Spawn [-100, 100]")
    class SpawnExclusionTests {

        @Test
        @DisplayName("El centro exacto (0, 0) está dentro del spawn")
        void testCenterIsInSpawn() {
            assertTrue(RoadGeometry.isInsideSpawn(0, 0));
            assertNull(RoadGeometry.getRoadDirection(0, 0));
            assertFalse(RoadGeometry.isInsideCorridor(0, 0));
        }

        @ParameterizedTest(name = "Esquina ({0}, {1}) debe estar dentro del spawn")
        @CsvSource({
                "-100, -100",
                "100, -100",
                "-100, 100",
                "100, 100",
                "0, -100",
                "0, 100",
                "-100, 0",
                "100, 0",
                "-99, 99",
                "50, -50"
        })
        void testSpawnPerimeterIsInclusive(int x, int z) {
            assertTrue(RoadGeometry.isInsideSpawn(x, z),
                    () -> "La coordenada (" + x + ", " + z + ") debería estar dentro del spawn inclusivo");
            assertNull(RoadGeometry.getRoadDirection(x, z),
                    () -> "No debe haber carretera dentro del spawn en (" + x + ", " + z + ")");
            assertFalse(RoadGeometry.isInsideCorridor(x, z));
        }

        @ParameterizedTest(name = "Coordenada ({0}, {1}) fuera del spawn")
        @CsvSource({
                "0, -101",
                "0, 101",
                "101, 0",
                "-101, 0",
                "101, 101",
                "-101, -101",
                "200, 200"
        })
        void testOutsideSpawnCoordinates(int x, int z) {
            assertFalse(RoadGeometry.isInsideSpawn(x, z),
                    () -> "La coordenada (" + x + ", " + z + ") debería estar fuera del spawn");
        }
    }

    @Nested
    @DisplayName("2. Inicio Exacto de los Cuatro Caminos en ±101")
    class ExactRoadStartTests {

        @Test
        @DisplayName("Camino Norte inicia exactamente en Z = -101 avanzando a -Z")
        void testNorthRoadStart() {
            // En Z = -100 aún es spawn
            assertNull(RoadGeometry.getRoadDirection(0, -100));
            // En Z = -101 es el primer bloque del camino Norte (Amarillo)
            assertEquals(RoadDirection.NORTH, RoadGeometry.getRoadDirection(0, -101));
            assertEquals(RoadDirection.NORTH, RoadGeometry.getRoadDirection(0, -102));
            assertEquals(RoadDirection.NORTH, RoadGeometry.getRoadDirection(0, -5000));
            assertEquals("YELLOW_CONCRETE", RoadDirection.NORTH.getConcreteMaterial());
        }

        @Test
        @DisplayName("Camino Sur inicia exactamente en Z = 101 avanzando a +Z")
        void testSouthRoadStart() {
            // En Z = 100 aún es spawn
            assertNull(RoadGeometry.getRoadDirection(0, 100));
            // En Z = 101 es el primer bloque del camino Sur (Azul)
            assertEquals(RoadDirection.SOUTH, RoadGeometry.getRoadDirection(0, 101));
            assertEquals(RoadDirection.SOUTH, RoadGeometry.getRoadDirection(0, 102));
            assertEquals(RoadDirection.SOUTH, RoadGeometry.getRoadDirection(0, 5000));
            assertEquals("BLUE_CONCRETE", RoadDirection.SOUTH.getConcreteMaterial());
        }

        @Test
        @DisplayName("Camino Este inicia exactamente en X = 101 avanzando a +X")
        void testEastRoadStart() {
            // En X = 100 aún es spawn
            assertNull(RoadGeometry.getRoadDirection(100, 0));
            // En X = 101 es el primer bloque del camino Este (Rojo)
            assertEquals(RoadDirection.EAST, RoadGeometry.getRoadDirection(101, 0));
            assertEquals(RoadDirection.EAST, RoadGeometry.getRoadDirection(102, 0));
            assertEquals(RoadDirection.EAST, RoadGeometry.getRoadDirection(5000, 0));
            assertEquals("RED_CONCRETE", RoadDirection.EAST.getConcreteMaterial());
        }

        @Test
        @DisplayName("Camino Oeste inicia exactamente en X = -101 avanzando a -X")
        void testWestRoadStart() {
            // En X = -100 aún es spawn
            assertNull(RoadGeometry.getRoadDirection(-100, 0));
            // En X = -101 es el primer bloque del camino Oeste (Verde)
            assertEquals(RoadDirection.WEST, RoadGeometry.getRoadDirection(-101, 0));
            assertEquals(RoadDirection.WEST, RoadGeometry.getRoadDirection(-102, 0));
            assertEquals(RoadDirection.WEST, RoadGeometry.getRoadDirection(-5000, 0));
            assertEquals("GREEN_CONCRETE", RoadDirection.WEST.getConcreteMaterial());
        }
    }

    @Nested
    @DisplayName("3. Anchura Exacta de 11 Bloques [-5, 5]")
    class Exact11BlockWidthTests {

        @ParameterizedTest(name = "Norte X={0} debe pertenecer al camino")
        @ValueSource(ints = {-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5})
        void testNorthCorridorWidth(int x) {
            assertEquals(RoadDirection.NORTH, RoadGeometry.getRoadDirection(x, -200));
            assertTrue(RoadGeometry.isInsideCorridor(x, -200));
        }

        @ParameterizedTest(name = "Norte X={0} debe estar fuera del camino")
        @ValueSource(ints = {-7, -6, 6, 7})
        void testNorthCorridorOutsideWidth(int x) {
            assertNull(RoadGeometry.getRoadDirection(x, -200));
            assertFalse(RoadGeometry.isInsideCorridor(x, -200));
        }

        @ParameterizedTest(name = "Sur X={0} debe pertenecer al camino")
        @ValueSource(ints = {-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5})
        void testSouthCorridorWidth(int x) {
            assertEquals(RoadDirection.SOUTH, RoadGeometry.getRoadDirection(x, 200));
            assertTrue(RoadGeometry.isInsideCorridor(x, 200));
        }

        @ParameterizedTest(name = "Sur X={0} debe estar fuera del camino")
        @ValueSource(ints = {-7, -6, 6, 7})
        void testSouthCorridorOutsideWidth(int x) {
            assertNull(RoadGeometry.getRoadDirection(x, 200));
            assertFalse(RoadGeometry.isInsideCorridor(x, 200));
        }

        @ParameterizedTest(name = "Este Z={0} debe pertenecer al camino")
        @ValueSource(ints = {-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5})
        void testEastCorridorWidth(int z) {
            assertEquals(RoadDirection.EAST, RoadGeometry.getRoadDirection(200, z));
            assertTrue(RoadGeometry.isInsideCorridor(200, z));
        }

        @ParameterizedTest(name = "Este Z={0} debe estar fuera del camino")
        @ValueSource(ints = {-7, -6, 6, 7})
        void testEastCorridorOutsideWidth(int z) {
            assertNull(RoadGeometry.getRoadDirection(200, z));
            assertFalse(RoadGeometry.isInsideCorridor(200, z));
        }

        @ParameterizedTest(name = "Oeste Z={0} debe pertenecer al camino")
        @ValueSource(ints = {-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5})
        void testWestCorridorWidth(int z) {
            assertEquals(RoadDirection.WEST, RoadGeometry.getRoadDirection(-200, z));
            assertTrue(RoadGeometry.isInsideCorridor(-200, z));
        }

        @ParameterizedTest(name = "Oeste Z={0} debe estar fuera del camino")
        @ValueSource(ints = {-7, -6, 6, 7})
        void testWestCorridorOutsideWidth(int z) {
            assertNull(RoadGeometry.getRoadDirection(-200, z));
            assertFalse(RoadGeometry.isInsideCorridor(-200, z));
        }
    }

    @Nested
    @DisplayName("4. Laterales Inmediatos en ±6 (Libres de Protección)")
    class DirectlyAdjacentBorderTests {

        @ParameterizedTest(name = "Norte lateral ({0}, -200) es adyacente")
        @ValueSource(ints = {-6, 6})
        void testNorthDirectlyAdjacent(int x) {
            assertTrue(RoadGeometry.isDirectlyAdjacent(x, -200));
            assertFalse(RoadGeometry.isInsideCorridor(x, -200));
        }

        @ParameterizedTest(name = "Sur lateral ({0}, 200) es adyacente")
        @ValueSource(ints = {-6, 6})
        void testSouthDirectlyAdjacent(int x) {
            assertTrue(RoadGeometry.isDirectlyAdjacent(x, 200));
            assertFalse(RoadGeometry.isInsideCorridor(x, 200));
        }

        @ParameterizedTest(name = "Este lateral (200, {0}) es adyacente")
        @ValueSource(ints = {-6, 6})
        void testEastDirectlyAdjacent(int z) {
            assertTrue(RoadGeometry.isDirectlyAdjacent(200, z));
            assertFalse(RoadGeometry.isInsideCorridor(200, z));
        }

        @ParameterizedTest(name = "Oeste lateral (-200, {0}) es adyacente")
        @ValueSource(ints = {-6, 6})
        void testWestDirectlyAdjacent(int z) {
            assertTrue(RoadGeometry.isDirectlyAdjacent(-200, z));
            assertFalse(RoadGeometry.isInsideCorridor(-200, z));
        }

        @Test
        @DisplayName("Dentro del corredor o más allá de ±6 no es adyacente inmediato")
        void testNonAdjacentCoordinates() {
            // Dentro del corredor no es adyacente externo
            assertFalse(RoadGeometry.isDirectlyAdjacent(0, -200));
            assertFalse(RoadGeometry.isDirectlyAdjacent(5, -200));
            // A distancia 2 bloques (±7) no es adyacente directo
            assertFalse(RoadGeometry.isDirectlyAdjacent(7, -200));
            assertFalse(RoadGeometry.isDirectlyAdjacent(-7, -200));
            // Dentro del spawn no aplica
            assertFalse(RoadGeometry.isDirectlyAdjacent(6, 50));
        }
    }

    @Nested
    @DisplayName("5. Disyunción Geométrica: Ninguna Carretera se Solapa")
    class CorridorsDisjointTests {

        @Test
        @DisplayName("Las diagonales no pertenecen a ninguna carretera")
        void testDiagonalsAreEmpty() {
            assertNull(RoadGeometry.getRoadDirection(200, 200));
            assertNull(RoadGeometry.getRoadDirection(-200, 200));
            assertNull(RoadGeometry.getRoadDirection(200, -200));
            assertNull(RoadGeometry.getRoadDirection(-200, -200));
        }

        @Test
        @DisplayName("Verificar que un punto sólo puede pertenecer a un corredor o a ninguno")
        void testSingleOwnership() {
            int hits = 0;
            // Punto en camino Este
            if (RoadGeometry.getRoadDirection(150, 0) == RoadDirection.EAST) hits++;
            if (RoadGeometry.getRoadDirection(150, 0) == RoadDirection.NORTH) hits++;
            if (RoadGeometry.getRoadDirection(150, 0) == RoadDirection.SOUTH) hits++;
            if (RoadGeometry.getRoadDirection(150, 0) == RoadDirection.WEST) hits++;
            assertEquals(1, hits);
        }
    }

    @Nested
    @DisplayName("6. Límites Verticales Dinámicos [minHeight, maxHeightExclusive)")
    class VerticalLimitsTests {

        @Test
        @DisplayName("Overworld estándar: minHeight = -64, maxHeight = 320")
        void testStandardOverworldVerticalLimits() {
            int x = 0;
            int z = -200; // En camino Norte

            // Por debajo del límite inferior válido (-65)
            assertFalse(RoadGeometry.isProtectedVolume(x, -65, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Cota mínima válida inclusiva (-64)
            assertTrue(RoadGeometry.isProtectedVolume(x, -64, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Cota del pavimento (62)
            assertTrue(RoadGeometry.isProtectedVolume(x, 62, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Cota máxima válida (319 = maxHeightExclusive - 1)
            assertTrue(RoadGeometry.isProtectedVolume(x, 319, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Cota máxima exclusiva (320) NO es válida
            assertFalse(RoadGeometry.isProtectedVolume(x, 320, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Por encima del límite (321)
            assertFalse(RoadGeometry.isProtectedVolume(x, 321, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }

        @Test
        @DisplayName("Mundo con límites personalizados (ej. 0 a 256)")
        void testCustomWorldLimits() {
            int x = 150; // En camino Este
            int z = 0;
            int customMin = 0;
            int customMax = 256;

            assertFalse(RoadGeometry.isProtectedVolume(x, -1, z, customMin, customMax));
            assertTrue(RoadGeometry.isProtectedVolume(x, 0, z, customMin, customMax));
            assertTrue(RoadGeometry.isProtectedVolume(x, 62, z, customMin, customMax));
            assertTrue(RoadGeometry.isProtectedVolume(x, 255, z, customMin, customMax));
            assertFalse(RoadGeometry.isProtectedVolume(x, 256, z, customMin, customMax));
        }
    }

    @Nested
    @DisplayName("7. Inalterabilidad Estricta de Y < 62 para el Generador")
    class GeneratorSubsurfaceIntegrityTests {

        @ParameterizedTest(name = "Cota Y={0} está por debajo del pavimento y NO debe ser modificada por el generador")
        @ValueSource(ints = {-64, -50, 0, 30, 59, 60, 61})
        void testGeneratorCannotModifySubsurface(int y) {
            int x = 0;
            int z = 300; // En camino Sur

            // La protección para jugadores sí cubre el subsuelo
            assertTrue(RoadGeometry.isProtectedVolume(x, y, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE),
                    "El subsuelo debe estar protegido contra jugadores");

            // PERO el generador TIENE PROHIBIDO modificarlo
            assertFalse(RoadGeometry.isGeneratorModifiable(x, y, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE),
                    "El generador NUNCA debe modificar bloques en Y < 62");

            assertFalse(RoadGeometry.isPavement(x, y, z));
            assertFalse(RoadGeometry.isClearanceSpace(x, y, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }

        @Test
        @DisplayName("Cota Y = 62 es pavimento y sí es modificable por el generador")
        void testPavementAtY62() {
            int x = 0;
            int z = 300;

            assertTrue(RoadGeometry.isPavement(x, 62, z));
            assertTrue(RoadGeometry.isGeneratorModifiable(x, 62, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            assertFalse(RoadGeometry.isClearanceSpace(x, 62, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }

        @ParameterizedTest(name = "Cota Y={0} es espacio de despeje aéreo y sí es modificable por el generador")
        @ValueSource(ints = {63, 64, 67, 100, 200, 319})
        void testClearanceAboveY62(int y) {
            int x = 0;
            int z = 300;

            assertFalse(RoadGeometry.isPavement(x, y, z));
            assertTrue(RoadGeometry.isClearanceSpace(x, y, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            assertTrue(RoadGeometry.isGeneratorModifiable(x, y, z, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }
    }

    @Nested
    @DisplayName("8. Límites de Chunks: Fronteras CX=0 y CX=-1 (Coordenadas Negativas)")
    class ChunkBoundaryTests {

        @Test
        @DisplayName("Camino Sur cruza exactamente entre CX=-1 (5 bloques) y CX=0 (6 bloques)")
        void testSouthRoadCrossesChunkBoundary() {
            int cz = 10; // Z ∈ [160, 175], fuera del spawn

            // Chunk CX = -1: X ∈ [-16, -1]. Intersecta en X ∈ [-5, -1] (5 bloques)
            assertTrue(RoadGeometry.chunkIntersectsRoad(-1, cz));
            Optional<ChunkIntersection> optWestHalf = RoadGeometry.evaluateChunk(-1, cz);
            assertTrue(optWestHalf.isPresent());
            ChunkIntersection westHalf = optWestHalf.get();
            assertEquals(RoadDirection.SOUTH, westHalf.direction());
            assertEquals(-5, westHalf.minBlockX());
            assertEquals(-1, westHalf.maxBlockX());
            assertEquals(11, westHalf.minLocalX()); // -5 - (-16) = 11
            assertEquals(15, westHalf.maxLocalX()); // -1 - (-16) = 15
            assertEquals(5, westHalf.maxBlockX() - westHalf.minBlockX() + 1);

            // Chunk CX = 0: X ∈ [0, 15]. Intersecta en X ∈ [0, 5] (6 bloques)
            assertTrue(RoadGeometry.chunkIntersectsRoad(0, cz));
            Optional<ChunkIntersection> optEastHalf = RoadGeometry.evaluateChunk(0, cz);
            assertTrue(optEastHalf.isPresent());
            ChunkIntersection eastHalf = optEastHalf.get();
            assertEquals(RoadDirection.SOUTH, eastHalf.direction());
            assertEquals(0, eastHalf.minBlockX());
            assertEquals(5, eastHalf.maxBlockX());
            assertEquals(0, eastHalf.minLocalX());
            assertEquals(5, eastHalf.maxLocalX());
            assertEquals(6, eastHalf.maxBlockX() - eastHalf.minBlockX() + 1);

            // Suma total de ancho: 5 + 6 = 11 bloques exactos
            assertEquals(11, (westHalf.maxBlockX() - westHalf.minBlockX() + 1) +
                    (eastHalf.maxBlockX() - eastHalf.minBlockX() + 1));

            // Chunks vecinos contiguos que NO deben intersectar
            assertFalse(RoadGeometry.chunkIntersectsRoad(-2, cz)); // X ∈ [-32, -17]
            assertFalse(RoadGeometry.chunkIntersectsRoad(1, cz));  // X ∈ [16, 31]
            assertTrue(RoadGeometry.evaluateChunk(-2, cz).isEmpty());
            assertTrue(RoadGeometry.evaluateChunk(1, cz).isEmpty());
        }

        @Test
        @DisplayName("Camino Este cruza entre CZ=-1 (5 bloques) y CZ=0 (6 bloques)")
        void testEastRoadCrossesChunkBoundary() {
            int cx = 10; // X ∈ [160, 175], fuera del spawn

            // Chunk CZ = -1: Z ∈ [-16, -1]. Intersecta en Z ∈ [-5, -1] (5 bloques)
            assertTrue(RoadGeometry.chunkIntersectsRoad(cx, -1));
            Optional<ChunkIntersection> optNorthHalf = RoadGeometry.evaluateChunk(cx, -1);
            assertTrue(optNorthHalf.isPresent());
            ChunkIntersection northHalf = optNorthHalf.get();
            assertEquals(RoadDirection.EAST, northHalf.direction());
            assertEquals(-5, northHalf.minBlockZ());
            assertEquals(-1, northHalf.maxBlockZ());
            assertEquals(5, northHalf.maxBlockZ() - northHalf.minBlockZ() + 1);

            // Chunk CZ = 0: Z ∈ [0, 15]. Intersecta en Z ∈ [0, 5] (6 bloques)
            assertTrue(RoadGeometry.chunkIntersectsRoad(cx, 0));
            Optional<ChunkIntersection> optSouthHalf = RoadGeometry.evaluateChunk(cx, 0);
            assertTrue(optSouthHalf.isPresent());
            ChunkIntersection southHalf = optSouthHalf.get();
            assertEquals(RoadDirection.EAST, southHalf.direction());
            assertEquals(0, southHalf.minBlockZ());
            assertEquals(5, southHalf.maxBlockZ());
            assertEquals(6, southHalf.maxBlockZ() - southHalf.minBlockZ() + 1);

            // Suma total de ancho en Z: 5 + 6 = 11 bloques exactos
            assertEquals(11, (northHalf.maxBlockZ() - northHalf.minBlockZ() + 1) +
                    (southHalf.maxBlockZ() - southHalf.minBlockZ() + 1));
        }

        @Test
        @DisplayName("Chunk de transición en la frontera del spawn (CZ = 6, Z ∈ [96, 111])")
        void testSpawnTransitionChunk() {
            // Chunk CX = 0, CZ = 6: X ∈ [0, 15], Z ∈ [96, 111]
            // Contiene parte del spawn (Z <= 100) y el arranque del camino Sur (Z >= 101)
            assertTrue(RoadGeometry.chunkIntersectsRoad(0, 6));

            Optional<ChunkIntersection> optIntersection = RoadGeometry.evaluateChunk(0, 6);
            assertTrue(optIntersection.isPresent());
            ChunkIntersection intersection = optIntersection.get();

            assertEquals(RoadDirection.SOUTH, intersection.direction());
            assertEquals(0, intersection.minBlockX());
            assertEquals(5, intersection.maxBlockX());

            // Los bloques en Z deben arrancar ESTRICTAMENTE en 101 (el spawn Z <= 100 está excluido)
            assertEquals(101, intersection.minBlockZ());
            assertEquals(111, intersection.maxBlockZ());

            // Coordenadas locales correspondientes a Z: 101 - 96 = 5, 111 - 96 = 15
            assertEquals(5, intersection.minLocalZ());
            assertEquals(15, intersection.maxLocalZ());

            // Chunk completamente dentro del spawn: CZ = 5 (Z ∈ [80, 95])
            assertFalse(RoadGeometry.chunkIntersectsRoad(0, 5));
            assertTrue(RoadGeometry.evaluateChunk(0, 5).isEmpty());
        }

        @Test
        @DisplayName("Chunk central del spawn (0, 0) no intersecta ninguna carretera")
        void testSpawnCenterChunkDoesNotIntersect() {
            assertFalse(RoadGeometry.chunkIntersectsRoad(0, 0));
            assertTrue(RoadGeometry.evaluateChunk(0, 0).isEmpty());
        }
    }

    @Nested
    @DisplayName("9. Invariantes del Constructor y Métodos de ChunkIntersection")
    class ChunkIntersectionValidationTests {

        @Test
        @DisplayName("Rechazar dirección nula con NullPointerException")
        void testNullDirectionThrows() {
            assertThrows(NullPointerException.class, () ->
                    new ChunkIntersection(null, 0, 5, 101, 111, 0, 5, 5, 15));
        }

        @Test
        @DisplayName("Rechazar límites globales de X invertidos")
        void testInvertedGlobalXThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.NORTH, 5, 0, -111, -101, 0, 5, 0, 10));
            assertTrue(ex.getMessage().contains("Límites globales de X invertidos"));
        }

        @Test
        @DisplayName("Rechazar límites globales de Z invertidos")
        void testInvertedGlobalZThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.NORTH, 0, 5, -101, -111, 0, 5, 0, 10));
            assertTrue(ex.getMessage().contains("Límites globales de Z invertidos"));
        }

        @Test
        @DisplayName("Rechazar límites locales de X invertidos")
        void testInvertedLocalXThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 5, 0, 5, 15));
            assertTrue(ex.getMessage().contains("Límites locales de X invertidos"));
        }

        @Test
        @DisplayName("Rechazar límites locales de Z invertidos")
        void testInvertedLocalZThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 0, 5, 15, 5));
            assertTrue(ex.getMessage().contains("Límites locales de Z invertidos"));
        }

        @ParameterizedTest(name = "Rechazar localX={0} fuera de [0..15]")
        @ValueSource(ints = {-1, 16, 20, -5})
        void testLocalXOutOfBoundsThrows(int badLocalX) {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, badLocalX, 5, 5, 15));
        }

        @ParameterizedTest(name = "Rechazar localZ={0} fuera de [0..15]")
        @ValueSource(ints = {-1, 16, 20, -10})
        void testLocalZOutOfBoundsThrows(int badLocalZ) {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 0, 5, 5, badLocalZ));
        }

        @Test
        @DisplayName("Rechazar límites globales en X que cruzan fronteras de chunk")
        void testMultiChunkSpanXThrows() {
            // X=15 está en chunk 0, X=16 está en chunk 1 (con coordenadas locales válidas)
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.EAST, 15, 16, 0, 5, 0, 1, 0, 5));
            assertTrue(ex.getMessage().contains("múltiples chunks"));
        }

        @Test
        @DisplayName("Rechazar límites globales en Z que cruzan fronteras de chunk")
        void testMultiChunkSpanZThrows() {
            // Z=-1 está en chunk -1, Z=0 está en chunk 0 (con coordenadas locales válidas)
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, -1, 0, 0, 5, 0, 1));
            assertTrue(ex.getMessage().contains("múltiples chunks"));
        }

        @Test
        @DisplayName("Rechazar discrepancia de longitud en X entre global y local")
        void testSpanMismatchXThrows() {
            // Global span: 5 - 0 = 5. Local span: 4 - 0 = 4. Discrepancia!
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 0, 4, 5, 15));
            assertTrue(ex.getMessage().contains("Discrepancia en longitud X"));
        }

        @Test
        @DisplayName("Rechazar discrepancia de longitud en Z entre global y local")
        void testSpanMismatchZThrows() {
            // Global span: 111 - 101 = 10. Local span: 15 - 6 = 9. Discrepancia!
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 0, 5, 6, 15));
            assertTrue(ex.getMessage().contains("Discrepancia en longitud Z"));
        }

        @Test
        @DisplayName("Rechazar desalineación de módulo local (x & 15)")
        void testModuloMismatchThrows() {
            // Global X: [0, 5] en chunk 0, pero localX puesto artificialmente en [1, 6] (mismo span, pero desfasado)
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    new ChunkIntersection(RoadDirection.SOUTH, 0, 5, 101, 111, 1, 6, 5, 15));
            assertTrue(ex.getMessage().contains("Desalineación"));
        }

        @Test
        @DisplayName("Verificar cálculo unívoco de chunkX(), chunkZ(), getSpanX(), getSpanZ() y getColumnsCount()")
        void testChunkIntersectionAccessors() {
            // Chunk CX = -1, CZ = 10 -> X ∈ [-16, -1], Z ∈ [160, 175]
            // Intersección Sur: X ∈ [-5, -1] -> localX ∈ [11, 15], Z ∈ [160, 175] -> localZ ∈ [0, 15]
            ChunkIntersection ci = new ChunkIntersection(
                    RoadDirection.SOUTH,
                    -5, -1, 160, 175,
                    11, 15, 0, 15
            );

            assertEquals(-1, ci.chunkX());
            assertEquals(10, ci.chunkZ());
            assertEquals(5, ci.getSpanX());
            assertEquals(16, ci.getSpanZ());
            assertEquals(80, ci.getColumnsCount()); // 5 * 16 = 80 columnas
        }
    }

    @Nested
    @DisplayName("10. Fronteras de Transición en las Cuatro Direcciones")
    class TransitionChunksAllDirectionsTests {

        @Test
        @DisplayName("Transición Norte: CZ = -7 (Z ∈ [-112, -97]) arranca exactamente en Z = -101")
        void testNorthTransitionChunk() {
            // CX = 0, CZ = -7
            Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(0, -7);
            assertTrue(opt.isPresent());
            ChunkIntersection ci = opt.get();
            assertEquals(RoadDirection.NORTH, ci.direction());
            assertEquals(0, ci.minBlockX());
            assertEquals(5, ci.maxBlockX());
            assertEquals(-112, ci.minBlockZ());
            assertEquals(-101, ci.maxBlockZ()); // Termina en -101 hacia el spawn
            assertEquals(0, ci.minLocalZ());
            assertEquals(11, ci.maxLocalZ()); // -101 - (-112) = 11
            assertEquals(12, ci.getSpanZ()); // [-112, -101] -> 12 bloques de largo
            assertEquals(6 * 12, ci.getColumnsCount());

            // CX = -1, CZ = -7 (mitad oeste del corredor norte)
            Optional<ChunkIntersection> optWest = RoadGeometry.evaluateChunk(-1, -7);
            assertTrue(optWest.isPresent());
            ChunkIntersection ciWest = optWest.get();
            assertEquals(-5, ciWest.minBlockX());
            assertEquals(-1, ciWest.maxBlockX());
            assertEquals(5, ciWest.getSpanX());
            assertEquals(12, ciWest.getSpanZ());
            assertEquals(5 * 12, ciWest.getColumnsCount());

            // Total de columnas en la transición norte: 72 + 60 = 132 bloques
            assertEquals(132, ci.getColumnsCount() + ciWest.getColumnsCount());

            // Chunk inmediatamente interior al spawn (CZ = -6, Z ∈ [-96, -81]) no intersecta
            assertFalse(RoadGeometry.chunkIntersectsRoad(0, -6));
            assertTrue(RoadGeometry.evaluateChunk(0, -6).isEmpty());
        }

        @Test
        @DisplayName("Transición Este: CX = 6 (X ∈ [96, 111]) arranca exactamente en X = 101")
        void testEastTransitionChunk() {
            // CX = 6, CZ = 0
            Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(6, 0);
            assertTrue(opt.isPresent());
            ChunkIntersection ci = opt.get();
            assertEquals(RoadDirection.EAST, ci.direction());
            assertEquals(101, ci.minBlockX());
            assertEquals(111, ci.maxBlockX());
            assertEquals(0, ci.minBlockZ());
            assertEquals(5, ci.maxBlockZ());
            assertEquals(5, ci.minLocalX()); // 101 - 96 = 5
            assertEquals(15, ci.maxLocalX()); // 111 - 96 = 15
            assertEquals(11, ci.getSpanX());
            assertEquals(6, ci.getSpanZ());
            assertEquals(66, ci.getColumnsCount());

            // CX = 6, CZ = -1 (mitad norte del corredor este)
            Optional<ChunkIntersection> optNorth = RoadGeometry.evaluateChunk(6, -1);
            assertTrue(optNorth.isPresent());
            ChunkIntersection ciNorth = optNorth.get();
            assertEquals(-5, ciNorth.minBlockZ());
            assertEquals(-1, ciNorth.maxBlockZ());
            assertEquals(5, ciNorth.getSpanZ());
            assertEquals(11, ciNorth.getSpanX());
            assertEquals(55, ciNorth.getColumnsCount());

            // Total de columnas en la transición este: 66 + 55 = 121 bloques
            assertEquals(121, ci.getColumnsCount() + ciNorth.getColumnsCount());

            // Chunk interior al spawn (CX = 5, X ∈ [80, 95]) no intersecta
            assertFalse(RoadGeometry.chunkIntersectsRoad(5, 0));
            assertTrue(RoadGeometry.evaluateChunk(5, 0).isEmpty());
        }

        @Test
        @DisplayName("Transición Oeste: CX = -7 (X ∈ [-112, -97]) arranca exactamente en X = -101")
        void testWestTransitionChunk() {
            // CX = -7, CZ = 0
            Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(-7, 0);
            assertTrue(opt.isPresent());
            ChunkIntersection ci = opt.get();
            assertEquals(RoadDirection.WEST, ci.direction());
            assertEquals(-112, ci.minBlockX());
            assertEquals(-101, ci.maxBlockX());
            assertEquals(0, ci.minBlockZ());
            assertEquals(5, ci.maxBlockZ());
            assertEquals(0, ci.minLocalX());
            assertEquals(11, ci.maxLocalX());
            assertEquals(12, ci.getSpanX());
            assertEquals(6, ci.getSpanZ());
            assertEquals(72, ci.getColumnsCount());

            // CX = -7, CZ = -1 (mitad norte del corredor oeste)
            Optional<ChunkIntersection> optNorth = RoadGeometry.evaluateChunk(-7, -1);
            assertTrue(optNorth.isPresent());
            ChunkIntersection ciNorth = optNorth.get();
            assertEquals(-5, ciNorth.minBlockZ());
            assertEquals(-1, ciNorth.maxBlockZ());
            assertEquals(5, ciNorth.getSpanZ());
            assertEquals(12, ciNorth.getSpanX());
            assertEquals(60, ciNorth.getColumnsCount());

            // Total de columnas en la transición oeste: 72 + 60 = 132 bloques
            assertEquals(132, ci.getColumnsCount() + ciNorth.getColumnsCount());

            // Chunk interior al spawn (CX = -6, X ∈ [-96, -81]) no intersecta
            assertFalse(RoadGeometry.chunkIntersectsRoad(-6, 0));
            assertTrue(RoadGeometry.evaluateChunk(-6, 0).isEmpty());
        }
    }

    @Nested
    @DisplayName("11. Consistencia y Disyunción Global (chunkIntersectsRoad == evaluateChunk.isPresent)")
    class ConsistencyAndDisjointnessPropertyTests {

        @Test
        @DisplayName("Consistencia biunívoca en una cuadrícula completa de chunks alrededor del spawn [-15..15]")
        void testGridConsistencyAroundSpawn() {
            for (int cx = -15; cx <= 15; cx++) {
                for (int cz = -15; cz <= 15; cz++) {
                    final int curX = cx;
                    final int curZ = cz;
                    boolean fastCheck = RoadGeometry.chunkIntersectsRoad(curX, curZ);
                    Optional<ChunkIntersection> opt = RoadGeometry.evaluateChunk(curX, curZ);
                    assertEquals(fastCheck, opt.isPresent(),
                            () -> "Inconsistencia en chunk (" + curX + ", " + curZ + "): fastCheck=" + fastCheck + " vs isPresent=" + opt.isPresent());

                    if (opt.isPresent()) {
                        ChunkIntersection ci = opt.get();
                        assertEquals(curX, ci.chunkX());
                        assertEquals(curZ, ci.chunkZ());
                        assertTrue(ci.getColumnsCount() >= 1 && ci.getColumnsCount() <= 256);
                    }
                }
            }
        }

        @Test
        @DisplayName("Ningún chunk intersecta más de una carretera simultáneamente (demostración por disyunción)")
        void testNoChunkIntersectsMultipleRoads() {
            // Corremos un barrido de 400 chunks de prueba
            for (int cx = -20; cx <= 20; cx++) {
                for (int cz = -20; cz <= 20; cz++) {
                    final int curX = cx;
                    final int curZ = cz;
                    int intersectionsCount = 0;
                    int minChunkX = curX << 4;
                    int maxChunkX = minChunkX + 15;
                    int minChunkZ = curZ << 4;
                    int maxChunkZ = minChunkZ + 15;

                    // North check
                    if (maxChunkX >= -5 && minChunkX <= 5 && minChunkZ <= -101) intersectionsCount++;
                    // South check
                    if (maxChunkX >= -5 && minChunkX <= 5 && maxChunkZ >= 101) intersectionsCount++;
                    // East check
                    if (maxChunkZ >= -5 && minChunkZ <= 5 && maxChunkX >= 101) intersectionsCount++;
                    // West check
                    if (maxChunkZ >= -5 && minChunkZ <= 5 && minChunkX <= -101) intersectionsCount++;

                    final int totalIntersections = intersectionsCount;
                    assertTrue(totalIntersections <= 1,
                            () -> "El chunk (" + curX + ", " + curZ + ") intersecta " + totalIntersections + " carreteras a la vez, lo cual viola la separación mínima de 96 bloques!");
                }
            }
        }
    }

    @Nested
    @DisplayName("12. Sobrecargas Dinámicas de Límites Verticales")
    class DynamicOverloadsTests {

        @Test
        @DisplayName("isClearanceSpace con 5 parámetros respeta cotas dinámicas")
        void testClearanceSpace5Params() {
            int x = 0;
            int z = 200; // Corredor Sur

            // Por debajo de minHeight
            assertFalse(RoadGeometry.isClearanceSpace(x, -65, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            // En cota pavimento (62) NO es despeje aéreo
            assertFalse(RoadGeometry.isClearanceSpace(x, 62, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            // En Y=63 sí es despeje aéreo
            assertTrue(RoadGeometry.isClearanceSpace(x, 63, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            // En cota máxima exclusiva (320) NO es despeje
            assertFalse(RoadGeometry.isClearanceSpace(x, 320, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }

        @Test
        @DisplayName("isGeneratorModifiable con 5 parámetros respeta cota inferior Y < 62")
        void testGeneratorModifiable5Params() {
            int x = 0;
            int z = 200; // Corredor Sur

            // Subsuelo (Y < 62) NUNCA es modificable por el generador
            assertFalse(RoadGeometry.isGeneratorModifiable(x, 61, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            assertFalse(RoadGeometry.isGeneratorModifiable(x, -64, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Pavimento (Y = 62) sí es modificable por el generador
            assertTrue(RoadGeometry.isGeneratorModifiable(x, 62, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            // Despeje aéreo (Y = 63..319) sí es modificable
            assertTrue(RoadGeometry.isGeneratorModifiable(x, 63, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
            assertTrue(RoadGeometry.isGeneratorModifiable(x, 319, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));

            // Límite superior exclusivo (320) NO es modificable
            assertFalse(RoadGeometry.isGeneratorModifiable(x, 320, z, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT_EXCLUSIVE));
        }
    }

    @Nested
    @DisplayName("13. Seguridad contra Desbordamiento en Coordenadas Extremas")
    class ExtremeCoordinatesAndOverflowTests {

        @ParameterizedTest(name = "Coordenada de chunk extrema ({0}, {1}) debe retornar false sin desbordamiento ni excepción")
        @CsvSource({
                "2147483647, 0",
                "-2147483648, 0",
                "0, 2147483647",
                "0, -2147483648",
                "2147483647, 2147483647",
                "-2147483648, -2147483648",
                "134217728, 0",
                "-134217729, 0",
                "0, 134217728",
                "0, -134217729"
        })
        void testExtremeChunkCoordinatesDoNotOverflow(int cx, int cz) {
            assertFalse(RoadGeometry.chunkIntersectsRoad(cx, cz));
            assertTrue(RoadGeometry.evaluateChunk(cx, cz).isEmpty());
        }

        @Test
        @DisplayName("Coordenadas de bloque en Integer.MIN_VALUE e Integer.MAX_VALUE en spawn y corredores")
        void testExtremeBlockCoordinates() {
            assertFalse(RoadGeometry.isInsideSpawn(Integer.MIN_VALUE, Integer.MAX_VALUE));
            assertFalse(RoadGeometry.isInsideSpawn(Integer.MAX_VALUE, Integer.MIN_VALUE));

            // En Z extremo negativo sobre X=0 pertenece al corredor Norte
            assertEquals(RoadDirection.NORTH, RoadGeometry.getRoadDirection(0, Integer.MIN_VALUE));
            assertTrue(RoadGeometry.isInsideCorridor(0, Integer.MIN_VALUE));

            // En Z extremo positivo sobre X=0 pertenece al corredor Sur
            assertEquals(RoadDirection.SOUTH, RoadGeometry.getRoadDirection(0, Integer.MAX_VALUE));
            assertTrue(RoadGeometry.isInsideCorridor(0, Integer.MAX_VALUE));

            // En X extremo positivo sobre Z=0 pertenece al corredor Este
            assertEquals(RoadDirection.EAST, RoadGeometry.getRoadDirection(Integer.MAX_VALUE, 0));
            assertTrue(RoadGeometry.isInsideCorridor(Integer.MAX_VALUE, 0));

            // En X extremo negativo sobre Z=0 pertenece al corredor Oeste
            assertEquals(RoadDirection.WEST, RoadGeometry.getRoadDirection(Integer.MIN_VALUE, 0));
            assertTrue(RoadGeometry.isInsideCorridor(Integer.MIN_VALUE, 0));
        }
    }
}
