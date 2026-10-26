package com.infinityroads.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Escuchador reactivo de contención de fluidos para InfinityRoads.
 *
 * GARANTÍAS DE CONTENCIÓN:
 * 1. Impide que agua o lava procedentes del exterior fluyan hacia el volumen protegido de 11 bloques.
 * 2. Bloquea dispensadores que intenten descargar cubos de agua, lava o nieve en polvo hacia la carretera.
 * 3. Los fluidos fuera del corredor fluyen y se comportan con total normalidad.
 * 4. Cero mutaciones o alteraciones en bloques con Y < 62.
 * 5. Cero tareas periódicas de inspección o borrado continuo; respuesta 100% reactiva por eventos.
 * 6. No destruye ni modifica los bloques fuente externos.
 */
public class FluidContainmentListener implements Listener {

    private final RoadProtectionService protectionService;
    private final com.infinityroads.logging.RoadLogger roadLogger;

    public FluidContainmentListener(RoadProtectionService protectionService, com.infinityroads.logging.RoadLogger roadLogger) {
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService no puede ser nulo");
        this.roadLogger = roadLogger;
    }

    public FluidContainmentListener(RoadProtectionService protectionService) {
        this(protectionService, null);
    }

    public RoadProtectionService getProtectionService() {
        return protectionService;
    }

    /**
     * Intercepta la propagación y flujo de fluidos (BlockFromToEvent).
     *
     * Reglas:
     * - Si el origen (fromBlock) está en el corredor protegido con Y >= 62, se cancela para evitar
     *   que líquidos colocados dentro se propaguen a ningún bloque (sin bypass administrativo).
     * - Si el destino (toBlock) está en el corredor protegido con Y >= 62, se cancela para evitar
     *   que líquidos exteriores penetren en la carretera.
     * - Por debajo de Y=62, no se cancela el flujo natural bajo el pavimento.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (toBlock == null || !protectionService.isTargetWorld(toBlock.getWorld())) {
            return;
        }

        Block fromBlock = event.getBlock();
        if (fromBlock == null) {
            return;
        }

        // 1. Líquido que intenta propagarse DESDE dentro de la carretera en Y >= 62 (incluso colocado por admin)
        if (fromBlock.getY() >= 62 && protectionService.isProtected(fromBlock)) {
            event.setCancelled(true);
            if (roadLogger != null) {
                roadLogger.debug("[Fluids] Propagación desde carretera bloqueada en (%d, %d, %d)", fromBlock.getX(), fromBlock.getY(), fromBlock.getZ());
            }
            return;
        }

        // 2. Líquido que intenta penetrar HACIA dentro de la carretera en Y >= 62
        if (toBlock.getY() >= 62 && protectionService.isProtected(toBlock)) {
            event.setCancelled(true);
            if (roadLogger != null) {
                roadLogger.debug("[Fluids] Propagación hacia carretera bloqueada en (%d, %d, %d)", toBlock.getX(), toBlock.getY(), toBlock.getZ());
            }
        }
    }

    /**
     * Intercepta la descarga de cubos de fluidos por dispensadores (BlockDispenseEvent).
     * Si un dispensador apunta directamente hacia el corredor protegido en Y >= 62, cancela la descarga.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Material type = item.getType();
        if (type != Material.WATER_BUCKET && type != Material.LAVA_BUCKET && type != Material.POWDER_SNOW_BUCKET) {
            return;
        }

        Block dispenserBlock = event.getBlock();
        if (dispenserBlock == null || !protectionService.isTargetWorld(dispenserBlock.getWorld())) {
            return;
        }

        if (dispenserBlock.getBlockData() instanceof Directional directional) {
            Block targetBlock = dispenserBlock.getRelative(directional.getFacing());
            if (targetBlock.getY() >= 62 && protectionService.isProtected(targetBlock)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Intercepta cambios dinámicos de nivel de fluidos dentro de la columna protegida en Y >= 62.
     * Los fluidos bajo Y=62 cambian de nivel y fluyen con total normalidad.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidLevelChange(FluidLevelChangeEvent event) {
        Block block = event.getBlock();
        if (block == null || !protectionService.isTargetWorld(block.getWorld())) {
            return;
        }

        if (block.getY() >= 62 && protectionService.isProtected(block)) {
            event.setCancelled(true);
        }
    }
}
