# InfinityRoads 🛣️

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-brightgreen.svg)](https://papermc.io)
[![Paper API](https://img.shields.io/badge/Paper%20API-26.3.build.209--stable-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://adoptium.net)
[![Tests](https://img.shields.io/badge/Tests-283%20Passed-success.svg)](#testing)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**InfinityRoads** es un plugin de alto rendimiento para servidores Minecraft **Paper / Purpur (26.3)** diseñado para generar, proteger y gestionar cuatro carreteras cardinales infinitas que parten desde la zona de exclusión del spawn central hacia el infinito.

---

## 🌟 Características Principales

- **Geometría Inmutable:** Cuatro carreteras cardinales de 11 bloques de ancho (5 a cada lado del eje central), con superficie de pavimento a cota fija **Y=62** y gálibo vertical a cielo abierto.
- **Zona de Exclusión del Spawn:** Cuadrado inclusivo $[-100, 100]$ en $X$ y $Z$ completamente intocable por el generador y libre de restricciones del plugin.
- **Protección Vertical Absoluta:** Cobertura integral de la columna vertical válida del mundo $[world.getMinHeight(), world.getMaxHeight())$ frente a rotura, colocación, cubos, pistones, explosiones, mechones y fuego.
- **Invarianza Subterránea Estricta:** **Cero mutaciones** en bloques con $Y < 62$. El subsuelo, cuevas naturales, minerales y redes de túneles permanecen 100% intactos.
- **Contención Reactiva de Fluidos:** Impide que agua o lava exteriores penetren en el corredor protegido y bloquea derrames hacia el exterior a partir de $Y \ge 62$. Los fluidos bajo $Y=62$ y fuera de la carretera mantienen su simulación Vanilla sin alteraciones.
- **Seguridad en Explosiones:** Creepers, TNT y cristales explotan con efectos visuales y sonoros completos, pero los bloques protegidos no sufren daños y los jugadores sobre la vía son inmunes al daño explosivo.
- **Sesiones de Mantenimiento Administrativo:** Bypass seguro con `/ir admin on` protegido por permiso, auto-expiración a los 15 minutos desde su activación y registro en `audit.log`.
- **Procesamiento de Chunks Preexistentes (Fase 4):** Procesamiento asíncrono, controlado, seguro, reanudable e idempotente mediante cola persistente (`queue.json`), control de TPS ($> 19.0$), ventana deslizante y token de confirmación de 2 minutos.
- **Observabilidad de Producción (Fase 5):** Logging estructurado por subsistemas (`[Lifecycle]`, `[Generator]`, `[Protection]`, `[Fluids]`, `[Batch]`, `[Admin]`) con modo DEBUG discreto para evitar spam en consola.

---

## 🧭 Especificación de Carreteras

| Corredor | Dirección | Material del Pavimento | Rango Transversal | Inicio |
|---|:---:|---|:---:|:---:|
| **Norte** | `NORTH` ($-Z$) | `YELLOW_CONCRETE` | $X \in [-5, 5]$ | $Z \le -101$ |
| **Sur** | `SOUTH` ($+Z$) | `BLUE_CONCRETE` | $X \in [-5, 5]$ | $Z \ge 101$ |
| **Este** | `EAST` ($+X$) | `RED_CONCRETE` | $Z \in [-5, 5]$ | $X \ge 101$ |
| **Oeste** | `WEST` ($-X$) | `GREEN_CONCRETE` | $Z \in [-5, 5]$ | $X \le -101$ |

- **Cota del Pavimento:** $Y = 62$ (los pies del jugador transitan en $Y = 63$).
- **Gálibo Mínimo:** 5 bloques despejados ($Y = 63$ hasta $Y = 67$).
- **Despeje Superior:** Cielo abierto hasta la cota máxima del mundo.

---

## 🛠️ Comandos y Permisos

### Comandos Administrativos (`/ir` o `/infinityroads`)

| Comando | Descripción | Permiso |
|---|---|---|
| `/ir admin on` | Activa la sesión de mantenimiento administrativo (15 min). | `infinityroads.maintenance` |
| `/ir admin off` | Finaliza la sesión activa de mantenimiento. | `infinityroads.maintenance` |
| `/ir status` | Muestra el estado del plugin, configuración y sesión actual. | `infinityroads.admin` |
| `/ir reload` | Recarga la configuración (`config.yml`) en caliente. | `infinityroads.reload` |
| `/ir chunks scan [radio]` | Escanea chunks preexistentes y genera un token seguro. | `infinityroads.chunks` |
| `/ir chunks start <token>` | Inicia el procesamiento por lotes con el token generado. | `infinityroads.chunks` |
| `/ir chunks pause` | Pausa el procesamiento de chunks en curso. | `infinityroads.chunks` |
| `/ir chunks resume` | Reanuda el procesamiento pausado respetando el estado. | `infinityroads.chunks` |
| `/ir chunks cancel` | Cancela el procesamiento y limpia la cola activa. | `infinityroads.chunks` |
| `/ir chunks status` | Muestra el progreso detallado (pendientes, completados, omitidos). | `infinityroads.chunks` |

### Árbol de Permisos

```yaml
infinityroads.admin:
  description: Acceso administrativo total a InfinityRoads
  default: op
  children:
    infinityroads.maintenance: true
    infinityroads.bypass: true
    infinityroads.reload: true
    infinityroads.chunks: true
```

---

## ⚙️ Configuración (`config.yml`)

```yaml
# Mundo de supervivencia objetivo
world-name: "world"
enabled: true

# Observabilidad y registro en producción
logging:
  debug: false

# Control del Spawn y Zona de Exclusión
spawn:
  center-x: 0
  center-z: 0
  protection-radius: 100

# Parámetros geométricos inmutables
geometry:
  fixed-y-level: 62
  pavement-width: 11
  min-clearance-height: 5

# Contención de fluidos
fluids:
  containment:
    block-flow-into-corridor: true
    block-buckets-into-corridor: true
    block-dispensers-into-corridor: true
    safe-deferred-sanitization: true

# Mantenimiento administrativo
maintenance:
  auto-expiration-minutes: 15
  audit-log: true

# Control de rendimiento por tick
performance:
  max-ms-per-tick: 3.0
  max-blocks-per-tick: 512
  tps-throttle-threshold: 19.0
  auto-process-existing-chunks: false

# Procesamiento por lotes de chunks preexistentes (Fase 4)
batch:
  default-radius: 1000
  sliding-window: 1
  tps-threshold: 19.0
  auto-unload-chunk: true
  chunk-delay-ticks: 1
  max-retries: 3
  token-validity-seconds: 120
```

---

## 🧪 Pruebas y Certificación de Calidad

El proyecto cuenta con una batería de **283 pruebas unitarias y de integración** desarrolladas con JUnit 5 y Mockito, que validan:

- Determinismo geométrico y exclusión del spawn.
- Aislamiento e invariabilidad de cotas $Y < 62$.
- Idempotencia del generador en `LimitedRegion` y `Chunk`.
- Protección reactiva completa de bloques y contención de fluidos.
- Persistencia y reanudabilidad de colas en JSON ante reinicios del servidor.
- Ciclo de vida y expiración de sesiones de mantenimiento.

Para ejecutar la suite de pruebas:

```bash
mvn test
```

---

## 📦 Compilación y Despliegue

### Requisitos de Entorno
- **Java Development Kit (JDK):** Versión 25 (OpenJDK / Eclipse Adoptium LTS)
- **Herramienta de Compilación:** Apache Maven 3.9+
- **Servidor:** Paper / Purpur 26.3 (API `26.3.build.209-stable`)

### Pasos de Compilación

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Edw11n/InfinityRoads.git
   cd InfinityRoads
   ```

2. Compilar y empaquetar:
   ```bash
   mvn clean package
   ```

3. El artefacto final listo para producción se encontrará en:
   ```text
   target/InfinityRoads.jar
   ```

4. Copiar `InfinityRoads.jar` a la carpeta `plugins/` del servidor Paper y reiniciar.

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia **MIT**. Consulta el archivo `LICENSE` para más detalles.
