package com.infinityroads.protection;

import com.infinityroads.geometry.RoadGeometry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio centralizado de consulta y validación de protección para InfinityRoads.
 * Integra la geometría espacial con el gestor de mantenimiento administrativo.
 */
public class RoadProtectionService {

    private volatile String targetWorldName;
    private final MaintenanceManager maintenanceManager;
    private final Map<UUID, Long> lastMessageTimestamp = new ConcurrentHashMap<>();

    public RoadProtectionService(String targetWorldName, MaintenanceManager maintenanceManager) {
        this.targetWorldName = Objects.requireNonNull(targetWorldName, "targetWorldName no puede ser nulo");
        this.maintenanceManager = Objects.requireNonNull(maintenanceManager, "maintenanceManager no puede ser nulo");
    }

    public String getTargetWorldName() {
        return targetWorldName;
    }

    public void setTargetWorldName(String targetWorldName) {
        this.targetWorldName = Objects.requireNonNull(targetWorldName, "targetWorldName no puede ser nulo");
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    /**
     * Determina si un mundo corresponde al mundo de supervivencia objetivo de InfinityRoads.
     */
    public boolean isTargetWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(targetWorldName);
    }

    /**
     * Comprueba si un bloque pertenece a la columna vertical protegida de cualquiera de las 4 carreteras.
     * Cubre desde world.getMinHeight() inclusive hasta world.getMaxHeight() exclusive.
     */
    public boolean isProtected(Block block) {
        if (block == null) {
            return false;
        }
        World world = block.getWorld();
        if (!isTargetWorld(world)) {
            return false;
        }
        return RoadGeometry.isProtectedVolume(
                block.getX(),
                block.getY(),
                block.getZ(),
                world.getMinHeight(),
                world.getMaxHeight()
        );
    }

    /**
     * Comprueba si una ubicación espacial pertenece a la columna vertical protegida.
     */
    public boolean isProtected(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        if (!isTargetWorld(world)) {
            return false;
        }
        return RoadGeometry.isProtectedVolume(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                world.getMinHeight(),
                world.getMaxHeight()
        );
    }

    /**
     * Comprueba si unas coordenadas específicas pertenecen a la columna vertical protegida en el mundo dado.
     */
    public boolean isProtected(World world, int x, int y, int z) {
        if (!isTargetWorld(world)) {
            return false;
        }
        return RoadGeometry.isProtectedVolume(x, y, z, world.getMinHeight(), world.getMaxHeight());
    }

    /**
     * Determina si un jugador tiene autorización para modificar un bloque específico.
     * Retorna true si el bloque no está protegido o si el jugador tiene una sesión de mantenimiento activa.
     */
    public boolean canPlayerModify(Player player, Block block) {
        if (!isProtected(block)) {
            return true;
        }
        return maintenanceManager.hasActiveBypass(player);
    }

    /**
     * Determina si un jugador tiene autorización para modificar una ubicación específica.
     */
    public boolean canPlayerModify(Player player, Location location) {
        if (!isProtected(location)) {
            return true;
        }
        return maintenanceManager.hasActiveBypass(player);
    }

    /**
     * Envía un mensaje informativo con limitación de frecuencia (máximo 1 mensaje cada 1.5s por jugador)
     * para advertir que la carretera está protegida.
     */
    public void notifyProtection(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastMessageTimestamp.get(player.getUniqueId());
        if (last == null || now - last > 1500) {
            lastMessageTimestamp.put(player.getUniqueId(), now);
            player.sendMessage("§c[InfinityRoads] Esta carretera está protegida contra modificaciones.");
        }
    }
}
