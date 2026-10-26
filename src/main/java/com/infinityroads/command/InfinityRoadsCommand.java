package com.infinityroads.command;

import com.infinityroads.InfinityRoadsPlugin;
import com.infinityroads.batch.BatchCandidateScanner;
import com.infinityroads.batch.BatchChunkProcessor;
import com.infinityroads.batch.BatchQueueData;
import com.infinityroads.batch.BatchTaskState;
import com.infinityroads.geometry.RoadGeometry;
import com.infinityroads.protection.MaintenanceManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Comando administrativo principal de InfinityRoads (/ir y /infinityroads).
 * Gestiona el modo de mantenimiento individual (/ir admin on/off), estado, recarga
 * y procesamiento por lotes de chunks preexistentes (/ir chunks ...).
 */
public class InfinityRoadsCommand implements CommandExecutor, TabCompleter {

    public static final String CHUNKS_PERMISSION = "infinityroads.chunks";

    private final InfinityRoadsPlugin plugin;
    private final MaintenanceManager maintenanceManager;
    private final Supplier<String> targetWorldSupplier;
    private final Runnable reloadAction;
    private final BatchCandidateScanner scanner;
    private final BatchChunkProcessor processor;

    public InfinityRoadsCommand(
            @Nullable InfinityRoadsPlugin plugin,
            @NotNull MaintenanceManager maintenanceManager,
            @NotNull Supplier<String> targetWorldSupplier,
            @NotNull Runnable reloadAction,
            @Nullable BatchCandidateScanner scanner,
            @Nullable BatchChunkProcessor processor
    ) {
        this.plugin = plugin;
        this.maintenanceManager = Objects.requireNonNull(maintenanceManager, "maintenanceManager no puede ser nulo");
        this.targetWorldSupplier = Objects.requireNonNull(targetWorldSupplier, "targetWorldSupplier no puede ser nulo");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction no puede ser nulo");
        this.scanner = scanner;
        this.processor = processor;
    }

    public InfinityRoadsCommand(
            @Nullable InfinityRoadsPlugin plugin,
            @NotNull MaintenanceManager maintenanceManager,
            @NotNull Supplier<String> targetWorldSupplier,
            @NotNull Runnable reloadAction
    ) {
        this(plugin, maintenanceManager, targetWorldSupplier, reloadAction, null, null);
    }

    public InfinityRoadsCommand(
            @NotNull InfinityRoadsPlugin plugin,
            @NotNull MaintenanceManager maintenanceManager,
            @Nullable BatchCandidateScanner scanner,
            @Nullable BatchChunkProcessor processor
    ) {
        this(
                Objects.requireNonNull(plugin, "plugin no puede ser nulo"),
                maintenanceManager,
                plugin::getTargetWorldName,
                plugin::loadConfiguration,
                scanner,
                processor
        );
    }

    public InfinityRoadsCommand(@NotNull InfinityRoadsPlugin plugin, @NotNull MaintenanceManager maintenanceManager) {
        this(
                Objects.requireNonNull(plugin, "plugin no puede ser nulo"),
                maintenanceManager,
                plugin::getTargetWorldName,
                plugin::loadConfiguration,
                plugin.getBatchScanner(),
                plugin.getBatchProcessor()
        );
    }

    public InfinityRoadsCommand(@NotNull MaintenanceManager maintenanceManager, @NotNull String targetWorldName, @NotNull Runnable reloadAction) {
        this(null, maintenanceManager, () -> targetWorldName, reloadAction, null, null);
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "admin" -> {
                    return handleAdminCommand(sender, args);
                }
                case "status" -> {
                    return handleStatusCommand(sender);
                }
                case "reload" -> {
                    return handleReloadCommand(sender);
                }
                case "chunks" -> {
                    return handleChunksCommand(sender, args, label);
                }
                default -> {
                    sender.sendMessage("§cSubcomando desconocido: " + args[0]);
                    sendHelp(sender, label);
                    return true;
                }
            }
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, String.format(
                        "[Command] Error inesperado al ejecutar comando /%s %s por %s: %s",
                        label, String.join(" ", args), sender.getName(), t.getMessage()
                ), t);
            }
            sender.sendMessage("§cOcurrió un error interno al ejecutar el comando. Revisa la consola.");
            return true;
        }
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEl comando /ir admin solo puede ser ejecutado por jugadores en el juego.");
            return true;
        }

        if (!player.hasPermission(MaintenanceManager.MAINTENANCE_PERMISSION)) {
            player.sendMessage("§cNo tienes permiso para gestionar el modo mantenimiento (" + MaintenanceManager.MAINTENANCE_PERMISSION + ").");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§cUso correcto: /ir admin <on|off>");
            return true;
        }

        String mode = args[1].toLowerCase();
        if ("on".equals(mode)) {
            boolean activated = maintenanceManager.activateSession(player);
            if (activated) {
                if (plugin != null) {
                    plugin.getLogger().info(String.format("[Admin] Jugador %s activó el modo mantenimiento (expira en 15m).", player.getName()));
                }
                player.sendMessage("§a[InfinityRoads] Modo mantenimiento ACTIVADO (duración máxima: 15 minutos).");
                player.sendMessage("§7Ahora puedes modificar bloques y reparar la carretera. La protección se restaurará al salir o usar /ir admin off.");
            } else {
                player.sendMessage("§cNo se pudo activar el modo mantenimiento. Verifica tus permisos.");
            }
            return true;
        } else if ("off".equals(mode)) {
            boolean deactivated = maintenanceManager.deactivateSession(player);
            if (deactivated) {
                if (plugin != null) {
                    plugin.getLogger().info(String.format("[Admin] Jugador %s desactivó el modo mantenimiento.", player.getName()));
                }
                player.sendMessage("§e[InfinityRoads] Modo mantenimiento DESACTIVADO.");
                player.sendMessage("§7La protección absoluta de la carretera ha sido restaurada para tu usuario.");
            } else {
                player.sendMessage("§7No tenías una sesión de mantenimiento activa.");
            }
            return true;
        } else {
            player.sendMessage("§cOpción inválida '" + args[1] + "'. Usa /ir admin on o /ir admin off.");
            return true;
        }
    }

    private boolean handleStatusCommand(CommandSender sender) {
        sender.sendMessage("§6=== Estado de InfinityRoads ===");
        sender.sendMessage("§7Mundo objetivo: §f" + targetWorldSupplier.get());
        sender.sendMessage("§7Generador activo: §aSí (Y=62, ancho 11 bloques)");

        if (processor != null) {
            sender.sendMessage("§7Procesador por lotes: §f" + processor.getState());
        }

        if (sender instanceof Player player) {
            boolean active = maintenanceManager.hasActiveBypass(player);
            if (active) {
                long remainingMs = maintenanceManager.getRemainingMillis(player.getUniqueId());
                long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
                long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60;
                sender.sendMessage("§7Tu sesión de mantenimiento: §aACTIVA §7(Restante: " + minutes + "m " + seconds + "s)");
            } else {
                sender.sendMessage("§7Tu sesión de mantenimiento: §cINACTIVA");
            }
        }
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("infinityroads.reload")) {
            sender.sendMessage("§cNo tienes permiso para recargar la configuración.");
            return true;
        }

        reloadAction.run();
        if (plugin != null) {
            plugin.getLogger().info(String.format("[Admin] Configuración recargada por %s.", sender.getName()));
        }
        sender.sendMessage("§a[InfinityRoads] Configuración recargada exitosamente.");
        return true;
    }

    private boolean handleChunksCommand(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(CHUNKS_PERMISSION)) {
            sender.sendMessage("§cNo tienes permiso para gestionar chunks (" + CHUNKS_PERMISSION + ").");
            return true;
        }

        if (scanner == null || processor == null) {
            sender.sendMessage("§cEl sistema de procesamiento de chunks no está disponible.");
            return true;
        }

        if (args.length < 2) {
            sendChunksHelp(sender, label);
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "preview" -> {
                int radius = 1000;
                if (args.length >= 3) {
                    try {
                        radius = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cRadio inválido: '" + args[2] + "'. Debe ser un número entero.");
                        return true;
                    }
                }

                if (radius <= RoadGeometry.SPAWN_MAX_X) {
                    sender.sendMessage("§cEl radio debe ser mayor a " + RoadGeometry.SPAWN_MAX_X + " bloques (límite del spawn).");
                    return true;
                }

                String worldName = targetWorldSupplier.get();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage("§cEl mundo objetivo '" + worldName + "' no está cargado.");
                    return true;
                }

                BatchCandidateScanner.PreviewResult preview = scanner.preview(world, radius);

                sender.sendMessage("§6--- [ InfinityRoads ] Previsualización de Chunks Candidatos ---");
                sender.sendMessage("§7Mundo objetivo: §f" + preview.worldName() + " §7| Radio: §f" + preview.radius() + " §7bloques");
                sender.sendMessage(String.format("§7Total chunks candidatos: §a%d §7(Sin cargar chunks del disco)", preview.totalCandidates()));
                sender.sendMessage(String.format("§7  - Norte (-Z): §f%d §7chunks", preview.northCandidates()));
                sender.sendMessage(String.format("§7  - Sur (+Z): §f%d §7chunks", preview.southCandidates()));
                sender.sendMessage(String.format("§7  - Este (+X): §f%d §7chunks", preview.eastCandidates()));
                sender.sendMessage(String.format("§7  - Oeste (-X): §f%d §7chunks", preview.westCandidates()));
                sender.sendMessage("§7Estado de chunks:");
                sender.sendMessage(String.format("§7  - Ya en memoria: §b%d §7(Completados: §a%d§7)", preview.loadedChunks(), preview.alreadyCompletedChunks()));
                sender.sendMessage(String.format("§7  - No verificados (pendientes): §e%d", preview.unverifiedChunks()));
                sender.sendMessage("§7Perfil de ejecución: §fVentana 1 chunk | Control TPS >= 19.0 | Exclusivo Y>=62");
                sender.sendMessage(String.format("§6Token de confirmación: §e§l%s §7(Válido por %d segundos)", preview.token(), preview.getRemainingSeconds()));
                sender.sendMessage("§aPara iniciar el procesamiento seguro, ejecuta:");
                sender.sendMessage("§f/" + label + " chunks start " + preview.token());

                if (plugin != null) {
                    plugin.getLogger().info(String.format(
                            "[Batch] Previsualización calculada a solicitud de %s [mundo: %s, radio: %d bloques]: %d chunks candidatos. Token emitido: %s",
                            sender.getName(), world.getName(), radius, preview.totalCandidates(), preview.token()
                    ));
                }
                return true;
            }

            case "start" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso correcto: /" + label + " chunks start <token>");
                    return true;
                }
                String token = args[2];
                return processor.startFromToken(token, sender);
            }

            case "status" -> {
                BatchQueueData queue = processor.getCurrentQueue();
                if (queue == null || queue.getState() == BatchTaskState.IDLE) {
                    sender.sendMessage("§eNo hay ninguna cola de procesamiento por lotes activa.");
                    sender.sendMessage("§7Usa §f/" + label + " chunks preview [radio] §7para previsualizar candidatos.");
                    return true;
                }

                sender.sendMessage("§6=== Estado del Procesamiento por Lotes ===");
                sender.sendMessage("§7Estado actual: " + formatState(queue.getState()));
                sender.sendMessage("§7Mundo: §f" + queue.getWorldName() + " §7| Radio: §f" + queue.getRadius() + " §7bloques");
                int total = queue.getTotalCount();
                int processed = queue.getProcessedCount();
                double pct = total > 0 ? (processed * 100.0 / total) : 0.0;
                sender.sendMessage(String.format("§7Progreso: §f%d/%d §7(§a%.1f%%§7)", processed, total, pct));
                sender.sendMessage(String.format("§7  - Completados: §a%d", queue.getCompletedCount()));
                sender.sendMessage(String.format("§7  - Omitidos (idempotentes): §b%d", queue.getSkippedCount()));
                sender.sendMessage(String.format("§7  - Pendientes: §e%d", queue.getPendingCount()));
                sender.sendMessage(String.format("§7  - Fallidos: §c%d", queue.getFailedCount()));
                double tps = processor.getCurrentServerTps();
                String tpsColor = tps >= 19.0 ? "§a" : "§c";
                sender.sendMessage(String.format("§7TPS del servidor: %s%.2f §7(Umbral: §f19.00§7)", tpsColor, tps));
                return true;
            }

            case "pause" -> {
                return processor.pause(sender);
            }

            case "resume" -> {
                return processor.resume(sender);
            }

            case "cancel" -> {
                return processor.cancel(sender);
            }

            case "retry" -> {
                return processor.retry(sender);
            }

            default -> {
                sender.sendMessage("§cAcción de chunks desconocida: " + action);
                sendChunksHelp(sender, label);
                return true;
            }
        }
    }

    private String formatState(BatchTaskState state) {
        return switch (state) {
            case IDLE -> "§7IDLE";
            case RUNNING -> "§aEN EJECUCIÓN (RUNNING)";
            case PAUSED -> "§6PAUSADO (PAUSED)";
            case CANCELLED -> "§cCANCELADO (CANCELLED)";
            case COMPLETED -> "§2COMPLETADO (COMPLETED)";
        };
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6--- Comandos de InfinityRoads ---");
        sender.sendMessage("§e/" + label + " admin on §7- Activa sesión temporal de mantenimiento (15 min).");
        sender.sendMessage("§e/" + label + " admin off §7- Desactiva sesión de mantenimiento.");
        sender.sendMessage("§e/" + label + " status §7- Muestra el estado del plugin y tu sesión.");
        sender.sendMessage("§e/" + label + " chunks preview [radio] §7- Previsualiza chunks candidatos.");
        sender.sendMessage("§e/" + label + " chunks status §7- Consulta el avance de la cola por lotes.");
        sender.sendMessage("§e/" + label + " reload §7- Recarga la configuración del plugin.");
    }

    private void sendChunksHelp(CommandSender sender, String label) {
        sender.sendMessage("§6--- Comandos de Procesamiento de Chunks ---");
        sender.sendMessage("§e/" + label + " chunks preview [radio] §7- Previsualiza matemáticamente y emite token.");
        sender.sendMessage("§e/" + label + " chunks start <token> §7- Inicia el procesamiento por lotes.");
        sender.sendMessage("§e/" + label + " chunks status §7- Muestra el estado y progreso de la cola.");
        sender.sendMessage("§e/" + label + " chunks pause §7- Pausa el procesamiento en curso.");
        sender.sendMessage("§e/" + label + " chunks resume §7- Reanuda el procesamiento pausado.");
        sender.sendMessage("§e/" + label + " chunks cancel §7- Cancela la cola de procesamiento.");
        sender.sendMessage("§e/" + label + " chunks retry §7- Restablece chunks fallidos para reintentar.");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> sub = new ArrayList<>();
            if (sender.hasPermission(MaintenanceManager.MAINTENANCE_PERMISSION)) {
                sub.add("admin");
            }
            sub.add("status");
            if (sender.hasPermission(CHUNKS_PERMISSION)) {
                sub.add("chunks");
            }
            if (sender.hasPermission("infinityroads.reload")) {
                sub.add("reload");
            }
            return filterPrefix(sub, args[0]);
        }

        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            if (sender.hasPermission(MaintenanceManager.MAINTENANCE_PERMISSION)) {
                return filterPrefix(List.of("on", "off"), args[1]);
            }
        }

        if (args.length == 2 && "chunks".equalsIgnoreCase(args[0])) {
            if (sender.hasPermission(CHUNKS_PERMISSION)) {
                return filterPrefix(List.of("preview", "start", "status", "pause", "resume", "cancel", "retry"), args[1]);
            }
        }

        if (args.length == 3 && "chunks".equalsIgnoreCase(args[0]) && "preview".equalsIgnoreCase(args[1])) {
            if (sender.hasPermission(CHUNKS_PERMISSION)) {
                return filterPrefix(List.of("500", "1000", "2000", "5000"), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                result.add(opt);
            }
        }
        return result;
    }
}
