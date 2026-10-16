package com.giksik.perimeterdefense.domain

enum class EnemyType { SCOUT, RAIDER, ARMORED, BOSS }

data class EnemyUnit(
    val id: Long,
    val type: EnemyType,
    /** Lane occupied by the enemy (0-based). */
    val lane: Int,
    /** Progress from spawn (0) toward the base. */
    val progress: Int,
    val health: Int,
    val maxHealth: Int,
    val attackDamage: Int,
    val rewardChips: Int,
    val slowTurns: Int = 0
) {
    val isAlive: Boolean get() = health > 0
}

data class CombatState(
    val wave: Int = 1,
    val baseHp: Int = 100,
    val maxBaseHp: Int = 100,
    val chips: Int = 0,
    val turn: Int = 0,
    val turretStunTurns: Int = 0,
    val enemies: List<EnemyUnit> = emptyList()
) {
    val isGameOver: Boolean get() = baseHp <= 0
}

data class CombatRules(
    val pathLength: Int = 5,
    val mortarDamage: Int = 20,
    val cryoDamage: Int = 8,
    val cryoSlowTurns: Int = 1,
    val teslaDamage: Int = 12,
    val teslaProgressRadius: Int = 1,
    val repairAmount: Int = 15
)

enum class CombatEventType {
    ENEMY_DAMAGED,
    ENEMY_SLOWED,
    ENEMY_DESTROYED,
    BASE_REPAIRED,
    BASE_DAMAGED,
    ENEMY_ADVANCED,
    TURRETS_STUNNED
}

data class CombatEvent(
    val type: CombatEventType,
    val enemyId: Long? = null,
    /** Damage, repair, reward, or progress amount, depending on event type. */
    val amount: Int = 0
)

data class CombatResult(
    val state: CombatState,
    val events: List<CombatEvent> = emptyList()
)
