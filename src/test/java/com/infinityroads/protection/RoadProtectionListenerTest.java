package com.infinityroads.protection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de RoadProtectionListener")
class RoadProtectionListenerTest {

    private MaintenanceManager maintenanceManager;
    private RoadProtectionService protectionService;
    private RoadProtectionListener listener;
    private World testWorld;

    static World createFakeWorld(String name, int minHeight, int maxHeight) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getMinHeight" -> minHeight;
                    case "getMaxHeight" -> maxHeight;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FakeWorld[" + name + "]";
                    default -> null;
                }
        );
    }

    static Block createFakeBlock(World world, int x, int y, int z, Material material) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getType" -> material;
                    case "getLocation" -> new Location(world, x, y, z);
                    case "getRelative" -> {
                        BlockFace face = (BlockFace) args[0];
                        yield createFakeBlock(world, x + face.getModX(), y + face.getModY(), z + face.getModZ(), Material.AIR);
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> Objects.hash(x, y, z);
                    case "toString" -> String.format("FakeBlock[%d, %d, %d, %s]", x, y, z, material);
                    default -> null;
                }
        );
    }

    static Player createFakePlayer(String name, boolean hasPerm) {
        return createFakePlayer(name, hasPerm, null);
    }

    static Player createFakePlayer(String name, boolean hasPerm, Location location) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "hasPermission" -> hasPerm;
                    case "getLocation" -> location;
                    case "sendMessage" -> null;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "FakePlayer[" + name + "]";
                    default -> null;
                }
        );
    }

    static Monster createFakeMonster(String name) {
        return (Monster) Proxy.newProxyInstance(
                Monster.class.getClassLoader(),
                new Class<?>[]{Monster.class, Enemy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FakeMonster[" + name + "]";
                    default -> null;
                }
        );
    }

    static Arrow createFakeArrow(LivingEntity shooter) {
        return (Arrow) Proxy.newProxyInstance(
                Arrow.class.getClassLoader(),
                new Class<?>[]{Arrow.class, Projectile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FakeArrow[shooter=" + shooter + "]";
                    default -> null;
                }
        );
    }

    static org.bukkit.damage.DamageSource createFakeDamageSource() {
        return (org.bukkit.damage.DamageSource) Proxy.newProxyInstance(
                org.bukkit.damage.DamageSource.class.getClassLoader(),
                new Class<?>[]{org.bukkit.damage.DamageSource.class},
                (proxy, method, args) -> null
        );
    }

    static class TestItemStack extends ItemStack {
        private final Material material;
        TestItemStack(Material material) {
            super();
            this.material = material;
        }
        @Override
        public Material getType() {
            return material;
        }
        @Override
        public ItemStack clone() {
            return new TestItemStack(material);
        }
    }

    static BlockState createFakeBlockState(Block block) {
        return (BlockState) Proxy.newProxyInstance(
                BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlock" -> block;
                    case "getType" -> block.getType();
                    case "getWorld" -> block.getWorld();
                    case "getX" -> block.getX();
                    case "getY" -> block.getY();
                    case "getZ" -> block.getZ();
                    default -> null;
                }
        );
    }

    @BeforeEach
    void setUp() {
        testWorld = createFakeWorld("world", -64, 320);
        maintenanceManager = new MaintenanceManager(15 * 60 * 1000);
        protectionService = new RoadProtectionService("world", maintenanceManager);
        listener = new RoadProtectionListener(protectionService);
    }

    @Nested
    @DisplayName("1. Jugador Normal vs Bloques en la Carretera")
    class NormalPlayerProtectionTests {

        @Test
        @DisplayName("Jugador normal no puede romper bloque dentro de la carretera")
        void testNormalPlayerCannotBreakRoadBlock() {
            Player player = createFakePlayer("NormalUser", false);
            // Carretera Sur: X=0, Y=62, Z=150
            Block roadBlock = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);

            BlockBreakEvent event = new BlockBreakEvent(roadBlock, player);
            listener.onBlockBreak(event);

            assertTrue(event.isCancelled(), "Romper bloque en carretera debe cancelarse");
        }

        @Test
        @DisplayName("Jugador normal SÍ puede romper bloque inmediatamente fuera del corredor (X=6)")
        void testNormalPlayerCanBreakOutsideRoadBlock() {
            Player player = createFakePlayer("NormalUser", false);
            // Fuera de carretera Sur (adyacente X=6): X=6, Y=62, Z=150
            Block outsideBlock = createFakeBlock(testWorld, 6, 62, 150, Material.STONE);

            BlockBreakEvent event = new BlockBreakEvent(outsideBlock, player);
            listener.onBlockBreak(event);

            assertFalse(event.isCancelled(), "Romper bloque fuera de carretera NO debe cancelarse");
        }

        @Test
        @DisplayName("Jugador normal no puede colocar bloque dentro del corredor")
        void testNormalPlayerCannotPlaceBlockInRoad() {
            Player player = createFakePlayer("NormalUser", false);
            Block roadAir = createFakeBlock(testWorld, 0, 63, 150, Material.AIR);
            Block against = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            BlockState replaced = createFakeBlockState(roadAir);
            ItemStack item = new TestItemStack(Material.COBBLESTONE);

            BlockPlaceEvent event = new BlockPlaceEvent(roadAir, replaced, against, item, player, true);
            listener.onBlockPlace(event);

            assertTrue(event.isCancelled(), "Colocar bloque en el corredor debe cancelarse");
        }

        @Test
        @DisplayName("Jugador normal SÍ puede colocar bloque fuera del corredor (X=6)")
        void testNormalPlayerCanPlaceOutsideRoad() {
            Player player = createFakePlayer("NormalUser", false);
            Block outsideAir = createFakeBlock(testWorld, 6, 63, 150, Material.AIR);
            Block against = createFakeBlock(testWorld, 6, 62, 150, Material.DIRT);
            BlockState replaced = createFakeBlockState(outsideAir);
            ItemStack item = new TestItemStack(Material.COBBLESTONE);

            BlockPlaceEvent event = new BlockPlaceEvent(outsideAir, replaced, against, item, player, true);
            listener.onBlockPlace(event);

            assertFalse(event.isCancelled(), "Colocar bloque fuera del corredor NO debe cancelarse");
        }

        @Test
        @DisplayName("Jugador normal no puede vaciar cubos dentro de la carretera")
        void testNormalPlayerCannotEmptyBucketInRoad() {
            Player player = createFakePlayer("NormalUser", false);
            Block pavement = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            // Vaciado sobre el pavimento (apunta a Y=63)
            PlayerBucketEmptyEvent event = new PlayerBucketEmptyEvent(player, pavement, BlockFace.UP, Material.WATER_BUCKET, new TestItemStack(Material.WATER_BUCKET));

            listener.onBucketEmpty(event);
            assertTrue(event.isCancelled(), "Vaciar cubo en la carretera debe cancelarse");
        }

        @Test
        @DisplayName("Jugador normal no puede recoger líquidos dentro de la carretera")
        void testNormalPlayerCannotFillBucketInRoad() {
            Player player = createFakePlayer("NormalUser", false);
            Block waterInRoad = createFakeBlock(testWorld, 0, 63, 150, Material.WATER);
            PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, waterInRoad, BlockFace.UP, Material.BUCKET, new TestItemStack(Material.WATER_BUCKET));

            listener.onBucketFill(event);
            assertTrue(event.isCancelled(), "Llenar cubo en la carretera debe cancelarse");
        }

        @Test
        @DisplayName("Interacción física (trampling) en la carretera se cancela")
        void testPhysicalInteractionCancelled() {
            Player player = createFakePlayer("NormalUser", false);
            Block block = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.UP);

            listener.onPlayerInteract(event);
            assertTrue(event.isCancelled());
        }

        @Test
        @DisplayName("Interacción inocua (comer comida o sostener espada) no se bloquea")
        void testInnocuousInteractionNotCancelled() {
            Player player = createFakePlayer("NormalUser", false);
            Block block = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            ItemStack food = new TestItemStack(Material.COOKED_BEEF);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, food, block, BlockFace.UP);

            listener.onPlayerInteract(event);
            assertFalse(event.isCancelled(), "Comer o sostener espada sobre la carretera NO debe cancelarse");
        }

        @Test
        @DisplayName("Interacción modificadora (azada, pala, hacha, mechero) en la carretera se cancela")
        void testModifyingInteractionCancelled() {
            Player player = createFakePlayer("NormalUser", false);
            Block block = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);

            ItemStack hoe = new TestItemStack(Material.DIAMOND_HOE);
            PlayerInteractEvent hoeEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hoe, block, BlockFace.UP);
            listener.onPlayerInteract(hoeEvent);
            assertTrue(hoeEvent.isCancelled());

            ItemStack flint = new TestItemStack(Material.FLINT_AND_STEEL);
            PlayerInteractEvent flintEvent = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, flint, block, BlockFace.UP);
            listener.onPlayerInteract(flintEvent);
            assertTrue(flintEvent.isCancelled());
        }
    }

    @Nested
    @DisplayName("2. Administrador con Modo Mantenimiento (/ir admin on)")
    class AdminMaintenanceProtectionTests {

        @Test
        @DisplayName("Administrador con mantenimiento activo SÍ puede romper y colocar bloques en la carretera")
        void testAdminWithActiveBypassCanModifyRoad() {
            Player admin = createFakePlayer("AdminUser", true);
            assertTrue(maintenanceManager.activateSession(admin));

            Block roadBlock = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            BlockBreakEvent breakEvent = new BlockBreakEvent(roadBlock, admin);
            listener.onBlockBreak(breakEvent);
            assertFalse(breakEvent.isCancelled(), "Admin en mantenimiento debe poder romper");

            Block roadAir = createFakeBlock(testWorld, 0, 63, 150, Material.AIR);
            BlockState replaced = createFakeBlockState(roadAir);
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(roadAir, replaced, roadBlock, new TestItemStack(Material.STONE), admin, true);
            listener.onBlockPlace(placeEvent);
            assertFalse(placeEvent.isCancelled(), "Admin en mantenimiento debe poder colocar");

            PlayerBucketEmptyEvent bucketEvent = new PlayerBucketEmptyEvent(admin, roadBlock, BlockFace.UP, Material.WATER_BUCKET, new TestItemStack(Material.WATER_BUCKET));
            listener.onBucketEmpty(bucketEvent);
            assertFalse(bucketEvent.isCancelled(), "Admin en mantenimiento debe poder vaciar cubos");
        }

        @Test
        @DisplayName("Al desactivar mantenimiento (/ir admin off), la protección se restaura de inmediato")
        void testDeactivatingMaintenanceRestoresProtection() {
            Player admin = createFakePlayer("AdminUser", true);
            maintenanceManager.activateSession(admin);

            Block roadBlock = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);

            // 1. Activo -> puede romper
            BlockBreakEvent event1 = new BlockBreakEvent(roadBlock, admin);
            listener.onBlockBreak(event1);
            assertFalse(event1.isCancelled());

            // 2. Desactivar
            maintenanceManager.deactivateSession(admin);

            // 3. Inactivo -> cancelado
            BlockBreakEvent event2 = new BlockBreakEvent(roadBlock, admin);
            listener.onBlockBreak(event2);
            assertTrue(event2.isCancelled(), "Al desactivar mantenimiento, debe volver a proteger");
        }

        @Test
        @DisplayName("Al desconectarse el jugador (PlayerQuitEvent), la sesión termina y la protección se restaura")
        void testQuitRestoresProtection() {
            Player admin = createFakePlayer("AdminUser", true);
            maintenanceManager.activateSession(admin);
            assertTrue(maintenanceManager.hasActiveBypass(admin));

            // Simular evento de desconexión
            listener.onPlayerQuit(new org.bukkit.event.player.PlayerQuitEvent(
                    admin,
                    (net.kyori.adventure.text.Component) null,
                    org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED
            ));

            assertFalse(maintenanceManager.hasActiveBypass(admin));
            Block roadBlock = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            BlockBreakEvent event = new BlockBreakEvent(roadBlock, admin);
            listener.onBlockBreak(event);
            assertTrue(event.isCancelled());
        }
    }

    @Nested
    @DisplayName("3. Protección de la Columna Vertical Completa")
    class FullVerticalProtectionTests {

        @ParameterizedTest(name = "Cota Y={0} dentro de la carretera debe estar protegida")
        @ValueSource(ints = {-64, -10, 0, 30, 61, 62, 63, 150, 319})
        void testVerticalColumnIsFullyProtected(int y) {
            Player player = createFakePlayer("NormalUser", false);
            // Carretera Norte: X=0, Z=-150
            Block block = createFakeBlock(testWorld, 0, y, -150, Material.STONE);

            BlockBreakEvent event = new BlockBreakEvent(block, player);
            listener.onBlockBreak(event);

            assertTrue(event.isCancelled(), "Cota Y=" + y + " en la columna protegida debe cancelarse");
        }

        @Test
        @DisplayName("Cotas fuera de los límites del mundo (Y < -64 o Y >= 320) no son protegidas")
        void testOutOfBoundsHeightsNotProtected() {
            assertFalse(protectionService.isProtected(testWorld, 0, -65, -150));
            assertFalse(protectionService.isProtected(testWorld, 0, 320, -150));
        }
    }

    @Nested
    @DisplayName("4. Explosiones: Protección Selectiva de Bloques de Carretera")
    class ExplosionProtectionTests {

        @Test
        @DisplayName("Los bloques de la carretera se eliminan de la explosión; los bloques fuera explotan")
        void testExplosionFiltersRoadBlocks() {
            Block road1 = createFakeBlock(testWorld, 0, 62, 150, Material.BLUE_CONCRETE);
            Block road2 = createFakeBlock(testWorld, 1, 62, 150, Material.BLUE_CONCRETE);
            Block outside1 = createFakeBlock(testWorld, 6, 62, 150, Material.DIRT);
            Block outside2 = createFakeBlock(testWorld, 7, 62, 150, Material.STONE);

            List<Block> blocks = new ArrayList<>(List.of(road1, road2, outside1, outside2));

            Location loc = new Location(testWorld, 3, 62, 150);
            EntityExplodeEvent event = new EntityExplodeEvent((Entity) null, loc, blocks, 4.0f, org.bukkit.ExplosionResult.DESTROY);

            listener.onEntityExplode(event);

            // Los 2 bloques de carretera deben haberse retirado; los 2 de afuera deben permanecer
            assertEquals(2, event.blockList().size());
            assertTrue(event.blockList().contains(outside1));
            assertTrue(event.blockList().contains(outside2));
            assertFalse(event.blockList().contains(road1));
            assertFalse(event.blockList().contains(road2));
        }
    }

    @Nested
    @DisplayName("5. Pistones y Mecanismos")
    class PistonProtectionTests {

        @Test
        @DisplayName("Pistón que empuja bloques hacia la carretera es cancelado")
        void testPistonPushingIntoRoadCancelled() {
            // Pistón en X=7 empujando hacia el Oeste (West -> modX = -1)
            // Bloque empujado en X=6 se movería a X=5 (dentro de la carretera)
            Block piston = createFakeBlock(testWorld, 7, 62, 150, Material.PISTON);
            Block pushedBlock = createFakeBlock(testWorld, 6, 62, 150, Material.STONE);

            BlockPistonExtendEvent event = new BlockPistonExtendEvent(piston, List.of(pushedBlock), BlockFace.WEST);
            listener.onPistonExtend(event);

            assertTrue(event.isCancelled(), "Pistón empujando hacia X=5 (carretera) debe cancelarse");
        }

        @Test
        @DisplayName("Pistón cuya cabeza se extendería dentro de la carretera es cancelado")
        void testPistonHeadExtendingIntoRoadCancelled() {
            // Pistón en X=6 mirando al Oeste (West -> modX = -1). Cabeza iría a X=5
            Block piston = createFakeBlock(testWorld, 6, 62, 150, Material.PISTON);

            BlockPistonExtendEvent event = new BlockPistonExtendEvent(piston, Collections.emptyList(), BlockFace.WEST);
            listener.onPistonExtend(event);

            assertTrue(event.isCancelled(), "Cabeza de pistón extendiéndose a X=5 debe cancelarse");
        }

        @Test
        @DisplayName("Pistón pegajoso que intenta extraer un bloque de la carretera es cancelado")
        void testStickyPistonPullingFromRoadCancelled() {
            // Bloque a jalar está en X=5 (carretera)
            Block piston = createFakeBlock(testWorld, 7, 62, 150, Material.STICKY_PISTON);
            Block roadBlock = createFakeBlock(testWorld, 5, 62, 150, Material.BLUE_CONCRETE);

            BlockPistonRetractEvent event = new BlockPistonRetractEvent(piston, List.of(roadBlock), BlockFace.EAST);
            listener.onPistonRetract(event);

            assertTrue(event.isCancelled(), "Pistón pegajoso jalando bloque de carretera debe cancelarse");
        }

        @Test
        @DisplayName("Pistón que opera completamente fuera del corredor NO es cancelado")
        void testPistonOperatingOutsideRoadNotCancelled() {
            // Pistón en X=10 empujando hacia X=11 (lejos de la carretera)
            Block piston = createFakeBlock(testWorld, 10, 62, 150, Material.PISTON);
            Block pushedBlock = createFakeBlock(testWorld, 11, 62, 150, Material.STONE);

            BlockPistonExtendEvent event = new BlockPistonExtendEvent(piston, List.of(pushedBlock), BlockFace.EAST);
            listener.onPistonExtend(event);

            assertFalse(event.isCancelled(), "Pistón lejos de la carretera no debe cancelarse");
        }
    }

    @Nested
    @DisplayName("6. Protección de Daño a Jugadores (Explosiones y Mobs Hostiles)")
    class PlayerDamageProtectionTests {

        private final org.bukkit.damage.DamageSource fakeSource = createFakeDamageSource();

        @Test
        @DisplayName("Jugador dentro de la carretera es inmune a explosiones de entidades (Creeper/TNT)")
        void testPlayerInsideRoadImmuneToEntityExplosion() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player player = createFakePlayer("Traveler", false, roadLoc);

            EntityDamageEvent event = new EntityDamageEvent(player, DamageCause.ENTITY_EXPLOSION, fakeSource, 20.0);
            listener.onEntityDamage(event);

            assertTrue(event.isCancelled(), "El daño por explosión de entidad debe cancelarse para un jugador en la vía");
        }

        @Test
        @DisplayName("Jugador dentro de la carretera es inmune a explosiones de bloques (TNT bloque, camas, anclas)")
        void testPlayerInsideRoadImmuneToBlockExplosion() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player player = createFakePlayer("Traveler", false, roadLoc);

            EntityDamageEvent event = new EntityDamageEvent(player, DamageCause.BLOCK_EXPLOSION, fakeSource, 20.0);
            listener.onEntityDamage(event);

            assertTrue(event.isCancelled(), "El daño por explosión de bloques debe cancelarse para un jugador en la vía");
        }

        @Test
        @DisplayName("Jugador dentro de la carretera es inmune a ataque cuerpo a cuerpo de mob hostil (Zombie)")
        void testPlayerInsideRoadImmuneToHostileMelee() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player player = createFakePlayer("Traveler", false, roadLoc);
            Monster zombie = createFakeMonster("Zombie");

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(zombie, player, DamageCause.ENTITY_ATTACK, fakeSource, 5.0);
            listener.onEntityDamage(event);

            assertTrue(event.isCancelled(), "El ataque cuerpo a cuerpo de mob hostil debe cancelarse");
        }

        @Test
        @DisplayName("Flecha de mob hostil (Skeleton) disparada desde fuera NO daña al jugador dentro de la vía")
        void testHostileArrowShotFromOutsideDoesNotDamagePlayerInside() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player player = createFakePlayer("Traveler", false, roadLoc);

            Monster skeletonOutside = createFakeMonster("Skeleton");
            Arrow arrow = createFakeArrow(skeletonOutside);

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, player, DamageCause.PROJECTILE, fakeSource, 4.0);
            listener.onEntityDamage(event);

            assertTrue(event.isCancelled(), "Proyectil hostil disparado desde afuera no debe dañar al jugador dentro de la carretera");
        }

        @Test
        @DisplayName("Jugador fuera de la carretera recibe daño normal de mob hostil (combate preservado)")
        void testPlayerOutsideRoadTakesNormalHostileDamage() {
            Location outsideLoc = new Location(testWorld, 20, 63, 150);
            Player player = createFakePlayer("Traveler", false, outsideLoc);

            Monster skeleton = createFakeMonster("Skeleton");
            Arrow arrow = createFakeArrow(skeleton);

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(arrow, player, DamageCause.PROJECTILE, fakeSource, 4.0);
            listener.onEntityDamage(event);

            assertFalse(event.isCancelled(), "El combate normal fuera de la carretera no debe cancelarse");
        }

        @Test
        @DisplayName("Jugador fuera de la carretera recibe daño normal por explosiones")
        void testPlayerOutsideRoadTakesNormalExplosionDamage() {
            Location outsideLoc = new Location(testWorld, 20, 63, 150);
            Player player = createFakePlayer("Traveler", false, outsideLoc);

            EntityDamageEvent event = new EntityDamageEvent(player, DamageCause.ENTITY_EXPLOSION, fakeSource, 15.0);
            listener.onEntityDamage(event);

            assertFalse(event.isCancelled(), "Explosiones fuera de la carretera deben infligir daño normal");
        }

        @Test
        @DisplayName("Daño ambiental normal (caída, hambre) dentro de la carretera NO es cancelado")
        void testEnvironmentalDamageInsideRoadNotCancelled() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player player = createFakePlayer("Traveler", false, roadLoc);

            EntityDamageEvent fallEvent = new EntityDamageEvent(player, DamageCause.FALL, fakeSource, 5.0);
            listener.onEntityDamage(fallEvent);
            assertFalse(fallEvent.isCancelled(), "El daño por caída no debe ser cancelado");

            EntityDamageEvent starveEvent = new EntityDamageEvent(player, DamageCause.STARVATION, fakeSource, 1.0);
            listener.onEntityDamage(starveEvent);
            assertFalse(starveEvent.isCancelled(), "El daño por inanición no debe ser cancelado");
        }

        @Test
        @DisplayName("Ataques entre jugadores (PvP) dentro de la carretera NO son cancelados por la regla de mobs")
        void testPvPInsideRoadNotCancelledByHostileRule() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player attacker = createFakePlayer("Attacker", false, roadLoc);
            Player victim = createFakePlayer("Victim", false, roadLoc);

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, fakeSource, 6.0);
            listener.onEntityDamage(event);

            assertFalse(event.isCancelled(), "La regla contra mobs hostiles no debe cancelar ataques entre jugadores");
        }

        @Test
        @DisplayName("Jugador atacando a un mob hostil dentro de la carretera NO es cancelado")
        void testPlayerAttackingMobInsideRoadNotCancelled() {
            Location roadLoc = new Location(testWorld, 0, 63, 150);
            Player attacker = createFakePlayer("Attacker", false, roadLoc);
            Monster zombie = createFakeMonster("Zombie");

            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, zombie, DamageCause.ENTITY_ATTACK, fakeSource, 8.0);
            listener.onEntityDamage(event);

            assertFalse(event.isCancelled(), "Los jugadores deben poder atacar mobs dentro de la vía");
        }
    }
}
