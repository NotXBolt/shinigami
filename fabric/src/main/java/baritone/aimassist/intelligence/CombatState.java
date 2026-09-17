package baritone.aimassist.intelligence;

/**
 * CombatState — high-level combat state for the Ultra Instinct decision engine.
 */
public enum CombatState {
    IDLE,       // No target, observe
    APPROACH,   // Moving toward target
    ENGAGE,     // In combat, attacking
    PRESSURE,   // Aggressive combo pressure
    EVADE,      // Dodging threats
    RECOVER,    // Recovering from damage/misses
    HEAL,       // Healing
    CLUTCH,     // Emergency clutch
    CHASE,      // Chasing retreating target
    FINISH,     // Finishing wounded target
    ESCAPE,     // Full retreat to survive
    REPOSITION  // Find better position
}
