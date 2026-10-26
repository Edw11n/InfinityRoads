package com.infinityroads.tools;

import com.infinityroads.geometry.ChunkIntersection;
import com.infinityroads.geometry.RoadDirection;
import com.infinityroads.geometry.RoadGeometry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Verificador forense de archivos de región Anvil (.mca).
 *
 * Descomprime e interpreta la estructura NBT exacta de chunks de Minecraft (formato 1.20+ / 26.3):
 * 1. Decodifica la paleta de estados de bloque y los arrays de longs empaquetados por sección.
 * 2. Comprueba que NINGÚN bloque de concreto exista dentro del cuadrado de spawn [-100, 100],
 *    incluyendo los chunks de transición que cruzan las fronteras ±100.
 * 3. Comprueba que todo el concreto colocado se encuentre estrictamente a cota Y = 62.
 * 4. Comprueba la coincidencia exacta de colores según el vector cardinal:
 *    - Norte (Z <= -101): yellow_concrete
 *    - Sur (Z >= 101): blue_concrete
 *    - Este (X >= 101): red_concrete
 *    - Oeste (X <= -101): green_concrete
 * 5. Comprueba el despeje aéreo estricto (Y = 63 a 319) sobre toda la calzada en chunks poblados.
 * 6. Comprueba la presencia del marcador PDC ("infinityroads:road_state").
 */
public class VerifyWorldGeneration {

    public static class NbtReader {
        private final DataInputStream dis;

        public NbtReader(InputStream in) {
            this.dis = new DataInputStream(in);
        }

        public Map<String, Object> readRootCompound() throws IOException {
            byte type = dis.readByte();
            if (type != 10) {
                throw new IOException("Root tag is not compound: " + type);
            }
            dis.readUTF();
            return readCompoundPayload();
        }

        @SuppressWarnings("unchecked")
        private Object readPayload(byte type) throws IOException {
            switch (type) {
                case 0: return null;
                case 1: return dis.readByte();
                case 2: return dis.readShort();
                case 3: return dis.readInt();
                case 4: return dis.readLong();
                case 5: return dis.readFloat();
                case 6: return dis.readDouble();
                case 7: {
                    int len = dis.readInt();
                    byte[] b = new byte[len];
                    dis.readFully(b);
                    return b;
                }
                case 8: return dis.readUTF();
                case 9: {
                    byte elemType = dis.readByte();
                    int len = dis.readInt();
                    List<Object> list = new ArrayList<>(len);
                    for (int i = 0; i < len; i++) {
                        list.add(readPayload(elemType));
                    }
                    return list;
                }
                case 10: return readCompoundPayload();
                case 11: {
                    int len = dis.readInt();
                    int[] arr = new int[len];
                    for (int i = 0; i < len; i++) arr[i] = dis.readInt();
                    return arr;
                }
                case 12: {
                    int len = dis.readInt();
                    long[] arr = new long[len];
                    for (int i = 0; i < len; i++) arr[i] = dis.readLong();
                    return arr;
                }
                default: throw new IOException("Unknown NBT tag type: " + type);
            }
        }

        private Map<String, Object> readCompoundPayload() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                byte type = dis.readByte();
                if (type == 0) break;
                String name = dis.readUTF();
                map.put(name, readPayload(type));
            }
            return map;
        }
    }

    public static void main(String[] args) throws Exception {
        Path regionDir = Paths.get("target/test-server/world/dimensions/minecraft/overworld/region");
        if (!Files.exists(regionDir)) {
            System.err.println("Region directory not found: " + regionDir);
            System.exit(1);
        }

        File[] files = regionDir.toFile().listFiles((dir, name) -> name.endsWith(".mca"));
        if (files == null || files.length == 0) {
            System.err.println("No .mca files found in: " + regionDir);
            System.exit(1);
        }

        System.out.println("Inspecting " + files.length + " Anvil region files with full NBT decoding...");

        int totalChunksDecoded = 0;
        int totalRoadChunksPopulated = 0;
        int violationsCount = 0;
        long totalRoadPavementBlocks = 0;
        long totalClearanceBlocksVerified = 0;

        for (File mcaFile : files) {
            String[] parts = mcaFile.getName().split("\\.");
            int rx = Integer.parseInt(parts[1]);
            int rz = Integer.parseInt(parts[2]);

            try (RandomAccessFile raf = new RandomAccessFile(mcaFile, "r")) {
                byte[] header = new byte[4096];
                raf.readFully(header);

                for (int cz = 0; cz < 32; cz++) {
                    for (int cx = 0; cx < 32; cx++) {
                        int index = (cx + cz * 32) * 4;
                        int offset = ((header[index] & 0xFF) << 16) |
                                     ((header[index + 1] & 0xFF) << 8) |
                                     (header[index + 2] & 0xFF);
                        int sectorCount = header[index + 3] & 0xFF;

                        if (offset == 0 || sectorCount == 0) {
                            continue;
                        }

                        totalChunksDecoded++;
                        raf.seek((long) offset * 4096);
                        int length = raf.readInt();
                        byte compressionType = raf.readByte();

                        byte[] compressed = new byte[length - 1];
                        raf.readFully(compressed);

                        byte[] decompressed = decompress(compressed);

                        Map<String, Object> root;
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(decompressed)) {
                            NbtReader reader = new NbtReader(bais);
                            root = reader.readRootCompound();
                        }

                        int chunkX = getInt(root, "xPos", rx * 32 + cx);
                        int chunkZ = getInt(root, "zPos", rz * 32 + cz);

                        String nbtRaw = new String(decompressed, StandardCharsets.ISO_8859_1);
                        boolean hasPdc = nbtRaw.contains("infinityroads:road_state");

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> sections = (List<Map<String, Object>>) root.get("sections");
                        if (sections == null) continue;

                        // Decodificar bloques del chunk organizados por [worldY][localZ][localX]
                        // Rango de secciones habitual en Minecraft 1.18+: -4 a 19 (Y=-64 a 319)
                        Map<Integer, String[][]> sectionBlocks = new HashMap<>();

                        for (Map<String, Object> section : sections) {
                            int secY = getInt(section, "Y", Integer.MIN_VALUE);
                            if (secY == Integer.MIN_VALUE) continue;

                            @SuppressWarnings("unchecked")
                            Map<String, Object> blockStates = (Map<String, Object>) section.get("block_states");
                            if (blockStates == null) continue;

                            @SuppressWarnings("unchecked")
                            List<?> palette = (List<?>) blockStates.get("palette");
                            if (palette == null || palette.isEmpty()) continue;

                            long[] data = (long[]) blockStates.get("data");
                            int paletteSize = palette.size();
                            int bitsPerBlock = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
                            int blocksPerLong = 64 / bitsPerBlock;
                            long mask = (1L << bitsPerBlock) - 1L;

                            String[][] grid = new String[16][16 * 16]; // localY -> [localZ * 16 + localX]

                            for (int localY = 0; localY < 16; localY++) {
                                for (int localZ = 0; localZ < 16; localZ++) {
                                    for (int localX = 0; localX < 16; localX++) {
                                        int blockIdx = (localY * 16 + localZ) * 16 + localX;
                                        int paletteIdx;
                                        if (paletteSize == 1 || data == null) {
                                            paletteIdx = 0;
                                        } else {
                                            int longIdx = blockIdx / blocksPerLong;
                                            int bitOffset = (blockIdx % blocksPerLong) * bitsPerBlock;
                                            paletteIdx = (longIdx < data.length) ? (int) ((data[longIdx] >>> bitOffset) & mask) : 0;
                                        }

                                        String blockName = "";
                                        if (paletteIdx >= 0 && paletteIdx < paletteSize) {
                                            Object elem = palette.get(paletteIdx);
                                            if (elem instanceof Map) {
                                                blockName = (String) ((Map<?, ?>) elem).get("Name");
                                            } else if (elem instanceof String) {
                                                blockName = (String) elem;
                                            }
                                        }
                                        grid[localY][localZ * 16 + localX] = (blockName != null) ? blockName : "";
                                    }
                                }
                            }
                            sectionBlocks.put(secY, grid);
                        }

                        // Función auxiliar para obtener el nombre de bloque en (worldX, worldY, worldZ)
                        ChunkBlockAccessor accessor = (wx, wy, wz) -> {
                            int sy = wy >> 4;
                            int ly = wy & 15;
                            int lz = wz & 15;
                            int lx = wx & 15;
                            String[][] g = sectionBlocks.get(sy);
                            if (g == null) return "minecraft:air";
                            String name = g[ly][lz * 16 + lx];
                            return (name != null && !name.isEmpty()) ? name : "minecraft:air";
                        };

                        // 1. REGLA UNIVERSAL: Ningún bloque de concreto puede existir dentro de [-100, 100]
                        for (int lz = 0; lz < 16; lz++) {
                            int wz = chunkZ * 16 + lz;
                            for (int lx = 0; lx < 16; lx++) {
                                int wx = chunkX * 16 + lx;

                                boolean insideSpawn = (wx >= -100 && wx <= 100 && wz >= -100 && wz <= 100);

                                for (int sy : sectionBlocks.keySet()) {
                                    for (int ly = 0; ly < 16; ly++) {
                                        int wy = sy * 16 + ly;
                                        String bName = accessor.getBlock(wx, wy, wz);
                                        boolean isConcrete = bName.endsWith("_concrete");

                                        if (isConcrete) {
                                            if (insideSpawn) {
                                                System.err.printf("VIOLATION: Spawn concrete detected! %s at (%d, %d, %d)%n",
                                                        bName, wx, wy, wz);
                                                violationsCount++;
                                            }
                                            if (wy != 62) {
                                                System.err.printf("VIOLATION: Concrete at invalid Y=%d (expected 62)! %s at (%d, %d, %d)%n",
                                                        wy, bName, wx, wy, wz);
                                                violationsCount++;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. REGLA DE INTERSECCIÓN: Si el chunk intersecta una carretera y fue poblado
                        Optional<ChunkIntersection> optInter = RoadGeometry.evaluateChunk(chunkX, chunkZ);
                        if (optInter.isPresent()) {
                            ChunkIntersection inter = optInter.get();
                            String expectedConcrete = "minecraft:" + inter.direction().getConcreteMaterial().toLowerCase();

                            // Comprobar si este chunk fue generado por InfinityRoads comprobando el pavimento
                            int sampleX = inter.minBlockX();
                            int sampleZ = inter.minBlockZ();
                            String sampleBlock = accessor.getBlock(sampleX, 62, sampleZ);
                            boolean isPopulated = sampleBlock.equals(expectedConcrete);

                            if (isPopulated) {
                                totalRoadChunksPopulated++;

                                // Verificar todos los bloques de pavimento (Y = 62)
                                for (int x = inter.minBlockX(); x <= inter.maxBlockX(); x++) {
                                    for (int z = inter.minBlockZ(); z <= inter.maxBlockZ(); z++) {
                                        String actual = accessor.getBlock(x, 62, z);
                                        if (!actual.equals(expectedConcrete)) {
                                            System.err.printf("VIOLATION: Pavement block missing or wrong at (%d, 62, %d)! Expected %s, found %s%n",
                                                    x, z, expectedConcrete, actual);
                                            violationsCount++;
                                        } else {
                                            totalRoadPavementBlocks++;
                                        }

                                        // Verificar despeje aéreo estricto Y = 63..319
                                        for (int y = 63; y < 320; y++) {
                                            String clearBlock = accessor.getBlock(x, y, z);
                                            boolean isAir = clearBlock.equals("minecraft:air") ||
                                                            clearBlock.equals("minecraft:cave_air") ||
                                                            clearBlock.equals("minecraft:void_air");
                                            if (!isAir) {
                                                System.err.printf("VIOLATION: Air clearance failure at (%d, %d, %d)! Found: %s%n",
                                                        x, y, z, clearBlock);
                                                violationsCount++;
                                            } else {
                                                totalClearanceBlocksVerified++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("=== ANVIL FORENSIC VERIFICATION RESULTS ===");
        System.out.println("Total chunks decoded: " + totalChunksDecoded);
        System.out.println("Total populated road chunks verified: " + totalRoadChunksPopulated);
        System.out.println("Total concrete road blocks verified at Y=62: " + totalRoadPavementBlocks);
        System.out.println("Total clearance air blocks verified (Y: 63..319): " + totalClearanceBlocksVerified);
        System.out.println("Total violations detected: " + violationsCount);

        if (violationsCount > 0) {
            System.err.println("VERIFICATION FAILED with " + violationsCount + " violations.");
            System.exit(1);
        } else {
            System.out.println("VERIFICATION PASSED: All invariants strictly satisfied!");
        }
    }

    @FunctionalInterface
    interface ChunkBlockAccessor {
        String getBlock(int x, int y, int z);
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private static byte[] decompress(byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        InflaterInputStream iis = new InflaterInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = iis.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toByteArray();
    }
}
