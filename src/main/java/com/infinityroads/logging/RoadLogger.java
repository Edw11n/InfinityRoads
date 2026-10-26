package com.infinityroads.logging;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor centralizado de logging y observabilidad para InfinityRoads.
 *
 * POLÍTICA DE REGISTRO EN PRODUCCIÓN:
 * - INFO: Ciclo de vida, carga de configuración, operaciones administrativas, transiciones de lotes y resúmenes finales.
 * - WARNING: Errores recuperables, reintentos de chunks, configuración inválida con fallback, throttles de TPS y problemas temporales de E/S.
 * - SEVERE: Errores críticos, excepciones inesperadas, imposibilidad de persistencia y fallos definitivos tras agotar reintentos.
 * - DEBUG: Diagnóstico granular a bajo nivel (coordenadas, omisiones, intercepciones de protección y eventos de fluidos).
 *
 * REGLAS DE SEGURIDAD Y RENDIMIENTO:
 * 1. DEBUG está desactivado por defecto en config.yml.
 * 2. Cero spam en producción: nunca registrar cada bloque ni cada evento exitoso de protección/fluidos en INFO o WARNING.
 * 3. Formato consistente con identificador de subsistema ([Lifecycle], [Config], [Generator], [Protection], [Fluids], [Admin], [Batch], [Persistence], [Command]).
 * 4. Contexto completo en excepciones (coordenadas, intentos, mundo, causa y stack trace).
 */
public class RoadLogger {

    private final Logger logger;
    private volatile boolean debugEnabled;

    public RoadLogger(Logger logger, boolean debugEnabled) {
        this.logger = Objects.requireNonNull(logger, "logger no puede ser nulo");
        this.debugEnabled = debugEnabled;
    }

    public RoadLogger(Logger logger) {
        this(logger, false);
    }

    public Logger getUnderlyingLogger() {
        return logger;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    // =========================================================================
    // Nivel INFO: Hitos, transiciones mayores y resúmenes
    // =========================================================================

    public void info(String message) {
        logger.info(message);
    }

    public void info(String format, Object... args) {
        logger.info(String.format(format, args));
    }

    // =========================================================================
    // Nivel WARNING: Errores recuperables, reintentos y throttles
    // =========================================================================

    public void warning(String message) {
        logger.warning(message);
    }

    public void warning(String format, Object... args) {
        logger.warning(String.format(format, args));
    }

    public void warning(String message, Throwable throwable) {
        logger.log(Level.WARNING, message, throwable);
    }

    public void warning(String format, Throwable throwable, Object... args) {
        logger.log(Level.WARNING, String.format(format, args), throwable);
    }

    // =========================================================================
    // Nivel SEVERE: Errores críticos, corrupción o fallos irrecuperables
    // =========================================================================

    public void severe(String message) {
        logger.severe(message);
    }

    public void severe(String format, Object... args) {
        logger.severe(String.format(format, args));
    }

    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }

    public void severe(String format, Throwable throwable, Object... args) {
        logger.log(Level.SEVERE, String.format(format, args), throwable);
    }

    // =========================================================================
    // Nivel DEBUG: Diagnóstico granular (bajo demanda vía config.yml)
    // =========================================================================

    public void debug(String message) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + message);
        } else {
            logger.fine(message);
        }
    }

    public void debug(String format, Object... args) {
        if (debugEnabled) {
            logger.info("[DEBUG] " + String.format(format, args));
        } else {
            logger.fine(String.format(format, args));
        }
    }
}
