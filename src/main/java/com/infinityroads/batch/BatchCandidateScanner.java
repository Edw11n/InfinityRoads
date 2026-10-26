package com.infinityroads.batch;

import com.infinityroads.generator.RoadGenerator;
import com.infinityroads.geometry.RoadGeometry;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Escáner matemático O(N) para previsualización segura y generación de tokens de confirmación.
 * Calcula exclusivamente chunks sobre los corredores cardinales sin inspeccionar ni cargar chunks del mundo.
 */
public class BatchCandidateScanner {

    private static final String TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoadGenerator roadGenerator;
    private final long tokenValidityMillis;
    private final Map<String, PreviewResult> activeTokens = new ConcurrentHashMap<>();

    public BatchCandidateScanner(RoadGenerator roadGenerator, long tokenValiditySeconds) {
        this.roadGenerator = Objects.requireNonNull(roadGenerator, "roadGenerator no puede ser nulo");
        this.tokenValidityMillis = TimeUnit.SECONDS.toMillis(tokenValiditySeconds);
    }

    public BatchCandidateScanner(RoadGenerator roadGenerator) {
        this(roadGenerator, 120); // 2 minutos por defecto
    }

    /**
     * Resultado inmutable de la previsualización matemática.
     */
    public record PreviewResult(
            String token,
            String worldName,
            int radius,
            int totalCandidates,
            int northCandidates,
            int southCandidates,
            int eastCandidates,
            int westCandidates,
            int loadedChunks,
            int unverifiedChunks,
            int alreadyCompletedChunks,
            long createdAt,
            long expiresAt,
            List<ChunkCoord> candidates
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        public long getRemainingSeconds() {
            long remaining = expiresAt - System.currentTimeMillis();
            return remaining > 0 ? TimeUnit.MILLISECONDS.toSeconds(remaining) : 0;
        }
    }

    /**
     * Calcula en O(N) la lista de chunks candidatos que intersectan las carreteras cardinales
     * hasta el radio especificado en bloques y emite un token de confirmación temporal.
     *
     * @param world Mundo de Bukkit donde se realizará la operación.
     * @param radius Radio en bloques desde el origen (0, 0).
     * @return PreviewResult con métricas y token de confirmación.
     */
    public PreviewResult preview(World world, int radius) {
        Objects.requireNonNull(world, "world no puede ser nulo");
        cleanExpiredTokens();

        if (radius <= RoadGeometry.SPAWN_MAX_X) {
            String token = generateToken();
            long now = System.currentTimeMillis();
            PreviewResult emptyResult = new PreviewResult(
                    token, world.getName(), radius, 0, 0, 0, 0, 0, 0, 0, 0,
                    now, now + tokenValidityMillis, Collections.emptyList()
            );
            activeTokens.put(token, emptyResult);
            return emptyResult;
        }

        List<ChunkCoord> northList = new ArrayList<>();
        List<ChunkCoord> southList = new ArrayList<>();
        List<ChunkCoord> eastList = new ArrayList<>();
        List<ChunkCoord> westList = new ArrayList<>();

        // 1. Norte: X in [-5, 5] (cx in [-1, 0]), Z de -101 a -radius
        int northStartCz = (-101) >> 4; // -7
        int northEndCz = (-radius) >> 4;
        for (int cz = northStartCz; cz >= northEndCz; cz--) {
            northList.add(new ChunkCoord(-1, cz));
            northList.add(new ChunkCoord(0, cz));
        }

        // 2. Sur: X in [-5, 5] (cx in [-1, 0]), Z de 101 a +radius
        int southStartCz = 101 >> 4; // 6
        int southEndCz = radius >> 4;
        for (int cz = southStartCz; cz <= southEndCz; cz++) {
            southList.add(new ChunkCoord(-1, cz));
            southList.add(new ChunkCoord(0, cz));
        }

        // 3. Este: Z in [-5, 5] (cz in [-1, 0]), X de 101 a +radius
        int eastStartCx = 101 >> 4; // 6
        int eastEndCx = radius >> 4;
        for (int cx = eastStartCx; cx <= eastEndCx; cx++) {
            eastList.add(new ChunkCoord(cx, -1));
            eastList.add(new ChunkCoord(cx, 0));
        }

        // 4. Oeste: Z in [-5, 5] (cz in [-1, 0]), X de -101 a -radius
        int westStartCx = (-101) >> 4; // -7
        int westEndCx = (-radius) >> 4;
        for (int cx = westStartCx; cx >= westEndCx; cx--) {
            westList.add(new ChunkCoord(cx, -1));
            westList.add(new ChunkCoord(cx, 0));
        }

        List<ChunkCoord> allCandidates = new ArrayList<>(
                northList.size() + southList.size() + eastList.size() + westList.size()
        );
        allCandidates.addAll(northList);
        allCandidates.addAll(southList);
        allCandidates.addAll(eastList);
        allCandidates.addAll(westList);

        int loadedCount = 0;
        int unverifiedCount = 0;
        int completedCount = 0;

        for (ChunkCoord coord : allCandidates) {
            if (world.isChunkLoaded(coord.x(), coord.z())) {
                loadedCount++;
                Chunk chunk = world.getChunkAt(coord.x(), coord.z());
                if (roadGenerator.isChunkCompleted(chunk)) {
                    completedCount++;
                }
            } else {
                unverifiedCount++;
            }
        }

        String token = generateToken();
        long now = System.currentTimeMillis();
        long expiresAt = now + tokenValidityMillis;

        PreviewResult result = new PreviewResult(
                token,
                world.getName(),
                radius,
                allCandidates.size(),
                northList.size(),
                southList.size(),
                eastList.size(),
                westList.size(),
                loadedCount,
                unverifiedCount,
                completedCount,
                now,
                expiresAt,
                Collections.unmodifiableList(allCandidates)
        );

        activeTokens.put(token, result);
        return result;
    }

    /**
     * Consume y valida un token de confirmación.
     * Si el token existe y no ha expirado, se retira de la lista activa y se retorna.
     *
     * @param token Token alfanumérico suministrado por el usuario.
     * @return Optional con el PreviewResult o Optional.empty() si es inválido o expiró.
     */
    public Optional<PreviewResult> consumeToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        cleanExpiredTokens();
        PreviewResult result = activeTokens.remove(token.trim().toUpperCase());
        if (result == null || result.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public Optional<PreviewResult> peekToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        PreviewResult result = activeTokens.get(token.trim().toUpperCase());
        if (result == null || result.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }
}
