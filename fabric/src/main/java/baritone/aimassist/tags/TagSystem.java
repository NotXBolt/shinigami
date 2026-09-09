package baritone.aimassist.tags;

import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class TagSystem {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module;

    public TagSystem(AimAssistModule module) {
        this.module = module;
    }

    private final List<Tag> activeTags = new CopyOnWriteArrayList<>();
    private TagPriority globalPriority = TagPriority.BALANCED;

    public enum TagType {
        CHASE,
        FOLLOW,
        KILL,
        ESCORT,
        GUARD,
        FLEE,
        FARM,
        GATHER,
        EXPLORE
    }

    public enum TagPriority {
        SPEED(0),
        AGGRESSIVE(1),
        BALANCED(2),
        SAFE(3),
        STEALTH(4);

        final int value;
        TagPriority(int v) { this.value = v; }
    }

    public enum SubTag {
        KILL_TARGET("kill - eliminate target"),
        AVOID_COMBAT("avoid - don't engage unless necessary"),
        AUTO_EAT("eat - auto maintain food"),
        AUTO_HEAL("heal - auto heal when low"),
        AUTO_GEAR("gear - auto equip best gear"),
        AUTO_CRAFT("craft - auto craft needed items"),
        PARKOUR("parkour - use advanced movement"),
        CLUTCH("clutch - auto MLG water/haybale"),
        WATER("swim - follow through water"),
        FLY("elytra - use elytra if available");

        final String description;
        SubTag(String d) { this.description = d; }
    }

    public static class Tag {
        public final TagType type;
        public final String target;
        public final Set<SubTag> subTags;
        public final long createdAt;
        public boolean completed;
        public String error;

        public Tag(TagType type, String target, SubTag... subTags) {
            this.type = type;
            this.target = target;
            this.subTags = new HashSet<>(Arrays.asList(subTags));
            this.createdAt = System.currentTimeMillis();
            this.completed = false;
        }

        public boolean hasSubTag(SubTag s) { return subTags.contains(s); }
        public void addSubTag(SubTag s) { subTags.add(s); }
    }

    public void addTag(TagType type, String target, SubTag... subTags) {
        activeTags.add(new Tag(type, target, subTags));
        sendFeedback("Tag added: " + type + " " + target);
    }

    public void addTagWithString(String input) {
        String[] parts = input.toLowerCase().split("\\s+");
        if (parts.length < 2) {
            sendFeedback("Usage: tag <chase/kill/follow> <player> [subtags]");
            return;
        }

        TagType type;
        try {
            type = TagType.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            sendFeedback("Unknown tag type: " + parts[0]);
            return;
        }

        String target = parts[1];
        List<SubTag> subs = new ArrayList<>();

        for (int i = 2; i < parts.length; i++) {
            try {
                subs.add(SubTag.valueOf(parts[i].toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        if (type == TagType.KILL || type == TagType.CHASE) {
            if (!subs.contains(SubTag.KILL_TARGET)) {
                subs.add(SubTag.KILL_TARGET);
            }
            subs.add(SubTag.AUTO_EAT);
            subs.add(SubTag.AUTO_HEAL);
            subs.add(SubTag.AUTO_GEAR);
            subs.add(SubTag.CLUTCH);
            subs.add(SubTag.PARKOUR);
        }

        activeTags.add(new Tag(type, target, subs.toArray(new SubTag[0])));
        module.setTargetPlayerName(target);
        sendFeedback("Active tag: §6" + type + " §f" + target + " §7[auto-survival enabled]");
    }

    public void processTags() {
        if (activeTags.isEmpty()) return;

        for (Tag tag : activeTags) {
            if (tag.completed) continue;

            switch (tag.type) {
                case CHASE -> processChase(tag);
                case KILL -> processKill(tag);
                case FOLLOW -> processFollow(tag);
                case FLEE -> processFlee(tag);
                case FARM -> processFarm(tag);
            }
        }

        activeTags.removeIf(t -> t.completed);
    }

    private void processChase(Tag tag) {
        module.setChaseMode(true);
        module.setCurrentChaseTarget(tag.target);

        if (tag.hasSubTag(SubTag.KILL_TARGET)) {
            module.setChaseKill(true);
        }
    }

    private void processKill(Tag tag) {
        module.setChaseMode(true);
        module.setChaseKill(true);
        module.setCurrentChaseTarget(tag.target);
        ((AimAssistConfig)module.getConfig()).setPvpMode(true);
        ((AimAssistConfig)module.getConfig()).setCritMode(true);
        ((AimAssistConfig)module.getConfig()).setMaceMode(true);
    }

    private void processFollow(Tag tag) {
        module.setChaseMode(true);
        module.setCurrentChaseTarget(tag.target);
    }

    private void processFlee(Tag tag) {
        module.setChaseMode(false);
        module.setFleeMode(true);
    }

    private void processFarm(Tag tag) {
        module.setFarmMode(true);
    }

    public void clearTags() {
        activeTags.clear();
        module.setChaseMode(false);
        module.setChaseKill(false);
        module.setFleeMode(false);
        module.setFarmMode(false);
        sendFeedback("All tags cleared");
    }

    public List<Tag> getActiveTags() { return activeTags; }
    public TagPriority getGlobalPriority() { return globalPriority; }
    public void setGlobalPriority(TagPriority p) { this.globalPriority = p; }

    private void sendFeedback(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§6[Tags] §f" + msg));
        }
    }

    public boolean hasActiveTags() { return !activeTags.isEmpty(); }
}
