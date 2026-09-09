package shinigami.tags;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ResourceManager {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    // Recipe cache
    private final Map<String, List<String>> craftingRecipes = new HashMap<>();

    // Item aliases
    private static final Map<String, String> ITEM_ALIASES = new HashMap<>();

    // Crop to seed mapping
    private static final Map<String, SeedInfo> CROPS = new HashMap<>();

    public ResourceManager() {
        initRecipes();
        initAliases();
        initCrops();
    }

    private void initRecipes() {
        // PAPER: 3 sugarcane → 3 paper
        craftingRecipes.put("paper", Arrays.asList("sugar_cane", "sugar_cane", "sugar_cane"));

        // STICK: 2 planks → 4 sticks
        // planks come from logs
        craftingRecipes.put("stick", Arrays.asList("oak_log", "oak_log"));
        craftingRecipes.put("stick_from_any", Arrays.asList("any_log", "any_log"));

        // SUGAR: 1 sugarcane → 1 sugar
        craftingRecipes.put("sugar", Arrays.asList("sugar_cane"));

        // BREAD: 3 wheat → 1 bread
        craftingRecipes.put("bread", Arrays.asList("wheat", "wheat", "wheat"));

        // COOKED_BEEF: 1 beef + fuel → 1 cooked_beef (smelting)
        craftingRecipes.put("cooked_beef", Arrays.asList("beef"));
        craftingRecipes.put("cooked_porkchop", Arrays.asList("porkchop"));
        craftingRecipes.put("cooked_chicken", Arrays.asList("chicken"));
        craftingRecipes.put("cooked_mutton", Arrays.asList("mutton"));
        craftingRecipes.put("cooked_cod", Arrays.asList("cod"));
        craftingRecipes.put("cooked_salmon", Arrays.asList("salmon"));

        // TORCH: 1 coal + 1 stick → 4 torches
        craftingRecipes.put("torch", Arrays.asList("coal", "stick"));

        // TNT: 5 gunpowder + 4 sand → 1 TNT
        craftingRecipes.put("tnt", Arrays.asList("gunpowder", "gunpowder", "gunpowder", "gunpowder", "gunpowder",
            "sand", "sand", "sand", "sand"));
    }

    private void initAliases() {
        ITEM_ALIASES.put("logs", "oak_log,spruce_log,birch_log,jungle_log,acacia_log,dark_oak_log,mangrove_log,cherry_log");
        ITEM_ALIASES.put("planks", "oak_planks,spruce_planks,birch_planks,jungle_planks,acacia_planks,dark_oak_planks");
        ITEM_ALIASES.put("stone_tools", "stone_pickaxe,stone_axe,stone_shovel,stone_hoe,stone_sword");
        ITEM_ALIASES.put("iron_tools", "iron_pickaxe,iron_axe,iron_shovel,iron_hoe,iron_sword");
        ITEM_ALIASES.put("food", "beef,porkchop,chicken,mutton,rabbit,cod,salmon,potato,carrot,bread,cooked_beef,cooked_porkchop,cooked_chicken");
    }

    private void initCrops() {
        CROPS.put("wheat", new SeedInfo("wheat_seeds", Blocks.WHEAT, Items.WHEAT, Items.WHEAT_SEEDS));
        CROPS.put("carrot", new SeedInfo("carrot", Blocks.CARROTS, Items.CARROT, Items.CARROT));
        CROPS.put("potato", new SeedInfo("potato", Blocks.POTATOES, Items.POTATO, Items.POTATO));
        CROPS.put("beetroot", new SeedInfo("beetroot_seeds", Blocks.BEETROOTS, Items.BEETROOT, Items.BEETROOT_SEEDS));
        CROPS.put("sugar_cane", new SeedInfo("sugar_cane", Blocks.SUGAR_CANE, Items.SUGAR_CANE, Items.SUGAR_CANE));
        CROPS.put("cactus", new SeedInfo("cactus", Blocks.CACTUS, Items.CACTUS, Items.CACTUS));
        CROPS.put("bamboo", new SeedInfo("bamboo", Blocks.BAMBOO, Items.BAMBOO, Items.BAMBOO));
    }

    // === MAIN API ===

    /**
     * Get the total count of an item in inventory (all slots).
     */
    public int getItemCount(String itemId) {
        if (mc.player == null) return 0;
        Item target = findItem(itemId);
        if (target == null) return 0;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Check if we can craft the given item.
     * Returns the chain of items needed.
     */
    public List<String> getCraftingChain(String targetItem) {
        List<String> chain = new ArrayList<>();
        chain.add(targetItem);

        // Check if item can be crafted directly
        List<String> ingredients = craftingRecipes.get(targetItem);
        if (ingredients == null) {
            // Try to find raw source
            String source = getRawSourceFor(targetItem);
            if (source != null) {
                chain.add(source);
            }
            return chain;
        }

        // For each ingredient, check if it also needs crafting
        for (String ingredient : ingredients) {
            if (ingredient.equals("any_log")) {
                chain.add("oak_log");
            } else if (!hasItem(ingredient)) {
                List<String> subChain = getCraftingChain(ingredient);
                chain.addAll(subChain);
            }
        }

        return chain;
    }

    /**
     * Get the raw source that produces the given item.
     * e.g., "gunpowder" → creepers, "sugar_cane" → sugar_cane plant
     */
    public String getRawSourceFor(String item) {
        return switch (item) {
            case "gunpowder" -> "creeper";
            case "sugar_cane" -> "sugar_cane";
            case "wheat" -> "wheat";
            case "beef" -> "cow";
            case "porkchop" -> "pig";
            case "chicken" -> "chicken";
            case "mutton" -> "sheep";
            case "cod" -> "cod";
            case "salmon" -> "salmon";
            case "rabbit" -> "rabbit";
            case "leather" -> "cow";
            case "feather" -> "chicken";
            case "string" -> "spider";
            case "bone" -> "skeleton";
            case "rotten_flesh" -> "zombie";
            case "spider_eye" -> "spider";
            case "ender_pearl" -> "enderman";
            case "blaze_rod" -> "blaze";
            case "coal" -> "coal_ore";
            case "iron_ingot" -> "iron_ore";
            case "gold_ingot" -> "gold_ore";
            case "diamond" -> "diamond_ore";
            case "emerald" -> "emerald_ore";
            case "netherite_scrap" -> "ancient_debris";
            case "oak_log" -> "oak_tree";
            case "spruce_log" -> "spruce_tree";
            default -> null;
        };
    }

    /**
     * Check if the item is in inventory.
     */
    public boolean hasItem(String itemId) {
        return getItemCount(itemId) > 0;
    }

    /**
     * Get the best entity to kill for a given item.
     * Returns entity type name or null.
     */
    public String getEntitySourceFor(String item) {
        return switch (item) {
            case "gunpowder" -> "creeper";
            case "beef" -> "cow";
            case "porkchop" -> "pig";
            case "chicken" -> "chicken";
            case "mutton" -> "sheep";
            case "cod" -> "cod";
            case "salmon" -> "salmon";
            case "rabbit" -> "rabbit";
            case "leather" -> "cow";
            case "feather" -> "chicken";
            case "string" -> "spider";
            case "bone" -> "skeleton";
            case "ender_pearl" -> "enderman";
            case "blaze_rod" -> "blaze";
            case "spider_eye" -> "spider";
            case "rotten_flesh" -> "zombie";
            default -> null;
        };
    }

    /**
     * Mine the source block for an item.
     * Returns whether the task was started.
     */
    public boolean mineResource(String item) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null || mc.player == null) return false;

        String blockToMine = switch (item) {
            case "coal" -> "coal_ore";
            case "iron_ingot" -> "iron_ore";
            case "gold_ingot" -> "gold_ore";
            case "diamond" -> "diamond_ore";
            case "emerald" -> "emerald_ore";
            case "redstone" -> "redstone_ore";
            case "lapis_lazuli" -> "lapis_ore";
            case "copper_ingot" -> "copper_ore";
            case "netherite_scrap" -> "ancient_debris";
            case "nether_quartz" -> "nether_quartz_ore";
            case "stone" -> "stone";
            case "cobblestone" -> "cobblestone";
            case "oak_log" -> "oak_log";
            case "spruce_log" -> "spruce_log";
            case "birch_log" -> "birch_log";
            default -> null;
        };

        if (blockToMine == null) return false;

        BaritoneAPI.getSettings().allowBreak.value = true;
        baritone.getMineProcess().mineByName(blockToMine);
        return true;
    }

    /**
     * Harvest and replant a crop.
     */
    public void harvestCrop(String cropName) {
        SeedInfo info = CROPS.get(cropName);
        if (info == null || mc.player == null) return;

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) return;

        // Find mature crop nearby
        BlockPos cropPos = findMatureCrop(info.block, 10);
        if (cropPos != null) {
            // Break the crop
            baritone.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalNear(cropPos, 2)
            );

            // Replant with seed if we have it
            int seedSlot = findItemInHotbar(info.seed);
            if (seedSlot != -1) {
                mc.player.getInventory().setSelectedSlot(seedSlot);
                BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(cropPos),
                    Direction.UP,
                    cropPos,
                    false
                );
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            }
        } else {
            // No mature crops - check if we need to plant
            if (hasItem(info.seed.getDescriptionId())) {
                plantCrop(cropName);
            } else {
                // Find wild version
                findAndHarvestWild(cropName);
            }
        }
    }

    /**
     * Plant a crop near water.
     */
    public void plantCrop(String cropName) {
        SeedInfo info = CROPS.get(cropName);
        if (info == null || mc.player == null) return;

        // Find suitable farmland near water
        BlockPos farmPos = findFarmland(5);
        if (farmPos == null) {
            // Create farmland
            createFarmland();
            farmPos = mc.player.blockPosition().below();
        }

        // Equip seeds
        int seedSlot = findItemInInventory(info.seed);
        if (seedSlot == -1) return;

        if (seedSlot < 9) {
            mc.player.getInventory().setSelectedSlot(seedSlot);
        } else {
            // Swap to hotbar
            int hotbarSlot = findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    seedSlot,
                    hotbarSlot,
                    net.minecraft.world.inventory.ContainerInput.SWAP,
                    mc.player
                );
                mc.player.getInventory().setSelectedSlot(hotbarSlot);
            }
        }

        // Plant on farmland
        BlockPos plantPos = farmPos.above();
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(plantPos),
            Direction.UP,
            plantPos,
            false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Find and harvest wild plants (e.g., wild sugarcane near water).
     */
    public void findAndHarvestWild(String cropName) {
        SeedInfo info = CROPS.get(cropName);
        if (info == null || mc.player == null) return;

        BlockPos wildPos = findWildCrop(info.block, 20);
        if (wildPos != null) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalNear(wildPos, 2)
                );
            }
            // Break the plant
            mc.gameMode.destroyBlock(wildPos);
        }
    }

    /**
     * Find a villager with a specific profession and trade with them.
     * Returns true if trading was initiated.
     */
    public boolean tradeWithVillager(String wantedItem) {
        if (mc.player == null || mc.level == null) return false;

        Villager bestVillager = null;
        double closest = 10;

        net.minecraft.world.phys.AABB villagerBox = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 12, mc.player.getY() - 12, mc.player.getZ() - 12,
            mc.player.getX() + 12, mc.player.getY() + 12, mc.player.getZ() + 12
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, villagerBox)) {
            if (e instanceof Villager villager && villager.isAlive()) {
                // Check if villager has the trade we want
                var offers = villager.getOffers();
                if (offers != null) {
                    for (var offer : offers) {
                        String resultItem = offer.getResult().getItem().getDescriptionId();
                        if (resultItem.contains(wantedItem)) {
                            double dist = mc.player.distanceTo(villager);
                            if (dist < closest) {
                                closest = dist;
                                bestVillager = villager;
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (bestVillager != null) {
            // Path to villager
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalNear(bestVillager.blockPosition(), 2)
                );
            }

            // Right-click to open trade GUI
            if (mc.player.distanceTo(bestVillager) < 5) {
                mc.player.connection.send(
                    new net.minecraft.network.protocol.game.ServerboundInteractPacket(
                        bestVillager.getId(),
                        InteractionHand.MAIN_HAND,
                        net.minecraft.world.phys.Vec3.ZERO,
                        true
                    )
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Automate the full trade cycle for an item.
     * Handles: gather resources → craft → trade
     */
    public boolean automateTradeCycle(String tradeItem, int quantity) {
        if (mc.player == null) return false;

        // 1. Check how many we have
        int currentCount = getItemCount(tradeItem);

        // 2. Determine what's needed to craft the trade item
        List<String> chain = getCraftingChain(tradeItem);

        // 3. Gather missing resources
        boolean allResourcesReady = true;
        for (String resource : chain) {
            if (!resource.equals(tradeItem) && !hasItem(resource) && !resource.contains("_log")) {
                allResourcesReady = false;
                // Start gathering
                String source = getRawSourceFor(resource);
                if (source != null && getEntitySourceFor(resource) != null) {
                    // Kill entity for resource
                    module.getSmartExecutor().addTask(
                        SmartTaskExecutor.TaskType.KILL_MOB,
                        source,
                        SmartTaskExecutor.TaskPriority.PRIMARY
                    );
                } else if (source != null) {
                    // Mine resource
                    mineResource(resource);
                }
            }
        }

        // 4. If logs needed, cut trees
        if (chain.stream().anyMatch(r -> r.contains("_log"))) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
                BaritoneAPI.getSettings().allowBreak.value = true;
                BaritoneAPI.getSettings().allowPlace.value = true;
                baritone.getMineProcess().mineByName("oak_log,spruce_log,birch_log");
            }
        }

        // 5. Craft the item
        if (craftItem(tradeItem, quantity)) {
            // 6. Trade with villager
            return tradeWithVillager(tradeItem);
        }

        return false;
    }

    /**
     * Craft an item. Supports shapeless recipes.
     * Returns true if crafting started or completed.
     */
    public boolean craftItem(String itemId, int count) {
        if (mc.player == null) return false;

        List<String> ingredients = craftingRecipes.get(itemId);
        if (ingredients == null) return false;

        // Check if we have the ingredients
        for (String ing : ingredients) {
            if (!ing.equals("any_log") && !hasItem(ing)) {
                return false; // Missing ingredients
            }
        }

        // Find crafting table
        BlockPos tablePos = findNearestBlock(Blocks.CRAFTING_TABLE, 5);
        if (tablePos == null) {
            // Use inventory crafting (2x2)
            // Simple recipes only
            return craftInInventory(itemId, ingredients, count);
        }

        // Use crafting table
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null) {
            baritone.getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalNear(tablePos, 2)
            );
        }

        // Open crafting table
        if (mc.player.distanceToSqr(Vec3.atCenterOf(tablePos)) < 16) {
            mc.player.connection.send(
                new net.minecraft.network.protocol.game.ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false),
                    0
                )
            );

            // Place items in crafting grid (simplified)
            // In a real implementation, this would handle slot placement
            return true;
        }

        return false;
    }

    // === INTERNAL HELPERS ===

    private boolean craftInInventory(String itemId, List<String> ingredients, int count) {
        // Simplified: just swaps ingredients into crafting grid
        // Real implementation would handle exact slot positions
        return false;
    }

    private Item findItem(String itemId) {
        String clean = itemId.toLowerCase().replace(" ", "_");
        // Check items registry
        Item item = BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(clean)).orElseThrow().value();
        if (item != Items.AIR) return item;

        // Check aliases
        String alias = ITEM_ALIASES.get(clean);
        if (alias != null) {
            String first = alias.split(",")[0];
            return BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(first)).orElseThrow().value();
        }

        return null;
    }

    private int findItemInHotbar(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private int findItemInInventory(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private static class SeedInfo {
        final String seedId;
        final net.minecraft.world.level.block.Block block;
        final Item harvest;
        final Item seed;

        SeedInfo(String seedId, net.minecraft.world.level.block.Block block, Item harvest, Item seed) {
            this.seedId = seedId;
            this.block = block;
            this.harvest = harvest;
            this.seed = seed;
        }
    }

    private BlockPos findMatureCrop(net.minecraft.world.level.block.Block crop, int radius) {
        if (mc.level == null || mc.player == null) return null;
        BlockPos center = mc.player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.is(crop)) {
                        // Check if mature (age property = 7 for most crops)
                        IntegerProperty ageProp = IntegerProperty.create("age", 0, 7);
                        if (state.hasProperty(ageProp)) {
                            int age = state.getValue(ageProp);
                            if (age >= 7) return pos;
                        } else {
                            return pos; // No age property, just return
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findWildCrop(net.minecraft.world.level.block.Block crop, int radius) {
        if (mc.level == null || mc.player == null) return null;
        BlockPos center = mc.player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.is(crop)) return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findFarmland(int radius) {
        if (mc.level == null || mc.player == null) return null;
        BlockPos center = mc.player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos pos = center.offset(dx, -1, dz);
                if (mc.level.getBlockState(pos).is(Blocks.FARMLAND)) {
                    BlockPos above = pos.above();
                    if (mc.level.getBlockState(above).isAir()) return pos;
                }
            }
        }
        return null;
    }

    private void createFarmland() {
        if (mc.player == null || mc.level == null) return;
        BlockPos target = mc.player.blockPosition().below();

        // Till the ground with hoe
        int hoeSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof HoeItem) {
                hoeSlot = i;
                break;
            }
        }
        if (hoeSlot == -1) return;

        mc.player.getInventory().setSelectedSlot(hoeSlot);
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(target),
            Direction.UP,
            target,
            false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private BlockPos findNearestBlock(net.minecraft.world.level.block.Block target, int radius) {
        if (mc.level == null || mc.player == null) return null;
        BlockPos playerPos = mc.player.blockPosition();
        double closest = radius * radius;
        BlockPos result = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = playerPos.offset(dx, dy, dz);
                    if (mc.level.getBlockState(check).is(target)) {
                        double dist = playerPos.distSqr(check);
                        if (dist < closest) {
                            closest = dist;
                            result = check;
                        }
                    }
                }
            }
        }
        return result;
    }

    private int findEmptyHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }
}
