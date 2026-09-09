package shinigami.system;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class EntityTrackerSystem {
    private final Minecraft mc = Minecraft.getInstance();
    private int tickCounter = 0;
    private int scanInterval = 2;

    private List<Player> players = new ArrayList<>();
    private List<Monster> monsters = new ArrayList<>();
    private List<Slime> slimes = new ArrayList<>();
    private List<Projectile> projectiles = new ArrayList<>();
    private List<PrimedTnt> tnts = new ArrayList<>();
    private List<FallingBlockEntity> fallingBlocks = new ArrayList<>();
    private List<EndCrystal> crystals = new ArrayList<>();
    private List<AbstractThrownPotion> potions = new ArrayList<>();
    private List<WitherSkull> witherSkulls = new ArrayList<>();
    private List<Fireball> fireballs = new ArrayList<>();
    private List<DragonFireball> dragonFireballs = new ArrayList<>();
    private List<ShulkerBullet> shulkerBullets = new ArrayList<>();
    private List<ThrownTrident> tridents = new ArrayList<>();
    private List<LivingEntity> livingEntities = new ArrayList<>();
    private List<Entity> allEntities = new ArrayList<>();

    private double cacheRange = 64;
    private boolean initialized = false;

    public void tick() {
        tickCounter++;
        if (tickCounter % scanInterval != 0) return;

        if (mc.level == null || mc.player == null) return;

        clearCache();
        AABB box = mc.player.getBoundingBox().inflate(cacheRange);

        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive() || e == mc.player) continue;
            if (!box.intersects(e.getBoundingBox())) continue;

            allEntities.add(e);
            if (e instanceof LivingEntity le) livingEntities.add(le);
            if (e instanceof Player p) players.add(p);
            else if (e instanceof Monster m) monsters.add(m);
            if (e instanceof Slime s) slimes.add(s);
            if (e instanceof Projectile proj) {
                projectiles.add(proj);
                if (e instanceof AbstractThrownPotion p) potions.add(p);
                else if (e instanceof WitherSkull w) witherSkulls.add(w);
                else if (e instanceof Fireball f) fireballs.add(f);
                else if (e instanceof DragonFireball d) dragonFireballs.add(d);
                else if (e instanceof ShulkerBullet s) shulkerBullets.add(s);
                else if (e instanceof ThrownTrident t) tridents.add(t);
            }
            if (e instanceof PrimedTnt t) tnts.add(t);
            if (e instanceof FallingBlockEntity f) fallingBlocks.add(f);
            if (e instanceof EndCrystal c) crystals.add(c);
        }
        initialized = true;
    }

    private void clearCache() {
        players.clear(); monsters.clear(); slimes.clear(); projectiles.clear();
        tnts.clear(); fallingBlocks.clear(); crystals.clear(); potions.clear();
        witherSkulls.clear(); fireballs.clear(); dragonFireballs.clear();
        shulkerBullets.clear(); tridents.clear();
        livingEntities.clear(); allEntities.clear();
    }

    public void forceScan() {
        tickCounter = 0;
        tick();
    }

    public List<Player> getPlayers() { return players; }
    public List<Monster> getMonsters() { return monsters; }
    public List<Slime> getSlimes() { return slimes; }
    public List<Projectile> getProjectiles() { return projectiles; }
    public List<PrimedTnt> getTnts() { return tnts; }
    public List<FallingBlockEntity> getFallingBlocks() { return fallingBlocks; }
    public List<EndCrystal> getCrystals() { return crystals; }
    public List<AbstractThrownPotion> getPotions() { return potions; }
    public List<WitherSkull> getWitherSkulls() { return witherSkulls; }
    public List<Fireball> getFireballs() { return fireballs; }
    public List<DragonFireball> getDragonFireballs() { return dragonFireballs; }
    public List<ThrownTrident> getTridents() { return tridents; }
    public List<LivingEntity> getLivingEntities() { return livingEntities; }
    public List<Entity> getAllEntities() { return allEntities; }
    public boolean isInitialized() { return initialized; }

    public Player getNearestPlayer() {
        if (mc.player == null) return null;
        Player best = null; double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = mc.player.distanceToSqr(p);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    public Monster getNearestMonster() {
        if (mc.player == null) return null;
        Monster best = null; double bestDist = Double.MAX_VALUE;
        for (Monster m : monsters) {
            double d = mc.player.distanceToSqr(m);
            if (d < bestDist) { bestDist = d; best = m; }
        }
        return best;
    }

    public Projectile getNearestProjectile() {
        if (mc.player == null) return null;
        Projectile best = null; double bestDist = Double.MAX_VALUE;
        for (Projectile p : projectiles) {
            double d = mc.player.distanceToSqr(p);
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }
}
