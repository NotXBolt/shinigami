package shinigami.tags;

import baritone.api.BaritoneAPI;
import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import shinigami.tags.SmartTaskExecutor.Task;
import shinigami.tags.SmartTaskExecutor.TaskType;
import shinigami.tags.SmartTaskExecutor.TaskPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Breaks down high-level tasks into atomic steps.
 * e.g., "trade paper 64" → [gather sugarcane, craft paper, find villager, trade]
 * e.g., "kill Notch" → [path to Notch, engage combat, finish]
 * e.g., "get gunpowder 10" → [find creeper, kill safely, collect, repeat]
 */
public class TaskPlanner {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final ResourceManager resources = new ResourceManager();

    public TaskPlanner() {}

    /**
     * Parse a user command like "get gunpowder 10" or "trade paper 64" or "kill Notch"
     * and return a list of atomic tasks to execute.
     */
    public List<Task> parseCommand(String input) {
        String[] parts = input.toLowerCase().split("\\s+");
        if (parts.length < 2) return List.of();

        String command = parts[0];
        String target = parts[1];
        int quantity = 1;

        // Parse quantity
        if (parts.length >= 3) {
            try {
                quantity = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                // parts[2] is not a number, could be part of the target name
                target = parts[1] + "_" + parts[2];
            }
        }
        if (parts.length >= 4) {
            try {
                quantity = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {}
        }

        return switch (command) {
            case "get" -> planGetItem(target, quantity);
            case "kill" -> planKill(target);
            case "chase" -> planChase(target);
            case "mine" -> planMine(target, quantity);
            case "craft" -> planCraft(target, quantity);
            case "trade" -> planTrade(target, quantity);
            case "farm" -> planFarm(target, quantity);
            case "explore" -> planExplore(target);
            case "defend" -> planDefend(target);
            default -> List.of();
        };
    }

    /**
     * Plan "get <item> <quantity>"
     * e.g., "get gunpowder 10" → kill 10 creepers
     * e.g., "get paper 64" → gather sugarcane, craft paper
     * e.g., "get diamond" → mine diamond ore
     */
    private List<Task> planGetItem(String item, int quantity) {
        List<Task> tasks = new ArrayList<>();

        // Check if we already have enough
        int current = resources.getItemCount(item);
        if (current >= quantity) {
            tasks.add(new Task(SmartTaskExecutor.TaskType.GET_ITEM, item, TaskPriority.PRIMARY));
            return tasks;
        }

        // Determine how to get this item
        String entitySource = resources.getEntitySourceFor(item);
        String rawSource = resources.getRawSourceFor(item);

        if (entitySource != null) {
            // Item comes from killing entities
            int needed = quantity; // Assume 1 drop per kill
            tasks.add(new Task(TaskType.KILL_MOB, entitySource, TaskPriority.PRIMARY));
            // Set quantity via target
        } else if (rawSource != null && rawSource.endsWith("_ore")) {
            // Item comes from mining
            tasks.add(new Task(TaskType.MINE_BLOCK, rawSource, TaskPriority.PRIMARY));
        } else {
            // Item may need crafting
            List<String> chain = resources.getCraftingChain(item);
            for (int i = chain.size() - 1; i >= 0; i--) {
                String needed = chain.get(i);
                if (!needed.equals(item) && !resources.hasItem(needed)) {
                    String src = resources.getRawSourceFor(needed);
                    if (src != null) {
                        if (resources.getEntitySourceFor(needed) != null) {
                            tasks.add(new Task(TaskType.KILL_MOB, src, TaskPriority.PRIMARY));
                        } else {
                            tasks.add(new Task(TaskType.MINE_BLOCK, src, TaskPriority.PRIMARY));
                        }
                    }
                }
            }
            // Final craft
            tasks.add(new Task(TaskType.CRAFT_ITEM, item, TaskPriority.PRIMARY));
        }

        return tasks;
    }

    /**
     * Plan "kill <target>"
     * e.g., "kill Notch" → chase + kill player
     * e.g., "kill creeper" → find and kill creepers
     */
    private List<Task> planKill(String target) {
        List<Task> tasks = new ArrayList<>();

        // Check if target is a player name or entity type
        if (isPlayerName(target)) {
            tasks.add(new Task(TaskType.KILL_PLAYER, target, TaskPriority.COMBAT));
        } else {
            tasks.add(new Task(TaskType.KILL_MOB, target, TaskPriority.COMBAT));
        }

        // Always add survival tasks
        tasks.add(new Task(TaskType.FARM_FOOD, "auto", TaskPriority.RESOURCE));
        tasks.add(new Task(TaskType.GET_ITEM, "iron_ingot", TaskPriority.RESOURCE));

        return tasks;
    }

    /**
     * Plan "chase <player>"
     * e.g., "chase Notch" → follow + optionally kill
     */
    private List<Task> planChase(String target) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(TaskType.FOLLOW_PLAYER, target, TaskPriority.COMBAT));
        tasks.add(new Task(TaskType.FARM_FOOD, "auto", TaskPriority.RESOURCE));
        return tasks;
    }

    /**
     * Plan "mine <block> <quantity>"
     * e.g., "mine diamond 10"
     */
    private List<Task> planMine(String block, int quantity) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(TaskType.MINE_BLOCK, block, TaskPriority.PRIMARY));
        return tasks;
    }

    /**
     * Plan "craft <item> <quantity>"
     * e.g., "craft paper 64" → gather materials then craft
     */
    private List<Task> planCraft(String item, int quantity) {
        List<Task> tasks = new ArrayList<>();

        List<String> chain = resources.getCraftingChain(item);
        for (String needed : chain) {
            if (!needed.equals(item) && !resources.hasItem(needed)) {
                String src = resources.getRawSourceFor(needed);
                if (src != null) {
                    if (resources.getEntitySourceFor(needed) != null) {
                        tasks.add(new Task(TaskType.KILL_MOB, src, TaskPriority.RESOURCE));
                    } else {
                        tasks.add(new Task(TaskType.MINE_BLOCK, src, TaskPriority.RESOURCE));
                    }
                }
            }
        }

        tasks.add(new Task(TaskType.CRAFT_ITEM, item, TaskPriority.PRIMARY));
        return tasks;
    }

    /**
     * Plan "trade <item> <quantity>"
     * e.g., "trade paper 64" → craft paper → trade with villager
     * This is the most complex task type.
     */
    private List<Task> planTrade(String item, int quantity) {
        List<Task> tasks = new ArrayList<>();

        // 1. Check what's needed to craft the trade item
        String craftTarget = item.equals("paper") || item.equals("stick") || item.equals("emerald") ? item : null;

        if (craftTarget != null) {
            // Gather materials for crafting
            List<String> chain = resources.getCraftingChain(craftTarget);
            int totalNeeded = quantity;

            if (!resources.hasItem(craftTarget) || resources.getItemCount(craftTarget) < totalNeeded) {
                for (String mat : chain) {
                    if (!mat.equals(craftTarget) && !mat.contains("_log")) {
                        int matNeed = totalNeeded;
                        tasks.add(new Task(TaskType.GET_ITEM, mat, TaskPriority.RESOURCE));
                    }
                }

                // Handle tree farming for sticks/planks
                if (chain.stream().anyMatch(m -> m.contains("_log"))) {
                    if (!resources.hasItem("oak_log")) {
                        tasks.add(new Task(TaskType.GET_ITEM, "oak_log", TaskPriority.RESOURCE));
                    }
                }

                // Craft the item
                tasks.add(new Task(TaskType.CRAFT_ITEM, craftTarget + " " + totalNeeded, TaskPriority.PRIMARY));
            }
        } else {
            // For emeralds or other currency: mine
            tasks.add(new Task(TaskType.MINE_BLOCK, "emerald_ore", TaskPriority.RESOURCE));
        }

        // 2. Find and trade with villager
        tasks.add(new Task(TaskType.GET_ITEM, item, TaskPriority.PRIMARY));

        return tasks;
    }

    /**
     * Plan "farm <crop> <quantity>"
     * e.g., "farm wheat 64" → plant, grow, harvest
     */
    private List<Task> planFarm(String crop, int quantity) {
        List<Task> tasks = new ArrayList<>();

        // Check if we have seeds
        String seedStr = switch (crop) {
            case "wheat" -> "wheat_seeds";
            case "carrot" -> "carrot";
            case "potato" -> "potato";
            case "beetroot" -> "beetroot_seeds";
            case "sugar_cane" -> "sugar_cane";
            default -> null;
        };

        if (seedStr != null && !resources.hasItem(seedStr)) {
            tasks.add(new Task(TaskType.GET_ITEM, seedStr, TaskPriority.RESOURCE));
        }

        // Plant and harvest
        tasks.add(new Task(TaskType.FARM_FOOD, crop, TaskPriority.RESOURCE));

        return tasks;
    }

    /**
     * Plan "explore <radius>"
     */
    private List<Task> planExplore(String radius) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(TaskType.EXPLORE, radius, TaskPriority.IDLE));
        return tasks;
    }

    /**
     * Plan "defend <location>"
     */
    private List<Task> planDefend(String location) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(new Task(TaskType.DEFEND, location, TaskPriority.COMBAT));
        return tasks;
    }

    /**
     * Check if a string looks like a player name (not an entity type).
     */
    private boolean isPlayerName(String name) {
        Item item = BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(name)).orElseThrow().value();
        if (item != Items.AIR) return false;

        // Check common entity types
        switch (name) {
            case "creeper": case "zombie": case "skeleton": case "spider":
            case "enderman": case "blaze": case "ghast": case "witch":
            case "cow": case "pig": case "chicken": case "sheep":
            case "villager": case "iron_golem": case "wolf":
                return false;
        }

        return true;
    }

    /**
     * Get a human-readable description of the task plan.
     */
    public String describePlan(List<Task> tasks) {
        StringBuilder sb = new StringBuilder("§6§lTask Plan:\n");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            sb.append("§7").append(i + 1).append(". §f")
              .append(t.type).append(" §e").append(t.target)
              .append(" §7[").append(t.priority).append("]\n");
        }
        return sb.toString();
    }
}
