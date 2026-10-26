package com.infinityroads.protection;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias de FluidContainmentListener")
class FluidContainmentListenerTest {

    private MaintenanceManager maintenanceManager;
    private RoadProtectionService protectionService;
    private FluidContainmentListener listener;
    private World testWorld;
    private World otherWorld;

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

    static Block createFakeBlock(World world, int x, int y, int z, Material material, BlockFace facing) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getWorld".equals(name)) return world;
                    if ("getX".equals(name)) return x;
                    if ("getY".equals(name)) return y;
                    if ("getZ".equals(name)) return z;
                    if ("getType".equals(name)) return material;
                    if ("getRelative".equals(name)) {
                        BlockFace face = (BlockFace) args[0];
                        return createFakeBlock(world, x + face.getModX(), y + face.getModY(), z + face.getModZ(), Material.AIR, BlockFace.NORTH);
                    }
                    if ("getBlockData".equals(name)) {
                        return (BlockData) Proxy.newProxyInstance(
                                BlockData.class.getClassLoader(),
                                new Class<?>[]{BlockData.class, Directional.class},
                                (p, m, a) -> {
                                    if ("getFacing".equals(m.getName())) return facing;
                                    if ("getMaterial".equals(m.getName())) return material;
                                    return null;
                                }
                        );
                    }
                    if ("equals".equals(name)) return proxy == args[0];
                    if ("hashCode".equals(name)) return Objects.hash(x, y, z);
                    return null;
                }
        );
    }

    @BeforeEach
    void setUp() {
        testWorld = createFakeWorld("world", -64, 320);
        otherWorld = createFakeWorld("nether", 0, 256);
        maintenanceManager = new MaintenanceManager();
        protectionService = new RoadProtectionService("world", maintenanceManager);
        listener = new FluidContainmentListener(protectionService);
    }

    @Nested
    @DisplayName("1. Propagación de Fluidos (BlockFromToEvent)")
    class BlockFromToTests {

        @Test
        @DisplayName("Agua exterior intentando fluir hacia la carretera (X=6 -> X=5) es cancelada")
        void testWaterFlowingIntoRoadCancelled() {
            // Origen en X=6 (fuera), destino en X=5 (carretera Sur, Z=150, Y=63)
            Block from = createFakeBlock(testWorld, 6, 63, 150, Material.WATER, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 5, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertTrue(event.isCancelled(), "El flujo de agua hacia X=5 (carretera) debe cancelarse");
        }

        @Test
        @DisplayName("Lava exterior intentando fluir hacia la carretera (X=6 -> X=5) es cancelada")
        void testLavaFlowingIntoRoadCancelled() {
            Block from = createFakeBlock(testWorld, 6, 63, 150, Material.LAVA, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 5, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertTrue(event.isCancelled(), "El flujo de lava hacia X=5 (carretera) debe cancelarse");
        }

        @Test
        @DisplayName("Agua exterior fluyendo en dirección opuesta (X=6 -> X=7) NO es cancelada")
        void testWaterFlowingAwayFromRoadNotCancelled() {
            Block from = createFakeBlock(testWorld, 6, 63, 150, Material.WATER, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 7, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertFalse(event.isCancelled(), "El flujo de agua hacia X=7 (lejos de la carretera) NO debe cancelarse");
        }

        @Test
        @DisplayName("Lava exterior fluyendo en dirección opuesta (X=6 -> X=7) NO es cancelada")
        void testLavaFlowingAwayFromRoadNotCancelled() {
            Block from = createFakeBlock(testWorld, 6, 63, 150, Material.LAVA, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 7, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertFalse(event.isCancelled(), "El flujo de lava hacia X=7 (lejos de la carretera) NO debe cancelarse");
        }

        @Test
        @DisplayName("Agua y lava fluyendo en zonas completamente alejadas de cualquier corredor NO son canceladas")
        void testFluidsFarOutsideCorridorNotCancelled() {
            // Agua en (500, 70, 500)
            Block waterFrom = createFakeBlock(testWorld, 500, 70, 500, Material.WATER, BlockFace.NORTH);
            Block waterTo = createFakeBlock(testWorld, 501, 70, 500, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent waterEvent = new BlockFromToEvent(waterFrom, waterTo);
            listener.onBlockFromTo(waterEvent);
            assertFalse(waterEvent.isCancelled(), "Agua lejana no debe ser cancelada");

            // Lava en (500, 70, 500)
            Block lavaFrom = createFakeBlock(testWorld, 500, 70, 500, Material.LAVA, BlockFace.NORTH);
            Block lavaTo = createFakeBlock(testWorld, 500, 70, 501, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent lavaEvent = new BlockFromToEvent(lavaFrom, lavaTo);
            listener.onBlockFromTo(lavaEvent);
            assertFalse(lavaEvent.isCancelled(), "Lava lejana no debe ser cancelada");
        }

        @Test
        @DisplayName("Agua saliendo del spawn hacia el camino Sur (Z=100 -> Z=101) es cancelada")
        void testWaterFlowingFromSpawnIntoSouthRoadCancelled() {
            Block from = createFakeBlock(testWorld, 0, 63, 100, Material.WATER, BlockFace.NORTH); // en spawn
            Block to = createFakeBlock(testWorld, 0, 63, 101, Material.AIR, BlockFace.NORTH); // en camino Sur

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertTrue(event.isCancelled(), "El flujo de agua penetrando en Z=101 debe cancelarse");
        }

        @Test
        @DisplayName("Lava saliendo del spawn hacia el camino Sur (Z=100 -> Z=101) es cancelada")
        void testLavaFlowingFromSpawnIntoSouthRoadCancelled() {
            Block from = createFakeBlock(testWorld, 0, 63, 100, Material.LAVA, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 0, 63, 101, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertTrue(event.isCancelled(), "El flujo de lava penetrando en Z=101 debe cancelarse");
        }

        @Test
        @DisplayName("Agua fluyendo dentro del spawn (Z=50 -> Z=51) NO es cancelada")
        void testWaterFlowingInsideSpawnNotCancelled() {
            Block from = createFakeBlock(testWorld, 0, 63, 50, Material.WATER, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 0, 63, 51, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);

            assertFalse(event.isCancelled(), "Flujo dentro del spawn no debe alterarse");
        }

        @Test
        @DisplayName("Flujo de agua y lava en otro mundo no es cancelado bajo ninguna circunstancia")
        void testFluidsInOtherWorldNotCancelled() {
            // Lava en coordenadas que en overworld serían carretera
            Block lavaFrom = createFakeBlock(otherWorld, 0, 63, 150, Material.LAVA, BlockFace.NORTH);
            Block lavaTo = createFakeBlock(otherWorld, 0, 63, 151, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent lavaEvent = new BlockFromToEvent(lavaFrom, lavaTo);
            listener.onBlockFromTo(lavaEvent);
            assertFalse(lavaEvent.isCancelled(), "Lava en mundo nether no debe afectarse");

            // Agua en coordenadas que en overworld serían frontera
            Block waterFrom = createFakeBlock(otherWorld, 6, 63, 150, Material.WATER, BlockFace.NORTH);
            Block waterTo = createFakeBlock(otherWorld, 5, 63, 150, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent waterEvent = new BlockFromToEvent(waterFrom, waterTo);
            listener.onBlockFromTo(waterEvent);
            assertFalse(waterEvent.isCancelled(), "Agua en mundo nether no debe afectarse");
        }

        @Test
        @DisplayName("Fluidos naturales bajo el pavimento (Y < 62) conservan su simulación y NO son cancelados")
        void testNaturalFluidsBelowY62NotCancelled() {
            // Agua fluyendo en una cueva subterránea a Y=30 directamente bajo la carretera Sur (X=0, Z=150)
            Block from = createFakeBlock(testWorld, 0, 30, 150, Material.WATER, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 0, 30, 151, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);
            assertFalse(event.isCancelled(), "Los fluidos bajo Y=62 deben fluir con total normalidad natural");

            // Lava fluyendo en cueva a Y=30 bajo carretera Sur
            Block lavaFrom = createFakeBlock(testWorld, 0, 30, 150, Material.LAVA, BlockFace.NORTH);
            Block lavaTo = createFakeBlock(testWorld, 1, 30, 150, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent lavaEvent = new BlockFromToEvent(lavaFrom, lavaTo);
            listener.onBlockFromTo(lavaEvent);
            assertFalse(lavaEvent.isCancelled(), "Lava bajo Y=62 debe fluir con total normalidad natural");

            // Fluidos en cota límite inmediata inferior (Y=61) bajo el pavimento
            Block y61From = createFakeBlock(testWorld, 0, 61, 150, Material.WATER, BlockFace.NORTH);
            Block y61To = createFakeBlock(testWorld, 0, 61, 151, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent y61Event = new BlockFromToEvent(y61From, y61To);
            listener.onBlockFromTo(y61Event);
            assertFalse(y61Event.isCancelled(), "Agua en Y=61 (inmediatamente bajo el pavimento) no debe cancelarse");
        }

        @Test
        @DisplayName("Líquido colocado dentro de la carretera (Y >= 62) NO puede propagarse hacia ningún bloque")
        void testLiquidPlacedInsideRoadCannotSpread() {
            // Caso 1: Fluir horizontalmente dentro del corredor (X=0 -> X=1 en Y=63)
            Block from = createFakeBlock(testWorld, 0, 63, 150, Material.WATER, BlockFace.NORTH);
            Block toInside = createFakeBlock(testWorld, 1, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent eventInside = new BlockFromToEvent(from, toInside);
            listener.onBlockFromTo(eventInside);
            assertTrue(eventInside.isCancelled(), "Líquido colocado dentro no debe propagarse dentro de la vía");

            // Caso 2: Fluir desde el borde (X=5) hacia el exterior (X=6)
            Block edgeFrom = createFakeBlock(testWorld, 5, 63, 150, Material.WATER, BlockFace.NORTH);
            Block toOutside = createFakeBlock(testWorld, 6, 63, 150, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent eventOutside = new BlockFromToEvent(edgeFrom, toOutside);
            listener.onBlockFromTo(eventOutside);
            assertTrue(eventOutside.isCancelled(), "Líquido colocado dentro no debe desbordarse hacia el exterior");

            // Caso 3: Fluir hacia abajo (Y=63 -> Y=62)
            Block toDown = createFakeBlock(testWorld, 0, 62, 150, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent eventDown = new BlockFromToEvent(from, toDown);
            listener.onBlockFromTo(eventDown);
            assertTrue(eventDown.isCancelled(), "Líquido colocado dentro no debe propagarse hacia abajo");

            // Caso 4: Lava colocada dentro del corredor intentando desbordarse al exterior
            Block lavaEdgeFrom = createFakeBlock(testWorld, 5, 63, 150, Material.LAVA, BlockFace.NORTH);
            Block lavaToOutside = createFakeBlock(testWorld, 6, 63, 150, Material.AIR, BlockFace.NORTH);
            BlockFromToEvent lavaExitEvent = new BlockFromToEvent(lavaEdgeFrom, lavaToOutside);
            listener.onBlockFromTo(lavaExitEvent);
            assertTrue(lavaExitEvent.isCancelled(), "Lava colocada dentro no debe desbordarse hacia el exterior");
        }

        @Test
        @DisplayName("Contención de fluidos NO tiene bypass administrativo (no propaga aunque haya sesión activa)")
        void testFluidContainmentHasNoAdminBypass() {
            // Aunque un admin haya colocado el agua con /ir admin on, la regla de contención no tiene bypass
            Block from = createFakeBlock(testWorld, 0, 63, 150, Material.LAVA, BlockFace.NORTH);
            Block to = createFakeBlock(testWorld, 0, 63, 151, Material.AIR, BlockFace.NORTH);

            BlockFromToEvent event = new BlockFromToEvent(from, to);
            listener.onBlockFromTo(event);
            assertTrue(event.isCancelled(), "La contención de fluidos no debe tener bypass bajo ninguna circunstancia");
        }
    }

    @Nested
    @DisplayName("2. Dispensadores con Líquidos (BlockDispenseEvent)")
    class DispenserTests {

        @Test
        @DisplayName("Dispensador en X=6 apuntando al Oeste (hacia X=5) con WATER_BUCKET es cancelado")
        void testDispenserFacingRoadWithWaterCancelled() {
            Block dispenser = createFakeBlock(testWorld, 6, 63, 150, Material.DISPENSER, BlockFace.WEST);
            ItemStack waterBucket = new TestItemStack(Material.WATER_BUCKET);

            BlockDispenseEvent event = new BlockDispenseEvent(dispenser, waterBucket, new Vector(-1, 0, 0));
            listener.onBlockDispense(event);

            assertTrue(event.isCancelled(), "Dispensador apuntando a la carretera con cubo de agua debe cancelarse");
        }

        @Test
        @DisplayName("Dispensador en X=6 apuntando al Este (hacia X=7) con WATER_BUCKET NO es cancelado")
        void testDispenserFacingAwayNotCancelled() {
            Block dispenser = createFakeBlock(testWorld, 6, 63, 150, Material.DISPENSER, BlockFace.EAST);
            ItemStack waterBucket = new TestItemStack(Material.WATER_BUCKET);

            BlockDispenseEvent event = new BlockDispenseEvent(dispenser, waterBucket, new Vector(1, 0, 0));
            listener.onBlockDispense(event);

            assertFalse(event.isCancelled(), "Dispensador apuntando fuera de la carretera no debe cancelarse");
        }

        @Test
        @DisplayName("Dispensador apuntando a la carretera con flecha (ARROW) NO es cancelado")
        void testDispenserWithArrowNotCancelled() {
            Block dispenser = createFakeBlock(testWorld, 6, 63, 150, Material.DISPENSER, BlockFace.WEST);
            ItemStack arrow = new TestItemStack(Material.ARROW);

            BlockDispenseEvent event = new BlockDispenseEvent(dispenser, arrow, new Vector(-1, 0, 0));
            listener.onBlockDispense(event);

            assertFalse(event.isCancelled(), "Dispensar flechas u otros ítems no debe ser cancelado");
        }

        @Test
        @DisplayName("Dispensador bajo Y=62 (en subsuelo o cueva natural) NO es cancelado")
        void testDispenserBelowY62NotCancelled() {
            Block dispenser = createFakeBlock(testWorld, 1, 40, 150, Material.DISPENSER, BlockFace.NORTH);
            ItemStack waterBucket = new TestItemStack(Material.WATER_BUCKET);

            BlockDispenseEvent event = new BlockDispenseEvent(dispenser, waterBucket, new Vector(0, 0, -1));
            listener.onBlockDispense(event);

            assertFalse(event.isCancelled(), "Mecanismos con fluidos bajo Y=62 no deben cancelarse");
        }
    }

    @Nested
    @DisplayName("3. Nivel de Fluidos (FluidLevelChangeEvent)")
    class FluidLevelChangeTests {

        @Test
        @DisplayName("Cambio de nivel de fluido dentro de la carretera se cancela")
        void testFluidLevelChangeInRoadCancelled() {
            Block roadBlock = createFakeBlock(testWorld, 0, 63, 150, Material.WATER, BlockFace.NORTH);
            FluidLevelChangeEvent event = new FluidLevelChangeEvent(roadBlock, null);

            listener.onFluidLevelChange(event);
            assertTrue(event.isCancelled());
        }

        @Test
        @DisplayName("Cambio de nivel de fluido fuera de la carretera NO se cancela")
        void testFluidLevelChangeOutsideRoadNotCancelled() {
            Block outsideBlock = createFakeBlock(testWorld, 10, 63, 150, Material.WATER, BlockFace.NORTH);
            FluidLevelChangeEvent event = new FluidLevelChangeEvent(outsideBlock, null);

            listener.onFluidLevelChange(event);
            assertFalse(event.isCancelled());
        }

        @Test
        @DisplayName("Cambio de nivel de fluido bajo Y=62 (subsuelo) NO se cancela")
        void testFluidLevelChangeBelowY62NotCancelled() {
            Block caveBlock = createFakeBlock(testWorld, 0, 45, 150, Material.WATER, BlockFace.NORTH);
            FluidLevelChangeEvent event = new FluidLevelChangeEvent(caveBlock, null);

            listener.onFluidLevelChange(event);
            assertFalse(event.isCancelled(), "Fluidos naturales bajo Y=62 deben poder cambiar de nivel");
        }
    }
}
