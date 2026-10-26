package com.infinityroads.generator;

import com.infinityroads.InfinityRoadsPlugin;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Random;

/**
 * Poblador de bloques integrado en el pipeline de generación de mundos de Paper/Purpur.
 * Se invoca durante la generación de nuevos chunks antes de su guardado final.
 */
public class RoadBlockPopulator extends BlockPopulator {

    private final InfinityRoadsPlugin plugin;
    private final String targetWorldName;
    private final RoadGenerator generator;

    public RoadBlockPopulator(@NotNull InfinityRoadsPlugin plugin, @NotNull RoadGenerator generator) {
        this.plugin = Objects.requireNonNull(plugin, "El plugin no puede ser nulo");
        this.targetWorldName = plugin.getTargetWorldName();
        this.generator = Objects.requireNonNull(generator, "El generador no puede ser nulo");
    }

    public RoadBlockPopulator(@NotNull String targetWorldName, @NotNull RoadGenerator generator) {
        this.plugin = null;
        this.targetWorldName = Objects.requireNonNull(targetWorldName, "El nombre del mundo no puede ser nulo");
        this.generator = Objects.requireNonNull(generator, "El generador no puede ser nulo");
    }

    /**
     * Población moderna en Paper con LimitedRegion (hilos de generación nativos).
     */
    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        if (plugin != null && !plugin.isEnabled()) {
            return;
        }

        if (!worldInfo.getName().equalsIgnoreCase(targetWorldName)) {
            return;
        }

        try {
            generator.generateInRegion(worldInfo, chunkX, chunkZ, limitedRegion);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, String.format(
                        "[Generator] Error crítico en RoadBlockPopulator.populate (LimitedRegion) [world: %s, chunk: [%d, %d]]: %s",
                        worldInfo.getName(), chunkX, chunkZ, t.getMessage()
                ), t);
            }
        }
    }

    /**
     * Población Bukkit clásica con instancia de Chunk (compatibilidad y fallbacks).
     */
    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        if (plugin != null && !plugin.isEnabled()) {
            return;
        }

        if (!world.getName().equalsIgnoreCase(targetWorldName)) {
            return;
        }

        try {
            generator.generateInChunk(chunk);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, String.format(
                        "[Generator] Error crítico en RoadBlockPopulator.populate (Chunk) [world: %s, chunk: [%d, %d]]: %s",
                        world.getName(), chunk.getX(), chunk.getZ(), t.getMessage()
                ), t);
            }
        }
    }
}
