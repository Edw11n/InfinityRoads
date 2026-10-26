package com.infinityroads.generator;

import com.infinityroads.geometry.RoadDirection;
import com.infinityroads.geometry.RoadGeometry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias e Integración del Generador Procedural (Fase 2)")
class RoadGeneratorTest {

    record BlockCoord(int x, int y, int z) {}
    record BlockSetCall(int x, int y, int z, Material material) {}

    private RoadGenerator generator;
    private WorldInfo worldInfo;
    private TestRegion testRegion;
    private LimitedRegion limitedRegion;

    static WorldInfo createFakeWorldInfo(String name, int minHeight, int maxHeight) {
        return (WorldInfo) Proxy.newProxyInstance(
                WorldInfo.class.getClassLoader(),
                new Class<?>[]{WorldInfo.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getMinHeight" -> minHeight;
                    case "getMaxHeight" -> maxHeight;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FakeWorldInfo[" + name + "]";
                    default -> null;
                }
        );
    }

    static class TestRegion {
        final Map<BlockCoord, Material> blocks = new HashMap<>();
        final List<BlockSetCall> setCalls = new ArrayList<>();
        Material defaultMaterial = Material.DIRT;
        boolean throwOnSet = false;
        int failAtSetCount = -1;

        LimitedRegion createProxy() {
            return (LimitedRegion) Proxy.newProxyInstance(
                    LimitedRegion.class.getClassLoader(),
                    new Class<?>[]{LimitedRegion.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("isInRegion".equals(name)) {
                            return true;
                        }
                        if ("getType".equals(name) && args.length == 3) {
                            int x = (int) args[0];
                            int y = (int) args[1];
                            int z = (int) args[2];
                            return blocks.getOrDefault(new BlockCoord(x, y, z), defaultMaterial);
                        }
                        if ("setType".equals(name) && args.length >= 4) {
                            if (throwOnSet || (failAtSetCount > 0 && setCalls.size() >= failAtSetCount)) {
                                throw new RuntimeException("Simulated I/O or chunk error during setType");
                            }
                            int x = (int) args[0];
                            int y = (int) args[1];
                            int z = (int) args[2];
                            Material mat = (Material) args[3];
                            blocks.put(new BlockCoord(x, y, z), mat);
                            setCalls.add(new BlockSetCall(x, y, z, mat));
                            return null;
                        }
                        if ("equals".equals(name)) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }
                        return null;
                    }
            );
        }
    }

    static class TestChunkWorld {
        final Map<BlockCoord, Material> blocks = new HashMap<>();
        final List<BlockSetCall> setCalls = new ArrayList<>();
        final Map<String, String> pdcStrings = new HashMap<>();
        final List<Object> populators = new ArrayList<>();
        int chunkX = 0;
        int chunkZ = 10;
        int maxHeight = 320;
        Material defaultMaterial = Material.STONE;
        boolean throwOnSet = false;
        int failAtSetCount = -1;

        Block createBlockProxy(int x, int y, int z) {
            return (Block) Proxy.newProxyInstance(
                    Block.class.getClassLoader(),
                    new Class<?>[]{Block.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getType".equals(name)) {
                            return blocks.getOrDefault(new BlockCoord(x, y, z), defaultMaterial);
                        }
                        if ("setType".equals(name)) {
                            if (throwOnSet || (failAtSetCount > 0 && setCalls.size() >= failAtSetCount)) {
                                throw new RuntimeException("Simulated chunk mutation error");
                            }
                            Material mat = (Material) args[0];
                            blocks.put(new BlockCoord(x, y, z), mat);
                            setCalls.add(new BlockSetCall(x, y, z, mat));
                            return null;
                        }
                        if ("getX".equals(name)) return x;
                        if ("getY".equals(name)) return y;
                        if ("getZ".equals(name)) return z;
                        return null;
                    }
            );
        }

        World createWorldProxy() {
            return (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getMaxHeight".equals(name)) return maxHeight;
                        if ("getMinHeight".equals(name)) return -64;
                        if ("getName".equals(name)) return "world";
                        if ("getBlockAt".equals(name) && args.length == 3) {
                            return createBlockProxy((int) args[0], (int) args[1], (int) args[2]);
                        }
                        if ("getPopulators".equals(name)) return populators;
                        return null;
                    }
            );
        }

        PersistentDataContainer createPdcProxy() {
            return (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("set".equals(name) && args.length == 3) {
                            NamespacedKey key = (NamespacedKey) args[0];
                            Object val = args[2];
                            pdcStrings.put(key.toString(), String.valueOf(val));
                            return null;
                        }
                        if ("get".equals(name) && args.length >= 2) {
                            NamespacedKey key = (NamespacedKey) args[0];
                            return pdcStrings.get(key.toString());
                        }
                        if ("has".equals(name)) {
                            NamespacedKey key = (NamespacedKey) args[0];
                            return pdcStrings.containsKey(key.toString());
                        }
                        return null;
                    }
            );
        }

        Chunk createChunkProxy() {
            World worldProxy = createWorldProxy();
            PersistentDataContainer pdcProxy = createPdcProxy();
            return (Chunk) Proxy.newProxyInstance(
                    Chunk.class.getClassLoader(),
                    new Class<?>[]{Chunk.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getX".equals(name)) return chunkX;
                        if ("getZ".equals(name)) return chunkZ;
                        if ("getWorld".equals(name)) return worldProxy;
                        if ("getPersistentDataContainer".equals(name)) return pdcProxy;
                        if ("getBlock".equals(name) && args.length == 3) {
                            int lx = (int) args[0];
                            int y = (int) args[1];
                            int lz = (int) args[2];
                            int wx = (chunkX << 4) + lx;
                            int wz = (chunkZ << 4) + lz;
                            return createBlockProxy(wx, y, wz);
                        }
                        return null;
                    }
            );
        }
    }

    @BeforeEach
    void setUp() {
        worldInfo = createFakeWorldInfo("world", -64, 320);
        testRegion = new TestRegion();
        limitedRegion = testRegion.createProxy();
        generator = new RoadGenerator();
    }

    @Nested
    @DisplayName("1. Cuatro Direcciones Cardinales y Coordenadas Negativas")
    class CardinalDirectionsAndNegativeCoordsTests {

        @Test
        @DisplayName("Norte (CZ < 0): YELLOW_CONCRETE en CX=0 y CX=-1")
        void testNorthNegativeAndPositiveChunkX() {
            TestRegion regPos = new TestRegion();
            TestRegion regNeg = new TestRegion();

            assertTrue(generator.generateInRegion(worldInfo, 0, -10, regPos.createProxy()));
            assertTrue(generator.generateInRegion(worldInfo, -1, -10, regNeg.createProxy()));

            assertTrue(regPos.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.YELLOW_CONCRETE));
            assertTrue(regNeg.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.YELLOW_CONCRETE));

            long posCount = regPos.setCalls.stream().filter(c -> c.y() == 62).count();
            long negCount = regNeg.setCalls.stream().filter(c -> c.y() == 62).count();
            assertEquals(6 * 16, posCount);
            assertEquals(5 * 16, negCount);
            assertEquals(11 * 16, posCount + negCount);
        }

        @Test
        @DisplayName("Sur (CZ > 0): BLUE_CONCRETE en CX=0 y CX=-1")
        void testSouthNegativeAndPositiveChunkX() {
            TestRegion regPos = new TestRegion();
            TestRegion regNeg = new TestRegion();

            assertTrue(generator.generateInRegion(worldInfo, 0, 10, regPos.createProxy()));
            assertTrue(generator.generateInRegion(worldInfo, -1, 10, regNeg.createProxy()));

            assertTrue(regPos.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.BLUE_CONCRETE));
            assertTrue(regNeg.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.BLUE_CONCRETE));
            assertEquals(11 * 16, regPos.setCalls.stream().filter(c -> c.y() == 62).count()
                    + regNeg.setCalls.stream().filter(c -> c.y() == 62).count());
        }

        @Test
        @DisplayName("Este (CX > 0): RED_CONCRETE en CZ=0 y CZ=-1")
        void testEastNegativeAndPositiveChunkZ() {
            TestRegion regPos = new TestRegion();
            TestRegion regNeg = new TestRegion();

            assertTrue(generator.generateInRegion(worldInfo, 10, 0, regPos.createProxy()));
            assertTrue(generator.generateInRegion(worldInfo, 10, -1, regNeg.createProxy()));

            assertTrue(regPos.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.RED_CONCRETE));
            assertTrue(regNeg.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.RED_CONCRETE));
            assertEquals(11 * 16, regPos.setCalls.stream().filter(c -> c.y() == 62).count()
                    + regNeg.setCalls.stream().filter(c -> c.y() == 62).count());
        }

        @Test
        @DisplayName("Oeste (CX < 0): GREEN_CONCRETE en CZ=0 y CZ=-1")
        void testWestNegativeAndPositiveChunkZ() {
            TestRegion regPos = new TestRegion();
            TestRegion regNeg = new TestRegion();

            assertTrue(generator.generateInRegion(worldInfo, -10, 0, regPos.createProxy()));
            assertTrue(generator.generateInRegion(worldInfo, -10, -1, regNeg.createProxy()));

            assertTrue(regPos.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.GREEN_CONCRETE));
            assertTrue(regNeg.setCalls.stream().filter(c -> c.y() == 62)
                    .allMatch(c -> c.material() == Material.GREEN_CONCRETE));
            assertEquals(11 * 16, regPos.setCalls.stream().filter(c -> c.y() == 62).count()
                    + regNeg.setCalls.stream().filter(c -> c.y() == 62).count());
        }
    }

    @Nested
    @DisplayName("2. Ausencia de Concreto en Todo el Cuadrado del Spawn [-100, 100]")
    class SpawnSquareExclusionTests {

        @ParameterizedTest(name = "Chunk spawn interior ({0}, {1}) no debe generar nada")
        @CsvSource({
                "0, 0",
                "1, 1",
                "-1, -1",
                "5, 0",
                "-6, 0",
                "0, 5",
                "0, -6",
                "3, -3",
                "-3, 3"
        })
        void testNoConcreteInSpawnSquare(int cx, int cz) {
            TestRegion reg = new TestRegion();
            boolean generated = generator.generateInRegion(worldInfo, cx, cz, reg.createProxy());
            assertFalse(generated, "No debe generar carretera dentro del spawn");
            assertTrue(reg.setCalls.isEmpty(), "No debe haber ninguna mutación de bloque dentro del spawn");
        }

        @Test
        @DisplayName("Transición Este CX=6 (X ∈ [96, 111]): X ∈ [96, 100] es intocable")
        void testEastSpawnTransitionStrictBoundary() {
            TestRegion reg = new TestRegion();
            assertTrue(generator.generateInRegion(worldInfo, 6, 0, reg.createProxy()));

            for (BlockSetCall call : reg.setCalls) {
                assertTrue(call.x() >= 101, "Violación: mutación dentro de spawn X=" + call.x());
            }
        }

        @Test
        @DisplayName("Transición Oeste CX=-7 (X ∈ [-112, -97]): X ∈ [-100, -97] es intocable")
        void testWestSpawnTransitionStrictBoundary() {
            TestRegion reg = new TestRegion();
            assertTrue(generator.generateInRegion(worldInfo, -7, 0, reg.createProxy()));

            for (BlockSetCall call : reg.setCalls) {
                assertTrue(call.x() <= -101, "Violación: mutación dentro de spawn X=" + call.x());
            }
        }

        @Test
        @DisplayName("Transición Sur CZ=6 (Z ∈ [96, 111]): Z ∈ [96, 100] es intocable")
        void testSouthSpawnTransitionStrictBoundary() {
            TestRegion reg = new TestRegion();
            assertTrue(generator.generateInRegion(worldInfo, 0, 6, reg.createProxy()));

            for (BlockSetCall call : reg.setCalls) {
                assertTrue(call.z() >= 101, "Violación: mutación dentro de spawn Z=" + call.z());
            }
        }

        @Test
        @DisplayName("Transición Norte CZ=-7 (Z ∈ [-112, -97]): Z ∈ [-100, -97] es intocable")
        void testNorthSpawnTransitionStrictBoundary() {
            TestRegion reg = new TestRegion();
            assertTrue(generator.generateInRegion(worldInfo, 0, -7, reg.createProxy()));

            for (BlockSetCall call : reg.setCalls) {
                assertTrue(call.z() <= -101, "Violación: mutación dentro de spawn Z=" + call.z());
            }
        }
    }

    @Nested
    @DisplayName("3. Pavimento Exactamente en Y = 62")
    class PavementHeightY62Tests {

        @Test
        @DisplayName("Todo el concreto generado está estrictamente a Y = 62")
        void testPavementStrictlyAtY62() {
            testRegion.defaultMaterial = Material.WATER;
            generator.generateInRegion(worldInfo, 0, 10, limitedRegion);

            List<BlockSetCall> concreteCalls = testRegion.setCalls.stream()
                    .filter(c -> c.material().name().endsWith("_CONCRETE"))
                    .toList();

            assertFalse(concreteCalls.isEmpty());
            for (BlockSetCall call : concreteCalls) {
                assertEquals(62, call.y(), "El concreto debe situarse exclusivamente en Y=62");
            }
        }
    }

    @Nested
    @DisplayName("4. Ninguna Mutación por Debajo de Y = 62")
    class SubsurfaceStrictInvarianceTests {

        @ParameterizedTest(name = "Chunk ({0}, {1}) no altera bloques en Y < 62")
        @CsvSource({
                "0, 10",
                "-1, 10",
                "0, -10",
                "-1, -10",
                "10, 0",
                "10, -1",
                "-10, 0",
                "-10, -1"
        })
        void testZeroMutationsBelowY62InRegion(int cx, int cz) {
            testRegion.defaultMaterial = Material.LAVA;
            generator.generateInRegion(worldInfo, cx, cz, limitedRegion);

            for (BlockSetCall call : testRegion.setCalls) {
                assertTrue(call.y() >= 62, "Violación en subsuelo: Y=" + call.y());
            }
        }

        @Test
        @DisplayName("generateInChunk tampoco altera ningún bloque en Y < 62")
        void testZeroMutationsBelowY62InChunk() {
            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 0;
            testChunk.chunkZ = 12;
            testChunk.defaultMaterial = Material.DEEPSLATE;
            Chunk chunk = testChunk.createChunkProxy();

            generator.generateInChunk(chunk);

            for (BlockSetCall call : testChunk.setCalls) {
                assertTrue(call.y() >= 62, "Violación en subsuelo desde generateInChunk: Y=" + call.y());
            }
        }
    }

    @Nested
    @DisplayName("5. Despeje Completo desde Y=63 hasta Altura Máxima Exclusiva")
    class FullVerticalClearanceTests {

        @Test
        @DisplayName("Despeja todos los bloques desde Y=63 hasta maxHeight - 1 sin depender de HeightMap")
        void testFullClearanceUpToMaxHeightMinusOne() {
            testRegion.defaultMaterial = Material.STONE;
            generator.generateInRegion(worldInfo, 0, 10, limitedRegion);

            // Rango de despeje: 63 a 319 (320 - 1)
            Set<Integer> clearedHeights = new HashSet<>();
            for (BlockSetCall call : testRegion.setCalls) {
                if (call.material() == Material.AIR) {
                    clearedHeights.add(call.y());
                }
            }

            assertTrue(clearedHeights.contains(63));
            assertTrue(clearedHeights.contains(100));
            assertTrue(clearedHeights.contains(200));
            assertTrue(clearedHeights.contains(319));
            assertFalse(clearedHeights.contains(320), "No debe escribir a maxHeight (320)");
            assertEquals(319 - 63 + 1, clearedHeights.size(), "Debe despejar exactamente los 257 niveles verticales");
        }

        @Test
        @DisplayName("generateInChunk despeja desde Y=63 hasta maxHeight - 1")
        void testGenerateInChunkFullClearance() {
            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 10;
            testChunk.chunkZ = 0; // Camino Este
            testChunk.defaultMaterial = Material.STONE;
            Chunk chunk = testChunk.createChunkProxy();

            generator.generateInChunk(chunk);

            Set<Integer> clearedHeights = new HashSet<>();
            for (BlockSetCall call : testChunk.setCalls) {
                if (call.material() == Material.AIR) {
                    clearedHeights.add(call.y());
                }
            }

            assertTrue(clearedHeights.contains(63));
            assertTrue(clearedHeights.contains(319));
            assertFalse(clearedHeights.contains(320));
        }
    }

    @Nested
    @DisplayName("6. Ausencia de Doble Generación")
    class DoubleGenerationPreventionTests {

        @Test
        @DisplayName("Chunk poblado por BlockPopulator no es re-generado por ChunkGenerationListener")
        void testNoDoubleGenerationBetweenPopulatorAndListener() {
            RoadBlockPopulator populator = new RoadBlockPopulator("world", generator);
            ChunkGenerationListener listener = new ChunkGenerationListener("world", generator, populator);

            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 0;
            testChunk.chunkZ = 10;
            Chunk chunk = testChunk.createChunkProxy();

            // 1. Simular la fase de BlockPopulator (LimitedRegion)
            TestRegion reg = new TestRegion();
            populator.populate(worldInfo, new Random(), 0, 10, reg.createProxy());
            assertTrue(generator.isInFlightCompleted(0, 10));

            // 2. Simular ChunkLoadEvent(isNewChunk = true)
            ChunkLoadEvent event = new ChunkLoadEvent(chunk, true);
            listener.onChunkLoad(event);

            // El listener debió consumir el marcador in-flight y sellar el PDC SIN ejecutar mutaciones en testChunk
            assertTrue(generator.isChunkCompleted(chunk), "El PDC debe estar sellado");
            assertFalse(generator.isInFlightCompleted(0, 10), "In-flight consumido");
            assertTrue(testChunk.setCalls.isEmpty(), "Zero mutaciones adicionales: CERO doble generación");

            // 3. Segunda llamada a onChunkLoad (recarga de chunk)
            listener.onChunkLoad(event);
            assertTrue(testChunk.setCalls.isEmpty(), "Cero mutaciones en recarga");
        }

        @Test
        @DisplayName("Si el chunk ya está completado en PDC, generateInChunk retorna false de inmediato")
        void testGenerateInChunkSkipsCompletedChunk() {
            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 0;
            testChunk.chunkZ = 10;
            Chunk chunk = testChunk.createChunkProxy();

            generator.markChunkCompleted(chunk);
            assertTrue(generator.isChunkCompleted(chunk));

            boolean generated = generator.generateInChunk(chunk);
            assertFalse(generated, "Debe omitir la generación");
            assertTrue(testChunk.setCalls.isEmpty(), "Ningún bloque modificado");
        }
    }

    @Nested
    @DisplayName("7. Comportamiento ante un Error a Mitad del Procesamiento")
    class MidProcessingErrorBehaviorTests {

        @Test
        @DisplayName("Si generateInRegion falla a mitad del proceso, NO marca in-flight completion")
        void testFailureMidRegionDoesNotMarkCompletion() {
            TestRegion failingRegion = new TestRegion();
            failingRegion.failAtSetCount = 10; // Falla tras colocar 10 bloques

            assertThrows(RuntimeException.class, () ->
                    generator.generateInRegion(worldInfo, 0, 10, failingRegion.createProxy()));

            assertFalse(generator.isInFlightCompleted(0, 10),
                    "No debe registrarse como completado si la ejecución se interrumpió con excepción");
        }

        @Test
        @DisplayName("Si generateInChunk falla a mitad del proceso, NO marca PDC como COMPLETED")
        void testFailureMidChunkDoesNotMarkPdc() {
            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 0;
            testChunk.chunkZ = 10;
            testChunk.failAtSetCount = 5;
            Chunk chunk = testChunk.createChunkProxy();

            assertThrows(RuntimeException.class, () -> generator.generateInChunk(chunk));

            assertFalse(generator.isChunkCompleted(chunk),
                    "El PDC NO debe estar completado si la generación falló a mitad del proceso");
            assertFalse(generator.isInFlightCompleted(0, 10));
        }
    }

    @Nested
    @DisplayName("8. Ninguna Modificación Automática de Chunks Preexistentes")
    class PreExistingChunkProtectionTests {

        @Test
        @DisplayName("ChunkLoadEvent con isNewChunk = false es ignorado inmediatamente")
        void testExistingChunkIsUntouched() {
            RoadBlockPopulator populator = new RoadBlockPopulator("world", generator);
            ChunkGenerationListener listener = new ChunkGenerationListener("world", generator, populator);

            TestChunkWorld testChunk = new TestChunkWorld();
            testChunk.chunkX = 0;
            testChunk.chunkZ = 15;
            Chunk chunk = testChunk.createChunkProxy();

            ChunkLoadEvent event = new ChunkLoadEvent(chunk, false); // Chunk preexistente
            listener.onChunkLoad(event);

            assertTrue(testChunk.setCalls.isEmpty(), "Un chunk preexistente jamás debe ser alterado");
            assertFalse(generator.isChunkCompleted(chunk));
        }
    }

    @Nested
    @DisplayName("9. Registro Correcto del Populator y Ciclo de Vida en Paper")
    class PopulatorLifecycleAndRegistrationTests {

        @Test
        @DisplayName("registerPopulatorIfNeeded añade el populator de manera idempotente")
        void testIdempotentPopulatorRegistration() {
            RoadBlockPopulator populator = new RoadBlockPopulator("world", generator);
            ChunkGenerationListener listener = new ChunkGenerationListener("world", generator, populator);

            TestChunkWorld testChunk = new TestChunkWorld();
            World world = testChunk.createWorldProxy();

            assertEquals(0, testChunk.populators.size());

            listener.registerPopulatorIfNeeded(world);
            assertEquals(1, testChunk.populators.size());
            assertSame(populator, testChunk.populators.get(0));

            // Segunda llamada: no duplica
            listener.registerPopulatorIfNeeded(world);
            assertEquals(1, testChunk.populators.size());
        }

        @Test
        @DisplayName("WorldInitEvent y WorldLoadEvent registran el populator correctamente")
        void testWorldInitAndLoadEvents() {
            RoadBlockPopulator populator = new RoadBlockPopulator("world", generator);
            ChunkGenerationListener listener = new ChunkGenerationListener("world", generator, populator);

            TestChunkWorld testChunk = new TestChunkWorld();
            World world = testChunk.createWorldProxy();

            listener.onWorldInit(new WorldInitEvent(world));
            assertEquals(1, testChunk.populators.size());

            listener.onWorldLoad(new WorldLoadEvent(world));
            assertEquals(1, testChunk.populators.size(), "No debe duplicar al cargar");
        }
    }
}
