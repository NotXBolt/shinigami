package baritone.aimassist.tags;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.*;
import baritone.aimassist.AimAssistModule;
import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public class SmartTaskExecutor {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module;
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    public SmartTaskExecutor(AimAssistModule module) {
        this.module = module;
    }

    public enum TaskPriority {
        CRITICAL(0),
        COMBAT(1),
        PRIMARY(2),
        RESOURCE(3),
        PATHING(4),
        IDLE(5);

        final int level;
        TaskPriority(int l) { this.level = l; }
    }

    public enum TaskType {
        KILL_PLAYER,
        KILL_MOB,
        GET_ITEM,
        MINE_BLOCK,
        CRAFT_ITEM,
        SMELT_ITEM,
        FARM_FOOD,
        FOLLOW_PLAYER,
        ESCORT,
        EXPLORE,
        DEFEND
    }

    public static class Task {
        public final TaskType type;
        public final String target;
        public TaskPriority priority;
        public boolean completed;
        public boolean paused;
        public String currentAction;
        public long startedAt;

        public Task(TaskType type, String target, TaskPriority priority) {
            this.type = type;
            this.target = target;
            this.priority = priority;
            this.startedAt = System.currentTimeMillis();
        }

        public boolean isTimedOut() {
            return System.currentTimeMillis() - startedAt > 300_000; // 5 min timeout
        }
    }

    private final List<Task> taskQueue = new ArrayList<>();
    private Task currentTask = null;
    private boolean miningPaused = false;
    private long lastMobCheck = 0;
    private static final double MOB_THREAT_RANGE = 12.0;

    public void tick() {
        if (mc.player == null || mc.level == null) return;

        // Always check for immediate threats regardless of current task
        checkAndHandleThreats();

        if (taskQueue.isEmpty()) {
            currentTask = null;
            return;
        }

        // Sort by priority and age
        taskQueue.sort((a, b) -> {
            int prio = Integer.compare(a.priority.level, b.priority.level);
            if (prio == 0) return Long.compare(b.startedAt, a.startedAt);
            return prio;
        });

        // Remove completed/timed out tasks
        taskQueue.removeIf(t -> t.completed || t.isTimedOut());

        currentTask = taskQueue.get(0);

        // If there's a combat priority task, handle it first
        if (hasCombatPriority()) {
            handleCombatPriority();
            return;
        }

        executeCurrentTask();
    }

    private void checkAndHandleThreats() {
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMobCheck < 500) return;
        lastMobCheck = now;

        Monster nearestThreat = null;
        double nearestDist = MOB_THREAT_RANGE;

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - MOB_THREAT_RANGE, mc.player.getY() - MOB_THREAT_RANGE, mc.player.getZ() - MOB_THREAT_RANGE,
            mc.player.getX() + MOB_THREAT_RANGE, mc.player.getY() + MOB_THREAT_RANGE, mc.player.getZ() + MOB_THREAT_RANGE
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof Monster monster && monster.isAlive()) {
                if (monster.getTarget() == mc.player) {
                    double dist = mc.player.distanceTo(monster);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestThreat = monster;
                    }
                }
            }
        }

        if (nearestThreat != null) {
            miningPaused = true;

            // Engage threat
            ((AimAssistConfig)module.getConfig()).setEnabled(true);
            ((AimAssistConfig)module.getConfig()).setCritMode(true);

            // Aim at threat
            baritone.api.utils.Rotation rot = module.getAimController().calculateRotation(
                nearestThreat.position().add(0, nearestThreat.getBbHeight() * 0.4, 0)
            );
            mc.player.setYRot(rot.getYaw());
            mc.player.setXRot(rot.getPitch());

            if (mc.player.distanceTo(nearestThreat) < config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                mc.gameMode.attack(mc.player, nearestThreat);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }

            // For creepers: use bow to avoid explosion
            if (nearestThreat instanceof Creeper) {
                handleCreeperSafe((Creeper) nearestThreat);
            }
        } else {
            miningPaused = false;
        }
    }

    private void handleCreeperSafe(Creeper creeper) {
        if (mc.player == null) return;

        double dist = mc.player.distanceTo(creeper);

        if (creeper.getSwellDir() > 20) {
            // RUN - creeper about to explode
            Vec3 away = mc.player.position().subtract(creeper.position()).normalize().scale(10);
            mc.player.setDeltaMovement(away.x, 0.5, away.z);
            return;
        }

        // Use bow for safe kill
        boolean hasBow = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BOW)) {
                mc.player.getInventory().setSelectedSlot(i);
                hasBow = true;
                break;
            }
        }

        if (hasBow && dist > 4) {
            mc.options.keyUse.setDown(true); // Draw bow
            // Auto-aim with bow prediction
            module.getBowAssist().setActive(true);
        } else if (dist > 3 && dist <= 6 && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            // Safe melee range (won't trigger explosion on hit)
            mc.gameMode.attack(mc.player, creeper);
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else if (dist <= 3) {
            // Too close - back away
            Vec3 away = mc.player.position().subtract(creeper.position()).normalize();
            mc.player.setDeltaMovement(away.x, 0.2, away.z);
        }
    }

    private boolean hasCombatPriority() {
        if (currentTask == null) return false;
        return currentTask.type == TaskType.KILL_PLAYER ||
               currentTask.type == TaskType.KILL_MOB ||
               currentTask.type == TaskType.DEFEND;
    }

    private void handleCombatPriority() {
        if (currentTask == null) return;

        switch (currentTask.type) {
            case KILL_PLAYER -> {
                module.setChaseMode(true);
                module.setChaseKill(true);
                module.setCurrentChaseTarget(currentTask.target);
                config.setEnabled(true);
                config.setPvpMode(true);
                config.setCritMode(true);
                config.setMaceMode(true);
                config.setAutoEat(true);
                config.setAutoHeal(true);

                // Aggressive baritone settings
                BaritoneAPI.getSettings().allowParkour.value = true;
                BaritoneAPI.getSettings().allowBreak.value = true;
            }
            case KILL_MOB -> {
                // Find and kill the specified mob type
                String mobType = currentTask.target.toLowerCase();
                LivingEntity target = findNearestMob(mobType);
                if (target != null) {
                    ((AimAssistConfig)module.getConfig()).setEnabled(true);
                    baritone.api.utils.Rotation rot = module.getAimController().calculateRotation(
                        target.position().add(0, target.getBbHeight() * 0.4, 0)
                    );
                    mc.player.setYRot(rot.getYaw());
                    mc.player.setXRot(rot.getPitch());

                    if (mc.player.distanceTo(target) <= config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                        if (target instanceof Creeper) {
                            handleCreeperSafe((Creeper) target);
                        } else {
                            mc.gameMode.attack(mc.player, target);
                            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        }
                    }

                    // Path toward it if too far
                    if (mc.player.distanceTo(target) > config.getRange()) {
                        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                        if (baritone != null) {
                            baritone.getCustomGoalProcess().setGoalAndPath(
                                new GoalNear(target.blockPosition(), 3)
                            );
                        }
                    }
                }
            }
        }
    }

    private void executeCurrentTask() {
        if (currentTask == null || mc.player == null) return;

        switch (currentTask.type) {
            case GET_ITEM -> executeGetItem(currentTask.target);
            case MINE_BLOCK -> executeMineBlock(currentTask.target);
            case FARM_FOOD -> executeFarmFood();
            case CRAFT_ITEM -> executeCraftItem(currentTask.target);
        }
    }

    private void executeGetItem(String itemName) {
        if (mc.player == null) return;

        // Check if we already have the item
        Item targetItem = findItemByName(itemName);
        if (targetItem == null) return;

        if (hasItemInInventory(targetItem)) {
            currentTask.completed = true;
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Task] §aGot " + itemName + "! Task complete."
                )
            );
            return;
        }

        // Find the best way to get the item
        if (targetItem == Items.GUNPOWDER) {
            // Kill creepers for gunpowder
            currentTask.currentAction = "Hunting creepers for gunpowder";
            Creeper nearest = findNearestCreeper();
            if (nearest != null) {
                handleCreeperSafe(nearest);
                if (mc.player.distanceTo(nearest) > 6) {
                    IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                    if (baritone != null) {
                        baritone.getCustomGoalProcess().setGoalAndPath(
                            new GoalNear(nearest.blockPosition(), 4)
                        );
                    }
                }
            } else {
                // No creepers nearby - search
                currentTask.currentAction = "Searching for creepers...";
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null && !baritone.getPathingBehavior().isPathing()) {
                    baritone.getExploreProcess().explore((int)mc.player.getX(), (int)mc.player.getZ());
                }
            }
        } else if (targetItem.getDefaultInstance().has(net.minecraft.core.component.DataComponents.FOOD)) {
            // Get food
            currentTask.currentAction = "Gathering food: " + itemName;
            killAnimalsForFood();
        } else {
            // Generic: try to find in chests or mine
            currentTask.currentAction = "Looking for: " + itemName;
        }
    }

    private void executeMineBlock(String blockName) {
        if (mc.player == null || mc.level == null) return;

        // Pause mining if mobs are near
        if (miningPaused) {
            currentTask.currentAction = "Defending from mobs";
            currentTask.paused = true;
            return;
        }
        currentTask.paused = false;
        currentTask.currentAction = "Mining " + blockName;

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != null) {
            BaritoneAPI.getSettings().allowBreak.value = true;
            baritone.getMineProcess().mineByName(blockName);
        }
    }

    private void executeFarmFood() {
        if (mc.player == null) return;

        if (mc.player.getFoodData().getFoodLevel() > 15) {
            currentTask.completed = true;
            return;
        }

        currentTask.currentAction = "Farming food";
        killAnimalsForFood();

        // Also check for crops
        if (!miningPaused) {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && !baritone.getPathingBehavior().isPathing()) {
                baritone.getFarmProcess().farm();
            }
        }
    }

    private void executeCraftItem(String itemName) {
        currentTask.currentAction = "Crafting " + itemName;
        // Use baritone's crafting if available, otherwise manual
    }

    private void killAnimalsForFood() {
        if (mc.level == null || mc.player == null) return;

        Animal nearest = null;
        double nearestDist = 15;

        net.minecraft.world.phys.AABB animalBox = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 20, mc.player.getY() - 20, mc.player.getZ() - 20,
            mc.player.getX() + 20, mc.player.getY() + 20, mc.player.getZ() + 20
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, animalBox)) {
            if (e instanceof Animal animal && animal.isAlive() && !animal.isBaby()) {
                double dist = mc.player.distanceTo(animal);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = animal;
                }
            }
        }

        if (nearest != null) {
            ((AimAssistConfig)module.getConfig()).setEnabled(true);

            baritone.api.utils.Rotation rot = module.getAimController().calculateRotation(
                nearest.position().add(0, nearest.getBbHeight() * 0.4, 0)
            );
            mc.player.setYRot(rot.getYaw());
            mc.player.setXRot(rot.getPitch());

            if (mc.player.distanceTo(nearest) <= config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                mc.gameMode.attack(mc.player, nearest);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            } else {
                IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                if (baritone != null) {
                    baritone.getCustomGoalProcess().setGoalAndPath(
                        new GoalNear(nearest.blockPosition(), 3)
                    );
                }
            }
        }
    }

    // === UTILITY METHODS ===

    private Creeper findNearestCreeper() {
        if (mc.level == null || mc.player == null) return null;
        double closest = 64;
        Creeper result = null;
        net.minecraft.world.phys.AABB creeperBox = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 64, mc.player.getY() - 64, mc.player.getZ() - 64,
            mc.player.getX() + 64, mc.player.getY() + 64, mc.player.getZ() + 64
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, creeperBox)) {
            if (e instanceof Creeper creeper && creeper.isAlive()) {
                double dist = mc.player.distanceTo(creeper);
                if (dist < closest) {
                    closest = dist;
                    result = creeper;
                }
            }
        }
        return result;
    }

    private LivingEntity findNearestMob(String type) {
        if (mc.level == null || mc.player == null) return null;
        double closest = 64;
        LivingEntity result = null;

        net.minecraft.world.phys.AABB mobBox = new net.minecraft.world.phys.AABB(
            mc.player.getX() - closest, mc.player.getY() - closest, mc.player.getZ() - closest,
            mc.player.getX() + closest, mc.player.getY() + closest, mc.player.getZ() + closest
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, mobBox)) {
            if (e instanceof LivingEntity living && living.isAlive() && living != mc.player) {
                String name = living.getType().getDescription().getString().toLowerCase();
                if (name.contains(type)) {
                    double dist = mc.player.distanceTo(living);
                    if (dist < closest) {
                        closest = dist;
                        result = living;
                    }
                }
            }
        }
        return result;
    }

    private Item findItemByName(String name) {
        String clean = name.toLowerCase().replace(" ", "_");
        return BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(clean)).orElseThrow().value();
    }

    private boolean hasItemInInventory(Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    // === PUBLIC API ===

    public void addTask(TaskType type, String target, TaskPriority priority) {
        taskQueue.add(new Task(type, target, priority));
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Task] §fAdded: " + type + " " + target + " [" + priority + "]"
                )
            );
        }
    }

    public void clearTasks() {
        taskQueue.clear();
        currentTask = null;
        miningPaused = false;
        if (mc.player != null) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().forceCancel();
        }
    }

    public List<Task> getTaskQueue() { return taskQueue; }
    public Task getCurrentTask() { return currentTask; }
    public boolean isMiningPaused() { return miningPaused; }
    public String getCurrentAction() {
        return currentTask != null ? currentTask.currentAction : "Idle";
    }
}
