package baritone.aimassist.system;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.Rotation;
import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class PortalManager {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;
    private final AimAssistModule module;

    public PortalManager(AimAssistModule module, AimAssistConfig config) {
        this.module = module;
        this.config = config;
    }

    /**
     * Calculate Nether coords from Overworld coords (divide by 8)
     */
    public BlockPos overworldToNether(BlockPos overworld) {
        return new BlockPos(overworld.getX() / 8, overworld.getY(), overworld.getZ() / 8);
    }

    /**
     * Calculate Overworld coords from Nether coords (multiply by 8)
     */
    public BlockPos netherToOverworld(BlockPos nether) {
        return new BlockPos(nether.getX() * 8, nether.getY(), nether.getZ() * 8);
    }

    /**
     * Build a portal at target location that links to a specific coordinate in the other dimension.
     * @param targetDim "overworld" or "nether" — where to build the portal
     * @param linkTo coordinate in the OTHER dimension that this portal should link to
     */
    public boolean buildLinkedPortal(String targetDim, BlockPos linkTo) {
        if (mc.player == null || mc.level == null) return false;

        BlockPos buildAt;
        String currentDim = getCurrentDimension();

        if (targetDim.equalsIgnoreCase("nether")) {
            // Building in nether to link to overworld coordinates
            if (currentDim.equals("overworld")) {
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §7You must be in the Nether to build there!"
                    )
                );
                return false;
            }
            buildAt = overworldToNether(linkTo);
        } else {
            // Building in overworld to link to nether coordinates
            if (currentDim.equals("nether")) {
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §7You must be in the Overworld to build there!"
                    )
                );
                return false;
            }
            buildAt = netherToOverworld(linkTo);
        }

        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7Building portal at §e" +
                buildAt.getX() + " " + buildAt.getY() + " " + buildAt.getZ() +
                " §7linking to " + targetDim + " §e" +
                linkTo.getX() + " " + linkTo.getY() + " " + linkTo.getZ()
            )
        );

        return buildPortalAt(buildAt);
    }

    /**
     * Full portal construction: gather resources, navigate, build frame, light it.
     */
    public boolean buildPortalAt(BlockPos targetPos) {
        if (mc.player == null || mc.level == null) return false;

        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7Starting portal construction..."
            )
        );

        // Phase 1: Gather resources
        if (!ensurePortalResources()) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §cFailed to gather portal resources"
                )
            );
            return false;
        }

        // Phase 2: Navigate to target
        BaritoneAPI.getProvider().getPrimaryBaritone()
            .getCustomGoalProcess().setGoalAndPath(new GoalNear(targetPos, 3));

        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7Navigating to build site..."
            )
        );

        // Phase 3: Build the frame (wait until close enough)
        if (mc.player.distanceToSqr(Vec3.atCenterOf(targetPos)) > 100) {
            return true; // Will continue on next tick when closer
        }

        return constructPortal(targetPos);
    }

    /**
     * Main portal construction logic - builds a 4x5 obsidian frame and lights it.
     */
    public boolean constructPortal(BlockPos targetPos) {
        if (mc.player == null || mc.level == null) return false;

        // Obsidian frame layout (4 wide, 5 tall, interior 2x3):
        // Bottom: 4 obsidian
        // Sides: 2 obsidian each (total 4)
        // Top: 4 obsidian
        // Total: 10 obsidian

        List<BlockPos> frameBlocks = new ArrayList<>();
        int x = targetPos.getX(), y = targetPos.getY(), z = targetPos.getZ();

        // Bottom row
        frameBlocks.add(new BlockPos(x, y, z));
        frameBlocks.add(new BlockPos(x + 1, y, z));
        frameBlocks.add(new BlockPos(x + 2, y, z));
        frameBlocks.add(new BlockPos(x + 3, y, z));

        // Top row
        frameBlocks.add(new BlockPos(x, y + 4, z));
        frameBlocks.add(new BlockPos(x + 1, y + 4, z));
        frameBlocks.add(new BlockPos(x + 2, y + 4, z));
        frameBlocks.add(new BlockPos(x + 3, y + 4, z));

        // Left column
        frameBlocks.add(new BlockPos(x, y + 1, z));
        frameBlocks.add(new BlockPos(x, y + 2, z));
        frameBlocks.add(new BlockPos(x, y + 3, z));

        // Right column
        frameBlocks.add(new BlockPos(x + 3, y + 1, z));
        frameBlocks.add(new BlockPos(x + 3, y + 2, z));
        frameBlocks.add(new BlockPos(x + 3, y + 3, z));

        // Check what blocks are already in place
        boolean hasObsidian = true;
        for (BlockPos bp : frameBlocks) {
            if (!mc.level.getBlockState(bp).is(Blocks.OBSIDIAN) &&
                !mc.level.getBlockState(bp).is(Blocks.CRYING_OBSIDIAN)) {
                hasObsidian = false;
                break;
            }
        }

        if (!hasObsidian) {
            // Place obsidian for each missing block
            if (!ensureItemInHand(Items.OBSIDIAN) && !ensureItemInHand(Items.CRYING_OBSIDIAN)) {
                // No obsidian in inventory
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §cNo obsidian! Mining some..."
                    )
                );
                // Try to get obsidian via baritone
                BaritoneAPI.getSettings().allowBreak.value = true;
                BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getMineProcess().mineByName("obsidian");
                return false;
            }

            // Place frame blocks
            for (BlockPos bp : frameBlocks) {
                if (mc.level.getBlockState(bp).isAir() || mc.level.getBlockState(bp).canBeReplaced()) {
                    placeBlock(bp);
                }
            }
        }

        // Phase 4: Light the portal
        return lightPortal(targetPos);
    }

    /**
     * Light the portal by any means possible:
     * 1. Flint and steel
     * 2. Fire charge
     * 3. Fire aspect sword + wood
     * 4. Lava bucket + wood
     */
    public boolean lightPortal(BlockPos portalPos) {
        if (mc.player == null || mc.level == null) return false;

        // Check if already lit
        for (int dx = 0; dx <= 3; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos check = portalPos.offset(dx, dy, 0);
                if (mc.level.getBlockState(check).is(Blocks.NETHER_PORTAL)) {
                    mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Shinigami] §aPortal already active!"
                        )
                    );
                    return true;
                }
            }
        }

        // Method 1: Flint and steel
        if (ensureItemInHand(Items.FLINT_AND_STEEL)) {
            return tryLightPortal(portalPos, InteractionHand.MAIN_HAND, Items.FLINT_AND_STEEL);
        }

        // Method 2: Fire charge
        if (ensureItemInHand(Items.FIRE_CHARGE)) {
            return tryLightPortal(portalPos, InteractionHand.MAIN_HAND, Items.FIRE_CHARGE);
        }

        // Method 3: Fire aspect sword + wood
        if (hasFireAspectSword()) {
            // Place a flammable block and light it with the sword
            return lightWithFireAspect(portalPos);
        }

        // Method 4: Lava + wood
        if (ensureItemInHand(Items.LAVA_BUCKET)) {
            return lightWithLava(portalPos);
        }

        // None of the above - gather resources
        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7No fire source. Gathering resources..."
            )
        );
        return gatherAndLight(portalPos);
    }

    private boolean tryLightPortal(BlockPos portalPos, InteractionHand hand, net.minecraft.world.item.Item item) {
        if (mc.gameMode == null) return false;

        // Click on the bottom-center of the portal frame
        BlockPos lightPos = portalPos.offset(1, 0, 0);
        Direction face = Direction.UP;

        // Try different positions inside the frame
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos check = portalPos.offset(dx, dy, 0);
                if (mc.level.getBlockState(check).isAir()) {
                    lightPos = check;
                    break;
                }
            }
        }

        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(lightPos), face, lightPos, false
        );

        try {
            mc.gameMode.useItemOn(mc.player, hand, hit);
        } catch (Exception e) {
            return false;
        }

        // Verify it lit
        for (int dx = 1; dx <= 2; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                if (mc.level.getBlockState(portalPos.offset(dx, dy, 0)).is(Blocks.NETHER_PORTAL)) {
                    mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Shinigami] §aPortal lit!"
                        )
                    );
                    return true;
                }
            }
        }

        return false;
    }

    private boolean lightWithFireAspect(BlockPos portalPos) {
        if (mc.player == null || mc.level == null) return false;

        // Place a wood plank inside the frame
        if (ensureItemInHand(Items.OAK_PLANKS) || ensureItemInHand(Items.SPRUCE_PLANKS) ||
            ensureItemInHand(Items.BIRCH_PLANKS) || ensureItemInHand(Items.JUNGLE_PLANKS)) {
            BlockPos placePos = portalPos.offset(1, 1, 0);
            if (mc.level.getBlockState(placePos).isAir()) {
                placeBlock(placePos);
            }

            // Equip fire aspect sword and hit the wood
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(ItemTags.SWORDS)) {
                    var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                    var holder = registry.get(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT).orElseThrow();
                    if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0) {
                        mc.player.getInventory().setSelectedSlot(i);
                        // Attack the wood block to ignite it
                        mc.gameMode.startDestroyBlock(placePos, Direction.UP);

                        // Fire should spread to portal
                        mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "§6[Shinigami] §7Fire aspect lit!"
                            )
                        );
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean lightWithLava(BlockPos portalPos) {
        if (mc.player == null || mc.level == null) return false;

        // Place a flammable block, pour lava next to it
        if (ensureItemInHand(Items.OAK_PLANKS)) {
            BlockPos woodPos = portalPos.offset(1, 1, 0);
            if (mc.level.getBlockState(woodPos).isAir()) {
                placeBlock(woodPos);
            }

            // Switch to lava bucket and place above/beside the wood
            if (ensureItemInHand(Items.LAVA_BUCKET)) {
                BlockPos lavaPos = woodPos.above();
                if (mc.level.getBlockState(lavaPos).isAir()) {
                    placeBlock(lavaPos);
                    mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Shinigami] §7Lava placed - fire will spread!"
                        )
                    );
                    return true;
                }
            }
        }
        return false;
    }

    private boolean gatherAndLight(BlockPos portalPos) {
        if (mc.player == null) return false;

        // Gather flint + iron for flint and steel
        if (!hasItem(Items.FLINT_AND_STEEL)) {
            if (hasItem(Items.IRON_INGOT) && hasItem(Items.FLINT)) {
                // Craft flint and steel
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §7Crafting flint and steel..."
                    )
                );
                // In a real system, this would call the crafting API
            } else {
                // Mine iron and gravel for flint
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §7Mining iron for flint and steel..."
                    )
                );
                BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getMineProcess().mineByName("iron_ore");
                return false;
            }
        }

        return false;
    }

    /**
     * Link two existing portals.
     * @param sourcePos position of portal 1
     * @param targetPos position of portal 2 (in other dimension)
     * @param fromDim dimension of sourcePos
     */
    public boolean linkPortals(BlockPos sourcePos, BlockPos targetPos, String fromDim) {
        if (mc.player == null || mc.level == null) return false;

        BlockPos expectedTarget;
        if (fromDim.equalsIgnoreCase("nether")) {
            expectedTarget = netherToOverworld(sourcePos);
        } else {
            expectedTarget = overworldToNether(sourcePos);
        }

        // Check if already linked
        double dist = Math.sqrt(expectedTarget.distSqr(targetPos));
        if (dist < 128) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §aPortal already linked (within " + (int)dist + " blocks)"
                )
            );
            return true;
        }

        // Rebuild the source portal at the correct position
        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7Rebuilding portal for proper link..."
            )
        );

        BlockPos correctPos;
        if (fromDim.equalsIgnoreCase("nether")) {
            correctPos = overworldToNether(targetPos);
        } else {
            correctPos = netherToOverworld(targetPos);
        }

        return buildPortalAt(correctPos);
    }

    private boolean ensurePortalResources() {
        boolean hasObsidian = false;
        boolean hasFireSource = false;

        for (int i = 0; i < (mc.player != null ? mc.player.getInventory().getContainerSize() : 0); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.OBSIDIAN) || stack.is(Items.CRYING_OBSIDIAN)) {
                if (stack.getCount() >= 10) hasObsidian = true;
            }
            if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
                hasFireSource = true;
            }
        }

        if (!hasObsidian) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Mining obsidian (need 10)..."
                )
            );
            BaritoneAPI.getSettings().allowBreak.value = true;
            BaritoneAPI.getSettings().pauseMiningForFallingBlocks.value = false;
            BaritoneAPI.getProvider().getPrimaryBaritone()
                .getMineProcess().mineByName("obsidian");
            return false;
        }

        if (!hasFireSource) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Gathering fire source..."
                )
            );
            // Try to get flint and steel or fire charge
            BaritoneAPI.getProvider().getPrimaryBaritone()
                .getMineProcess().mineByName("iron_ore");
            return false;
        }

        return true;
    }

    private boolean ensureItemInHand(net.minecraft.world.item.Item item) {
        if (mc.player == null) return false;
        if (mc.player.getMainHandItem().is(item)) return true;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) {
                mc.player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private boolean hasItem(net.minecraft.world.item.Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    private boolean hasFireAspectSword() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS)) {
                var ench = net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantmentsForCrafting(stack);
                var registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                var holder = registry.get(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT).orElseThrow();
                if (ench.getLevel(holder) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void placeBlock(BlockPos pos) {
        if (mc.gameMode == null || mc.player == null) return;
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(pos),
            Direction.UP,
            pos,
            false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private String getCurrentDimension() {
        if (mc.level == null) return "unknown";
        var dim = mc.level.dimension();
        if (dim == Level.NETHER) return "nether";
        if (dim == Level.OVERWORLD) return "overworld";
        return "end";
    }
}
