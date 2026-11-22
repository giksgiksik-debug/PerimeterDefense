package com.giksgik.perimeterdefense.domain

enum class EnemyType { SCOUT, RAIDER, ARMORED, BOSS }

enum class HangarUpgradeType { MORTAR, CRYO, TESLA, SHIELD }

object BoosterCosts {
    const val SCANNER = 0
    const val AIRSTRIKE = 40
    const val REPAIR = 30
}

data class HangarUpgrades(
    val mortarLevel: Int = 0,
    val cryoLevel: Int = 0,
    val teslaLevel: Int = 0,
    val shieldLevel: Int = 0
) {
    fun levelOf(type: HangarUpgradeType): Int = when (type) {
        HangarUpgradeType.MORTAR -> mortarLevel
        HangarUpgradeType.CRYO -> cryoLevel
        HangarUpgradeType.TESLA -> teslaLevel
        HangarUpgradeType.SHIELD -> shieldLevel
    }

    fun canUpgrade(type: HangarUpgradeType): Boolean = levelOf(type) < MAX_LEVEL

    fun costOf(type: HangarUpgradeType): Int = BASE_COST + levelOf(type) * COST_STEP

    fun withIncrement(type: HangarUpgradeType): HangarUpgrades = when (type) {
        HangarUpgradeType.MORTAR -> copy(mortarLevel = (mortarLevel + 1).coerceAtMost(MAX_LEVEL))
        HangarUpgradeType.CRYO -> copy(cryoLevel = (cryoLevel + 1).coerceAtMost(MAX_LEVEL))
        HangarUpgradeType.TESLA -> copy(teslaLevel = (teslaLevel + 1).coerceAtMost(MAX_LEVEL))
        HangarUpgradeType.SHIELD -> copy(shieldLevel = (shieldLevel + 1).coerceAtMost(MAX_LEVEL))
    }

    fun toCombatRules(base: CombatRules = CombatRules()): CombatRules = base.copy(
        mortarDamage = base.mortarDamage + mortarLevel * MORTAR_DAMAGE_PER_LEVEL,
        cryoDamage = base.cryoDamage + cryoLevel * CRYO_DAMAGE_PER_LEVEL,
        cryoSlowTurns = base.cryoSlowTurns + (cryoLevel / 2),
        teslaDamage = base.teslaDamage + teslaLevel * TESLA_DAMAGE_PER_LEVEL,
        repairAmount = base.repairAmount + shieldLevel * REPAIR_BONUS_PER_LEVEL
    )

    companion object {
        const val MAX_LEVEL = 5
        const val BASE_COST = 35
        const val COST_STEP = 25
        const val MORTAR_DAMAGE_PER_LEVEL = 6
        const val CRYO_DAMAGE_PER_LEVEL = 4
        const val TESLA_DAMAGE_PER_LEVEL = 5
        const val SHIELD_HP_PER_LEVEL = 20
        const val REPAIR_BONUS_PER_LEVEL = 3
    }
}

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
    val enemies: List<EnemyUnit> = emptyList(),
    val upgrades: HangarUpgrades = HangarUpgrades()
) {
    val isGameOver: Boolean get() = baseHp <= 0

    fun canAffordAnyUpgrade(): Boolean = HangarUpgradeType.entries.any { type ->
        upgrades.canUpgrade(type) && chips >= upgrades.costOf(type)
    }
}

data class CombatRules(
    val pathLength: Int = 7,
    val mortarDamage: Int = 30,
    val cryoDamage: Int = 14,
    val cryoSlowTurns: Int = 2,
    val teslaDamage: Int = 18,
    val teslaProgressRadius: Int = 2,
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
    val amount: Int = 0,
    /** Weapon responsible for a visual/audio hit effect; null for non-weapon events. */
    val weaponColor: AmmoColor? = null
)

data class CombatResult(
    val state: CombatState,
    val events: List<CombatEvent> = emptyList()
)
