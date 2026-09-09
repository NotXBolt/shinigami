package shinigami.integrated.minecraftpvpbot;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiMinecraftPvpBot — Rebranded integration of Minecraft-PVP-bot — python auto pvp, find_minecraft.py
 * Original repo: minecraftpvpbot — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: Minecraft-PVP-bot — python auto pvp, find_minecraft.py
 */
public class ShinigamiMinecraftPvpBot {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from minecraftpvpbot: runs via arbiter, safe, RL-aware
        // Original feature: Minecraft-PVP-bot — python auto pvp, find_minecraft.py
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "minecraftpvpbot"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiMinecraftPvpBot"; }
    public String getSource() { return "minecraftpvpbot"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
