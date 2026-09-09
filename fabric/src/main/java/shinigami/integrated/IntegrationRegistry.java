package shinigami.integrated;

import shinigami.movement.MovementArbiter;
import java.util.*;

/**
 * IntegrationRegistry — Joins all 22 repos, adapt & rebrand.
 * Each repo's Shinigami* class ticked via single registry, unified under MovementArbiter.
 */
public class IntegrationRegistry {
    private final List<Object> modules = new ArrayList<>();
    public IntegrationRegistry() {
        modules.add(new shinigami.integrated.dodger.ShinigamiDodger());
        modules.add(new shinigami.integrated.kiwi.ShinigamiKiwi());
        modules.add(new shinigami.integrated.oogabooga.ShinigamiOogabooga());
        modules.add(new shinigami.integrated.parkourcalculator.ShinigamiParkourCalculator());
        modules.add(new shinigami.integrated.parkourcalculatormod.ShinigamiParkourCalculatorMod());
        modules.add(new shinigami.integrated.betterautojump.ShinigamiBetterAutoJump());
        modules.add(new shinigami.integrated.cadence.ShinigamiCadence());
        modules.add(new shinigami.integrated.cosmos.ShinigamiCosmos());
        modules.add(new shinigami.integrated.stonecraft.ShinigamiStonecraft());
        modules.add(new shinigami.integrated.huntress.ShinigamiHuntress());
        modules.add(new shinigami.integrated.maple.ShinigamiMaple());
        modules.add(new shinigami.integrated.mineflayerpathfinder.ShinigamiMineflayerPathfinder());
        modules.add(new shinigami.integrated.pvpbotfabric.ShinigamiPvpBotFabric());
        modules.add(new shinigami.integrated.minecraftpvpbot.ShinigamiMinecraftPvpBot());
        modules.add(new shinigami.integrated.meinbot.ShinigamiMeinbot());
        modules.add(new shinigami.integrated.fabric.ShinigamiFabric());
        modules.add(new shinigami.integrated.yarn.ShinigamiYarn());
        modules.add(new shinigami.integrated.fabricautoclicker.ShinigamiFabricAutoClicker());
        modules.add(new shinigami.integrated.macebot.ShinigamiMacebot());
        modules.add(new shinigami.integrated.blockfighter.ShinigamiBlockfighter());
        modules.add(new shinigami.integrated.unionclef.ShinigamiUnionclef());
        modules.add(new shinigami.integrated.enthusiaautoclicker.ShinigamiEnthusiaAutoClicker());
    }
    public void tickAll(MovementArbiter arbiter) {
        for (Object m : modules) {
            try { m.getClass().getMethod("tick", MovementArbiter.class).invoke(m, arbiter); } catch (Exception ignored){}
        }
    }
    public int size(){ return modules.size(); }
}
