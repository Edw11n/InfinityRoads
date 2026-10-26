package com.infinityroads.protection;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gestor del modo de mantenimiento administrativo para InfinityRoads.
 *
 * REGLAS OBLIGATORIAS:
 * 1. Solo jugadores con el permiso 'infinityroads.maintenance' pueden activar la sesión (/ir admin on).
 * 2. La sesión dura un máximo de 15 minutos y expira automáticamente.
 * 3. Finaliza de inmediato al ejecutar '/ir admin off', al desconectarse el jugador o al reiniciar el servidor.
 * 4. El bypass solo evita la protección de InfinityRoads (no interfiere con WorldGuard ni otros plugins).
 * 5. La autorización se valida en cada evento protegido.
 * 6. Los operadores NO reciben bypass automático sin activar explícitamente el modo.
 * 7. Estructura ligera en memoria sin persistencia a disco ni tareas periódicas innecesarias.
 */
public class MaintenanceManager {

    public static final String MAINTENANCE_PERMISSION = "infinityroads.maintenance";
    public static final long DEFAULT_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final long sessionDurationMillis;

    public MaintenanceManager() {
        this(DEFAULT_DURATION_MILLIS);
    }

    public MaintenanceManager(long sessionDurationMillis) {
        this.sessionDurationMillis = sessionDurationMillis;
    }

    /**
     * Activa el modo de mantenimiento para un jugador si posee el permiso requerido.
     *
     * @param player Jugador que solicita la activación.
     * @return true si la sesión fue activada; false si no tiene permisos o el jugador es nulo.
     */
    public boolean activateSession(Player player) {
        if (player == null || !player.hasPermission(MAINTENANCE_PERMISSION)) {
            return false;
        }
        long expiresAt = System.currentTimeMillis() + sessionDurationMillis;
        activeSessions.put(player.getUniqueId(), expiresAt);
        return true;
    }

    /**
     * Desactiva el modo de mantenimiento para un jugador.
     *
     * @param player Jugador que desactiva la sesión.
     * @return true si había una sesión activa que fue removida; false en caso contrario.
     */
    public boolean deactivateSession(Player player) {
        if (player == null) {
            return false;
        }
        return activeSessions.remove(player.getUniqueId()) != null;
    }

    /**
     * Finaliza la sesión por UUID (usado al desconectarse el jugador).
     */
    public void endSession(UUID uuid) {
        if (uuid != null) {
            activeSessions.remove(uuid);
        }
    }

    /**
     * Verifica si un jugador cuenta con bypass activo y válido en el momento exacto del evento.
     * Comprueba permisos de forma estricta y expira la sesión si transcurrieron los 15 minutos.
     */
    public boolean hasActiveBypass(Player player) {
        if (player == null) {
            return false;
        }

        // Requiere permiso en todo momento; si fue revocado, se invalida la sesión
        if (!player.hasPermission(MAINTENANCE_PERMISSION)) {
            activeSessions.remove(player.getUniqueId());
            return false;
        }

        Long expiresAt = activeSessions.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }

        // Expiración perezosa sin necesidad de hilos periódicos
        if (System.currentTimeMillis() > expiresAt) {
            activeSessions.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    /**
     * Retorna los milisegundos restantes de la sesión de mantenimiento activa.
     */
    public long getRemainingMillis(UUID uuid) {
        if (uuid == null) return 0;
        Long expiresAt = activeSessions.get(uuid);
        if (expiresAt == null) return 0;

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            activeSessions.remove(uuid);
            return 0;
        }
        return remaining;
    }

    public boolean isSessionActive(UUID uuid) {
        if (uuid == null) return false;
        Long expiresAt = activeSessions.get(uuid);
        if (expiresAt == null) return false;
        if (System.currentTimeMillis() > expiresAt) {
            activeSessions.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearAllSessions() {
        activeSessions.clear();
    }
}
