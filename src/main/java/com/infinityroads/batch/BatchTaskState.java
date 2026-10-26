package com.infinityroads.batch;

/**
 * Estado global del procesador por lotes de chunks de InfinityRoads.
 */
public enum BatchTaskState {
    /**
     * Sin tarea activa o cola vacía.
     */
    IDLE,

    /**
     * Procesando activamente chunks en segundo plano / hilo principal según ventana deslizante.
     */
    RUNNING,

    /**
     * Pausado manualmente por un administrador, por TPS bajo, o recuperado tras reinicio del servidor.
     * Nunca se reanuda automáticamente sin intervención administrativa.
     */
    PAUSED,

    /**
     * Cancelado explícitamente por un administrador.
     */
    CANCELLED,

    /**
     * Todos los chunks en la cola han sido completados o clasificados como omitidos.
     */
    COMPLETED
}
