package com.infinityroads.command;

import com.infinityroads.InfinityRoadsPlugin;
import com.infinityroads.protection.MaintenanceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de InfinityRoadsCommand (/ir)")
class InfinityRoadsCommandTest {

    private MaintenanceManager maintenanceManager;
    private InfinityRoadsCommand command;
    private Command fakeCommand;
    private InfinityRoadsPlugin fakePlugin;

    static class CapturedMessages {
        final List<String> list = new ArrayList<>();
    }

    static Player createFakePlayer(String name, Set<String> permissions, CapturedMessages captured) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "hasPermission" -> permissions.contains((String) args[0]);
                    case "sendMessage" -> {
                        captured.list.add(String.valueOf(args[0]));
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "FakePlayer[" + name + "]";
                    default -> null;
                }
        );
    }

    static ConsoleCommandSender createFakeConsole(CapturedMessages captured) {
        return (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "CONSOLE";
                    case "hasPermission" -> true;
                    case "sendMessage" -> {
                        captured.list.add(String.valueOf(args[0]));
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> null;
                }
        );
    }

    @BeforeEach
    void setUp() {
        maintenanceManager = new MaintenanceManager();
        command = new InfinityRoadsCommand(maintenanceManager, "world", () -> {});
        fakeCommand = new Command("ir") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("/ir admin on activa la sesión si el jugador tiene el permiso")
    void testAdminOnWithPermission() {
        CapturedMessages msgs = new CapturedMessages();
        Set<String> perms = new HashSet<>(List.of(MaintenanceManager.MAINTENANCE_PERMISSION));
        Player player = createFakePlayer("AdminPlayer", perms, msgs);

        assertFalse(maintenanceManager.hasActiveBypass(player));

        boolean handled = command.onCommand(player, fakeCommand, "ir", new String[]{"admin", "on"});
        assertTrue(handled);
        assertTrue(maintenanceManager.hasActiveBypass(player));
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("ACTIVADO")));
    }

    @Test
    @DisplayName("/ir admin on es rechazado si el jugador no tiene el permiso")
    void testAdminOnWithoutPermissionDenied() {
        CapturedMessages msgs = new CapturedMessages();
        Player player = createFakePlayer("UserPlayer", Collections.emptySet(), msgs);

        boolean handled = command.onCommand(player, fakeCommand, "ir", new String[]{"admin", "on"});
        assertTrue(handled);
        assertFalse(maintenanceManager.hasActiveBypass(player));
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("No tienes permiso")));
    }

    @Test
    @DisplayName("/ir admin on desde consola es rechazado")
    void testAdminOnFromConsoleRejected() {
        CapturedMessages msgs = new CapturedMessages();
        ConsoleCommandSender console = createFakeConsole(msgs);

        boolean handled = command.onCommand(console, fakeCommand, "ir", new String[]{"admin", "on"});
        assertTrue(handled);
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("solo puede ser ejecutado por jugadores")));
    }

    @Test
    @DisplayName("/ir admin off desactiva la sesión de mantenimiento")
    void testAdminOffDeactivatesSession() {
        CapturedMessages msgs = new CapturedMessages();
        Set<String> perms = new HashSet<>(List.of(MaintenanceManager.MAINTENANCE_PERMISSION));
        Player player = createFakePlayer("AdminPlayer", perms, msgs);

        maintenanceManager.activateSession(player);
        assertTrue(maintenanceManager.hasActiveBypass(player));

        boolean handled = command.onCommand(player, fakeCommand, "ir", new String[]{"admin", "off"});
        assertTrue(handled);
        assertFalse(maintenanceManager.hasActiveBypass(player));
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("DESACTIVADO")));
    }

    @Test
    @DisplayName("/ir status muestra el estado y la sesión del jugador")
    void testStatusDisplaysCorrectly() {
        CapturedMessages msgs = new CapturedMessages();
        Set<String> perms = new HashSet<>(List.of(MaintenanceManager.MAINTENANCE_PERMISSION));
        Player player = createFakePlayer("AdminPlayer", perms, msgs);

        command.onCommand(player, fakeCommand, "ir", new String[]{"status"});
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("INACTIVA")));

        maintenanceManager.activateSession(player);
        msgs.list.clear();
        command.onCommand(player, fakeCommand, "ir", new String[]{"status"});
        assertTrue(msgs.list.stream().anyMatch(m -> m.contains("ACTIVA")));
    }

    @Test
    @DisplayName("Tab completion autocompleta subcomandos y opciones correctamente")
    void testTabCompletion() {
        CapturedMessages msgs = new CapturedMessages();
        Set<String> perms = new HashSet<>(List.of(MaintenanceManager.MAINTENANCE_PERMISSION, "infinityroads.reload"));
        Player player = createFakePlayer("AdminPlayer", perms, msgs);

        List<String> rootCompletions = command.onTabComplete(player, fakeCommand, "ir", new String[]{""});
        assertNotNull(rootCompletions);
        assertTrue(rootCompletions.contains("admin"));
        assertTrue(rootCompletions.contains("status"));
        assertTrue(rootCompletions.contains("reload"));

        List<String> adminCompletions = command.onTabComplete(player, fakeCommand, "ir", new String[]{"admin", ""});
        assertNotNull(adminCompletions);
        assertTrue(adminCompletions.contains("on"));
        assertTrue(adminCompletions.contains("off"));
    }
}
