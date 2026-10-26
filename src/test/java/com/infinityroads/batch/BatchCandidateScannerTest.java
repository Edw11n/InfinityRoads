package com.infinityroads.batch;

import com.infinityroads.generator.RoadGenerator;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias de BatchCandidateScanner y generación de tokens")
class BatchCandidateScannerTest {

    private RoadGenerator roadGenerator;
    private World testWorld;
    private boolean chunk0_6Loaded = false;
    private boolean chunk0_6Completed = false;

    @BeforeEach
    void setUp() {
        NamespacedKey stateKey = new NamespacedKey("infinityroads", "road_state");
        roadGenerator = new RoadGenerator(stateKey);
        chunk0_6Loaded = false;
        chunk0_6Completed = false;

        PersistentDataContainer fakePdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "has" -> chunk0_6Completed;
                    case "get" -> chunk0_6Completed ? RoadGenerator.PDC_STATE_COMPLETED : null;
                    default -> null;
                }
        );

        Chunk fakeChunk = (Chunk) Proxy.newProxyInstance(
                Chunk.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> 0;
                    case "getZ" -> 6;
                    case "getPersistentDataContainer" -> fakePdc;
                    default -> null;
                }
        );

        testWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "isChunkLoaded" -> {
                        int x = (int) args[0];
                        int z = (int) args[1];
                        yield (x == 0 && z == 6) && chunk0_6Loaded;
                    }
                    case "getChunkAt" -> {
                        int x = (int) args[0];
                        int z = (int) args[1];
                        yield (x == 0 && z == 6) ? fakeChunk : null;
                    }
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Radio dentro del spawn (<= 100) devuelve 0 candidatos")
    void testSpawnExclusionRadius() {
        BatchCandidateScanner scanner = new BatchCandidateScanner(roadGenerator, 120);

        BatchCandidateScanner.PreviewResult result = scanner.preview(testWorld, 100);

        assertEquals(0, result.totalCandidates());
        assertTrue(result.candidates().isEmpty());
        assertNotNull(result.token());
        assertEquals(6, result.token().length());
    }

    @Test
    @DisplayName("Cálculo matemático exacto de candidatos para radio = 500 bloques")
    void testPreviewCandidateGeometry() {
        BatchCandidateScanner scanner = new BatchCandidateScanner(roadGenerator, 120);

        int radius = 500;
        BatchCandidateScanner.PreviewResult preview = scanner.preview(testWorld, radius);

        assertTrue(preview.totalCandidates() > 0);
        assertEquals(preview.candidates().size(), preview.totalCandidates());

        // Verificar que cada chunk candidato intersecta obligatoriamente la geometría de carreteras
        for (ChunkCoord coord : preview.candidates()) {
            boolean validNorth = (coord.x() == -1 || coord.x() == 0) && coord.z() <= -7 && coord.z() >= ((-radius) >> 4);
            boolean validSouth = (coord.x() == -1 || coord.x() == 0) && coord.z() >= 6 && coord.z() <= (radius >> 4);
            boolean validEast = (coord.z() == -1 || coord.z() == 0) && coord.x() >= 6 && coord.x() <= (radius >> 4);
            boolean validWest = (coord.z() == -1 || coord.z() == 0) && coord.x() <= -7 && coord.x() >= ((-radius) >> 4);

            assertTrue(
                    validNorth || validSouth || validEast || validWest,
                    "El chunk " + coord + " no coincide con ninguno de los 4 corredores cardinales autorizados"
            );
        }

        // Simetría cardinal: las carreteras cardinales tienen la misma longitud para un mismo radio
        assertEquals(preview.northCandidates(), preview.southCandidates());
        assertEquals(preview.eastCandidates(), preview.westCandidates());
        assertEquals(preview.northCandidates(), preview.eastCandidates());
    }

    @Test
    @DisplayName("Ciclo de vida del token de confirmación: emisión, consumo único y expiración")
    void testTokenLifecycle() {
        BatchCandidateScanner scanner = new BatchCandidateScanner(roadGenerator, 120);

        BatchCandidateScanner.PreviewResult preview = scanner.preview(testWorld, 1000);
        String token = preview.token();

        assertNotNull(token);
        assertEquals(6, token.length());

        // El token se puede inspeccionar con peekToken
        assertTrue(scanner.peekToken(token).isPresent());

        // Consumo del token (debe retornar el resultado y quedar invalidado para nuevos usos)
        Optional<BatchCandidateScanner.PreviewResult> consumed = scanner.consumeToken(token);
        assertTrue(consumed.isPresent());
        assertEquals(preview.totalCandidates(), consumed.get().totalCandidates());

        // Segundo intento de consumo debe fallar (token de un solo uso)
        assertFalse(scanner.consumeToken(token).isPresent());
    }

    @Test
    @DisplayName("Token expirado no es aceptado")
    void testExpiredToken() {
        // Validez de 0 segundos -> expira de inmediato
        BatchCandidateScanner scanner = new BatchCandidateScanner(roadGenerator, 0);

        BatchCandidateScanner.PreviewResult preview = scanner.preview(testWorld, 500);
        String token = preview.token();

        // Al intentar consumir, debe fallar por expiración
        Optional<BatchCandidateScanner.PreviewResult> consumed = scanner.consumeToken(token);
        assertFalse(consumed.isPresent());
    }

    @Test
    @DisplayName("Detección de chunks ya cargados en memoria y chunks con PDC completado")
    void testLoadedAndCompletedChunksDetection() {
        chunk0_6Loaded = true;
        chunk0_6Completed = true;

        BatchCandidateScanner scanner = new BatchCandidateScanner(roadGenerator, 120);
        BatchCandidateScanner.PreviewResult preview = scanner.preview(testWorld, 200);

        assertEquals(1, preview.loadedChunks());
        assertEquals(1, preview.alreadyCompletedChunks());
        assertEquals(preview.totalCandidates() - 1, preview.unverifiedChunks());
    }
}
