package com.infinityroads.protection;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de MaintenanceManager")
class MaintenanceManagerTest {

    private MaintenanceManager manager;

    static class FakePlayer {
        final UUID uuid = UUID.randomUUID();
        final String name;
        final Set<String> permissions = new HashSet<>();
        final AtomicBoolean op = new AtomicBoolean(false);

        FakePlayer(String name) {
            this.name = name;
        }

        Player createProxy() {
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "getName" -> name;
                        case "hasPermission" -> {
                            String perm = (String) args[0];
                            yield permissions.contains(perm) || (op.get() && permissions.contains(perm));
                        }
                        case "isOp" -> op.get();
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> uuid.hashCode();
                        case "toString" -> "FakePlayer[" + name + "]";
                        default -> null;
                    }
            );
        }
    }

    @BeforeEach
    void setUp() {
        manager = new MaintenanceManager(15 * 60 * 1000); // 15 minutos
    }

    @Test
    @DisplayName("Jugador con permiso activa la sesión exitosamente")
    void testActivationWithPermission() {
        FakePlayer fake = new FakePlayer("Admin1");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        assertFalse(manager.hasActiveBypass(player));

        boolean activated = manager.activateSession(player);
        assertTrue(activated);
        assertTrue(manager.hasActiveBypass(player));
        assertTrue(manager.isSessionActive(fake.uuid));
        assertTrue(manager.getRemainingMillis(fake.uuid) > 0);
    }

    @Test
    @DisplayName("Jugador sin permiso es rechazado")
    void testActivationWithoutPermissionDenied() {
        FakePlayer fake = new FakePlayer("Player1");
        Player player = fake.createProxy();

        boolean activated = manager.activateSession(player);
        assertFalse(activated);
        assertFalse(manager.hasActiveBypass(player));
    }

    @Test
    @DisplayName("Operador NO tiene bypass automático si no ejecuta /ir admin on")
    void testOpHasNoBypassWithoutActivation() {
        FakePlayer fake = new FakePlayer("Op1");
        fake.op.set(true);
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        // Aunque sea OP y tenga el permiso, el bypass está inactivo hasta activarlo
        assertFalse(manager.hasActiveBypass(player));
    }

    @Test
    @DisplayName("deactivateSession desactiva inmediatamente el bypass")
    void testDeactivation() {
        FakePlayer fake = new FakePlayer("Admin2");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        manager.activateSession(player);
        assertTrue(manager.hasActiveBypass(player));

        boolean deactivated = manager.deactivateSession(player);
        assertTrue(deactivated);
        assertFalse(manager.hasActiveBypass(player));
    }

    @Test
    @DisplayName("endSession al desconectar jugador desactiva el bypass")
    void testPlayerQuitEndsSession() {
        FakePlayer fake = new FakePlayer("Admin3");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        manager.activateSession(player);
        assertTrue(manager.hasActiveBypass(player));

        manager.endSession(fake.uuid);
        assertFalse(manager.hasActiveBypass(player));
    }

    @Test
    @DisplayName("Sesión expira automáticamente transcurrido el tiempo límite")
    void testSessionExpiration() {
        // Gestor con duración de solo 50ms para la prueba
        MaintenanceManager shortManager = new MaintenanceManager(50);
        FakePlayer fake = new FakePlayer("Admin4");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        shortManager.activateSession(player);
        assertTrue(shortManager.hasActiveBypass(player));

        try {
            Thread.sleep(70);
        } catch (InterruptedException ignored) {}

        assertFalse(shortManager.hasActiveBypass(player), "La sesión debe haber expirado tras 50ms");
    }

    @Test
    @DisplayName("Revocar permiso invalida el bypass inmediatamente en la siguiente comprobación")
    void testPermissionRevocationInvalidatesBypass() {
        FakePlayer fake = new FakePlayer("Admin5");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        manager.activateSession(player);
        assertTrue(manager.hasActiveBypass(player));

        // Revocar permiso
        fake.permissions.remove(MaintenanceManager.MAINTENANCE_PERMISSION);

        assertFalse(manager.hasActiveBypass(player), "Al perder el permiso, el bypass debe denegarse");
    }

    @Test
    @DisplayName("Expiración se mide estrictamente desde la activación (15m) sin reiniciarse por acciones intermedias")
    void testExpirationMeasuredFromActivationRegardlessOfActions() {
        MaintenanceManager shortManager = new MaintenanceManager(60);
        FakePlayer fake = new FakePlayer("Admin6");
        fake.permissions.add(MaintenanceManager.MAINTENANCE_PERMISSION);
        Player player = fake.createProxy();

        shortManager.activateSession(player);
        assertTrue(shortManager.hasActiveBypass(player));

        // Acción intermedia a los 20ms
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignored) {}
        assertTrue(shortManager.hasActiveBypass(player), "A los 20ms la sesión sigue activa");

        // Dormir más allá de los 60ms iniciales desde la activación (total ~80ms)
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {}

        // Debe expirar porque los 60ms se cuentan estrictamente desde la activación, no desde la última acción
        assertFalse(shortManager.hasActiveBypass(player), "La sesión debe expirar estrictamente según el tiempo desde la activación");
    }
}
