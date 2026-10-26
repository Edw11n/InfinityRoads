package com.infinityroads.protection;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Escuchador integral de protección de bloques para InfinityRoads.
 *
 * GARANTÍAS DE PROTECCIÓN:
 * 1. Cubre la columna vertical completa de las 4 carreteras [world.getMinHeight(), world.getMaxHeight()).
 * 2. Impide rotura, colocación, cubos, pistones, explosiones y modificaciones de bloques por jugadores normales.
 * 3. Permite bypass completo y transparente a administradores con sesión activa (/ir admin on).
 * 4. No bloquea acciones inocuas (comer, disparar arcos, usar escudos, etc.).
 * 5. En explosiones, preserva los bloques de la carretera sin anular el daño a bloques exteriores.
 * 6. En pistones, cancela operaciones que empujen o extraigan bloques hacia/desde el volumen protegido.
 */
public class RoadProtectionListener implements Listener {

    private final RoadProtectionService protectionService;
    private final com.infinityroads.logging.RoadLogger roadLogger;

    public RoadProtectionListener(RoadProtectionService protectionService, com.infinityroads.logging.RoadLogger roadLogger) {
        this.protectionService = Objects.requireNonNull(protectionService, "protectionService no puede ser nulo");
        this.roadLogger = roadLogger;
    }

    public RoadProtectionListener(RoadProtectionService protectionService) {
        this(protectionService, null);
    }

    public RoadProtectionService getProtectionService() {
        return protectionService;
    }

    // =========================================================================
    // 1. Romper y Colocar Bloques
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectionService.canPlayerModify(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            protectionService.notifyProtection(event.getPlayer());
            if (roadLogger != null) {
                roadLogger.debug("[Protection] Rotura bloqueada para jugador %s en (%d, %d, %d) [Material: %s]",
                        event.getPlayer().getName(), event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(), event.getBlock().getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protectionService.canPlayerModify(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            protectionService.notifyProtection(event.getPlayer());
            if (roadLogger != null) {
                roadLogger.debug("[Protection] Colocación bloqueada para jugador %s en (%d, %d, %d) [Material: %s]",
                        event.getPlayer().getName(), event.getBlockPlaced().getX(), event.getBlockPlaced().getY(), event.getBlockPlaced().getZ(), event.getBlockPlaced().getType());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        Player player = event.getPlayer();
        for (BlockState state : event.getReplacedBlockStates()) {
            if (!protectionService.canPlayerModify(player, state.getBlock())) {
                event.setCancelled(true);
                protectionService.notifyProtection(player);
                return;
            }
        }
    }

    // =========================================================================
    // 2. Interacciones de Jugador (Físicas y con Ítems Modificadores)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Player player = event.getPlayer();

        // 2.1 Presión física (pisotear cultivos, huevos de tortuga, placas)
        if (action == Action.PHYSICAL) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && !protectionService.canPlayerModify(player, clicked)) {
                event.setCancelled(true);
            }
            return;
        }

        // 2.2 Clic derecho sobre bloque
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) return;

            ItemStack item = event.getItem();
            if (item == null) return;
            Material mat = item.getType();

            // Modificación directa del bloque clicado (desbastar, aplanar, arar, encender, etc.)
            if (isBlockModifyingItem(mat)) {
                if (!protectionService.canPlayerModify(player, clicked)) {
                    event.setCancelled(true);
                    protectionService.notifyProtection(player);
                    return;
                }
            }

            // Colocación de entidades que ocupan espacio de bloque (armaduras, cristales, marcos, etc.)
            if (isEntityPlacementItem(mat)) {
                Block placedTarget = clicked.getRelative(event.getBlockFace());
                if (!protectionService.canPlayerModify(player, placedTarget)) {
                    event.setCancelled(true);
                    protectionService.notifyProtection(player);
                }
            }
        }
    }

    // =========================================================================
    // 3. Uso de Cubos por Jugadores
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!protectionService.canPlayerModify(event.getPlayer(), target)) {
            event.setCancelled(true);
            protectionService.notifyProtection(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block target = event.getBlockClicked();
        if (!protectionService.canPlayerModify(event.getPlayer(), target)) {
            event.setCancelled(true);
            protectionService.notifyProtection(event.getPlayer());
        }
    }

    // =========================================================================
    // 4. Explosiones (Filtrado Preciso de Bloques)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(protectionService::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(protectionService::isProtected);
    }

    // =========================================================================
    // 5. Pistones y Mecanismos
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        Block piston = event.getBlock();

        // Si la cabeza del pistón se extendería dentro del volumen protegido
        if (protectionService.isProtected(piston.getRelative(direction))) {
            event.setCancelled(true);
            return;
        }

        // Inspeccionar todos los bloques empujados
        for (Block block : event.getBlocks()) {
            if (protectionService.isProtected(block)) {
                event.setCancelled(true);
                return;
            }
            if (protectionService.isProtected(block.getRelative(direction))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace direction = event.getDirection();
        for (Block block : event.getBlocks()) {
            if (protectionService.isProtected(block)) {
                event.setCancelled(true);
                return;
            }
            if (protectionService.isProtected(block.getRelative(direction))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // =========================================================================
    // 6. Fuego, Propagación y Cambios Ambientales
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (protectionService.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!protectionService.canPlayerModify(player, block)) {
            event.setCancelled(true);
            if (player != null) {
                protectionService.notifyProtection(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block block = event.getBlock();
        if (block.getY() >= 62 && protectionService.isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (block.getY() >= 62 && protectionService.isProtected(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (protectionService.isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (event.getEntity() instanceof Player player) {
            if (!protectionService.canPlayerModify(player, block)) {
                event.setCancelled(true);
                protectionService.notifyProtection(player);
            }
        } else {
            if (protectionService.isProtected(block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> protectionService.isProtected(state.getBlock()));
    }

    // =========================================================================
    // 7. Entidades Colgadas (Marcos, Cuadros)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Block block = event.getEntity().getLocation().getBlock();
        if (event instanceof HangingBreakByEntityEvent byEntity && byEntity.getRemover() instanceof Player player) {
            if (!protectionService.canPlayerModify(player, block)) {
                event.setCancelled(true);
                protectionService.notifyProtection(player);
            }
        } else {
            if (protectionService.isProtected(block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!protectionService.canPlayerModify(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            protectionService.notifyProtection(event.getPlayer());
        }
    }

    // =========================================================================
    // 8. Ciclo de Vida del Jugador (Cierre de Mantenimiento al Desconectar)
    // =========================================================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protectionService.getMaintenanceManager().endSession(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // 9. Protección contra Explosiones y Daño de Mobs Hostiles a Jugadores
    // =========================================================================

    /**
     * Protege a cualquier jugador ubicado dentro del corredor de la carretera contra daño
     * de explosiones (creepers, TNT, camas, cristales) y ataques de mobs hostiles o sus proyectiles.
     * La protección depende de la ubicación del jugador que recibe el daño.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Solo protege si el jugador receptor está dentro del corredor protegido
        if (!protectionService.isProtected(player.getLocation())) {
            return;
        }

        DamageCause cause = event.getCause();

        // 1. Inmunidad a explosiones dentro de la carretera (bloques o entidades explosivas)
        if (cause == DamageCause.BLOCK_EXPLOSION || cause == DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
            return;
        }

        // 2. Inmunidad a daño cuerpo a cuerpo o proyectiles provenientes de mobs hostiles
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (isHostileSource(byEntity.getDamager())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Anula efectos de pociones arrojadizas hostiles (brujas, etc.) sobre jugadores dentro de la carretera.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.getPotion().getShooter() instanceof Entity shooter && isHostileEntity(shooter)) {
            for (LivingEntity affected : event.getAffectedEntities()) {
                if (affected instanceof Player player && protectionService.isProtected(player.getLocation())) {
                    event.setIntensity(player, 0.0);
                }
            }
        }
    }

    /**
     * Protege a jugadores en la carretera contra nubes de efectos persistentes de origen hostil (dragón, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (event.getEntity().getSource() instanceof Entity source && isHostileEntity(source)) {
            event.getAffectedEntities().removeIf(entity ->
                    entity instanceof Player player && protectionService.isProtected(player.getLocation())
            );
        }
    }

    /**
     * Determina si una entidad atacante es un mob hostil o un proyectil/ataque derivado de un mob hostil.
     */
    public static boolean isHostileSource(Entity damager) {
        if (damager == null) {
            return false;
        }
        if (isHostileEntity(damager)) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter) {
                return isHostileEntity(shooter);
            }
        }
        if (damager instanceof EvokerFangs fangs) {
            LivingEntity owner = fangs.getOwner();
            return owner != null && isHostileEntity(owner);
        }
        if (damager instanceof AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof Entity source) {
                return isHostileEntity(source);
            }
        }
        return false;
    }

    /**
     * Determina si una entidad es hostil (Enemy, Monster, Ghast, Slime, Shulker, Boss, Hoglin).
     */
    public static boolean isHostileEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof Enemy) {
            return true;
        }
        return entity instanceof Monster
                || entity instanceof Ghast
                || entity instanceof Slime
                || entity instanceof Shulker
                || entity instanceof Boss
                || entity instanceof Hoglin;
    }

    // =========================================================================
    // Utilidades Internas
    // =========================================================================

    private boolean isBlockModifyingItem(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || mat == Material.FLINT_AND_STEEL
                || mat == Material.FIRE_CHARGE
                || mat == Material.BONE_MEAL
                || mat == Material.HONEYCOMB
                || name.endsWith("_DYE")
                || mat == Material.GLOW_INK_SAC
                || mat == Material.INK_SAC
                || mat == Material.SHEARS;
    }

    private boolean isEntityPlacementItem(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return mat == Material.ARMOR_STAND
                || mat == Material.END_CRYSTAL
                || mat == Material.ITEM_FRAME
                || mat == Material.GLOW_ITEM_FRAME
                || mat == Material.PAINTING
                || name.endsWith("_BOAT")
                || name.endsWith("_MINECART")
                || name.endsWith("_SPAWN_EGG");
    }
}
