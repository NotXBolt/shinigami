package shinigami.system;

import shinigami.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class AreaManager {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    public static class Area {
        public final String name;
        public final AABB bounds;
        public final long created;

        public Area(String name, AABB bounds) {
            this.name = name;
            this.bounds = bounds;
            this.created = System.currentTimeMillis();
        }

        public Vec3 getCenter() {
            return new Vec3(
                (bounds.minX + bounds.maxX) / 2,
                (bounds.minY + bounds.maxY) / 2,
                (bounds.minZ + bounds.maxZ) / 2
            );
        }

        public boolean contains(Vec3 pos) {
            return bounds.contains(pos);
        }

        public boolean contains(BlockPos pos) {
            return bounds.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }

        public double distanceTo(Vec3 pos) {
            return getCenter().distanceTo(pos);
        }
    }

    public AreaManager(AimAssistConfig config) {
        this.config = config;
    }

    public Area defineArea(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        double minX = Math.min(x1, x2);
        double minY = Math.min(y1, y2);
        double minZ = Math.min(z1, z2);
        double maxX = Math.max(x1, x2);
        double maxY = Math.max(y1, y2);
        double maxZ = Math.max(z1, z2);

        AABB bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        Area area = new Area(name, bounds);
        config.setArea(name, area);
        return area;
    }

    public Area defineArea(String name) {
        if (mc.player == null) return null;
        BlockPos pos = mc.player.blockPosition();
        return defineArea(name, pos.getX() - 5, pos.getY() - 3, pos.getZ() - 5,
            pos.getX() + 5, pos.getY() + 3, pos.getZ() + 5);
    }

    public boolean removeArea(String name) {
        return config.removeArea(name);
    }

    public Area getArea(String name) {
        return config.getArea(name);
    }

    public boolean isInArea(String name) {
        if (mc.player == null) return false;
        Area area = getArea(name);
        return area != null && area.contains(mc.player.position());
    }

    public void goToArea(String name) {
        if (mc.player == null) return;
        Area area = getArea(name);
        if (area == null) return;

        Vec3 center = area.getCenter();
        baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
            .getCustomGoalProcess().setGoalAndPath(
                new baritone.api.pathing.goals.GoalBlock(
                    (int)center.x, (int)center.y, (int)center.z
                )
            );
        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§6[Shinigami] §7Navigating to area §e" + name
            )
        );
    }

    public Map<String, Area> getAllAreas() {
        return config.getAllAreas();
    }

    public String listAreas() {
        Map<String, Area> areas = getAllAreas();
        if (areas.isEmpty()) return "§7No areas defined";

        StringBuilder sb = new StringBuilder("§6§lAreas:\n");
        areas.forEach((name, area) -> {
            Vec3 c = area.getCenter();
            sb.append("§e").append(name).append(" §7@ ")
              .append(String.format("%.0f, %.0f, %.0f", c.x, c.y, c.z))
              .append(" §8[").append((int)(area.bounds.maxX - area.bounds.minX))
              .append("x").append((int)(area.bounds.maxY - area.bounds.minY))
              .append("x").append((int)(area.bounds.maxZ - area.bounds.minZ))
              .append("]\n");
        });
        return sb.toString();
    }
}
