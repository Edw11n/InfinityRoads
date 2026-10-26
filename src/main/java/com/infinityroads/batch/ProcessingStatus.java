package com.infinityroads.batch;

/**
 * Estado de procesamiento de un chunk individual dentro de la cola por lotes.
 */
public enum ProcessingStatus {
    /**
     * En espera de ser procesado.
     */
    PENDING,

    /**
     * En proceso activo de carga o generación.
     * En caso de caída del servidor o reinicio, se restablece a PENDING.
     */
    PROCESSING,

    /**
     * Procesado exitosamente (pavimento generado a Y=62, despeje vertical aplicado y sellado con PDC).
     */
    COMPLETED,

    /**
     * Omitido porque ya estaba completado previamente (PDC COMPLETED) sin requerir modificaciones.
     */
    SKIPPED,

    /**
     * Falló tras alcanzar el límite máximo de reintentos permitidos.
     */
    FAILED
}
