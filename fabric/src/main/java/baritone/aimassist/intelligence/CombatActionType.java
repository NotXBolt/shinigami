package baritone.aimassist.intelligence;

/**
 * CombatActionType — all possible actions the intelligence system can take.
 * Priority order determines which action wins when multiple are valid.
 */
public enum CombatActionType {
    // Emergency (highest priority)
    EMERGENCY_DODGE,      // Void, lava, explosion — escape immediately
    CLUTCH,               // Emergency fallback using blocks/items

    // Dodge (high priority)
    DODGE,                // Dodge incoming attack/projectile

    // Combat
    ATTACK,               // Normal attack
    PRESSURE_ATTACK,      // Aggressive combo attack
    SMASH,                // Mace smash attack
    SHOOT,                // Bow/crossbow shot
    FINISH_ATTACK,        // Kill strike on wounded target

    // Movement
    CHASE,                // Chase target
    INTERCEPT,            // Cut off retreating target
    REPOSITION,           // Find better position
    CIRCLE,               // Circle around target
    PARKOUR,              // Parkour movement

    // Recovery
    RECOVER,              // Recover position after miss
    HEAL,                 // Eat food / drink potion
    ESCAPE,               // Full retreat
    RETREAT,              // Strategic retreat

    // Idle
    IDLE,                 // Wait/observe
    WAIT_FOR_COOLDOWN     // Wait for attack cooldown
}
