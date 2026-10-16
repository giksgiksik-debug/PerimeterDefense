package com.giksik.perimeterdefense.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic combat rules. This class has no Android/UI dependencies,
 * so the turn logic can be tested without launching the app.
 */
object CombatEngine {

    fun resolveMatches(
        initialState: CombatState,
        groups: List<MatchGroup>,
        rules: CombatRules = CombatRules()
    ): CombatResult {
        if (initialState.isGameOver || groups.isEmpty()) return CombatResult(initialState)

        var enemies = initialState.enemies.filter { it.isAlive }
        var baseHp = initialState.baseHp.coerceIn(0, initialState.maxBaseHp)
        var chips = initialState.chips
        val events = mutableListOf<CombatEvent>()

        fun damageEnemy(enemyId: Long, damage: Int) {
            val enemy = enemies.firstOrNull { it.id == enemyId } ?: return
            val appliedDamage = damage.coerceAtLeast(0)
            val remainingHealth = (enemy.health - appliedDamage).coerceAtLeast(0)
            events += CombatEvent(CombatEventType.ENEMY_DAMAGED, enemyId, minOf(enemy.health, appliedDamage))

            if (remainingHealth == 0) {
                enemies = enemies.filterNot { it.id == enemyId }
                chips += enemy.rewardChips.coerceAtLeast(0)
                events += CombatEvent(CombatEventType.ENEMY_DESTROYED, enemyId, enemy.rewardChips.coerceAtLeast(0))
            } else {
                enemies = enemies.map {
                    if (it.id == enemyId) it.copy(health = remainingHealth) else it
                }
            }
        }

        fun scaledDamage(damage: Int, group: MatchGroup): Int =
            (damage * group.multiplier).roundToInt().coerceAtLeast(1)

        for (group in groups) {
            // The green support cell repairs the base and remains available even
            // during a turret-overload window.
            if (group.ammoColor == AmmoColor.GREEN_HEAL) {
                val before = baseHp
                baseHp = (baseHp + (rules.repairAmount * group.multiplier).roundToInt())
                    .coerceAtMost(initialState.maxBaseHp)
                val repaired = baseHp - before
                if (repaired > 0) events += CombatEvent(CombatEventType.BASE_REPAIRED, amount = repaired)
                continue
            }

            if (initialState.turretStunTurns > 0) {
                events += CombatEvent(CombatEventType.TURRETS_STUNNED, amount = initialState.turretStunTurns)
                continue
            }

            val target = enemies
                .filter { it.isAlive }
                .sortedWith(compareByDescending<EnemyUnit> { it.progress }.thenBy { it.id })
                .firstOrNull() ?: continue

            when (group.ammoColor) {
                AmmoColor.RED -> damageEnemy(target.id, scaledDamage(rules.mortarDamage, group))
                AmmoColor.BLUE -> {
                    damageEnemy(target.id, scaledDamage(rules.cryoDamage, group))
                    enemies = enemies.map {
                        if (it.id == target.id) it.copy(slowTurns = maxOf(it.slowTurns, rules.cryoSlowTurns))
                        else it
                    }
                    if (enemies.any { it.id == target.id }) {
                        events += CombatEvent(CombatEventType.ENEMY_SLOWED, target.id, rules.cryoSlowTurns)
                    }
                }
                AmmoColor.YELLOW -> {
                    val targetIds = enemies
                        .filter {
                            it.lane == target.lane &&
                                abs(it.progress - target.progress) <= rules.teslaProgressRadius
                        }
                        .map { it.id }
                    targetIds.forEach { id -> damageEnemy(id, scaledDamage(rules.teslaDamage, group)) }
                }
                AmmoColor.GREEN_HEAL, AmmoColor.NONE -> Unit
            }
        }

        return CombatResult(
            state = initialState.copy(
                baseHp = baseHp,
                chips = chips,
                enemies = enemies
            ),
            events = events
        )
    }

    /**
     * Advances one player turn. Slowed enemies spend one turn recovering;
     * enemies that reach pathLength damage the base and leave the field.
     */
    fun advanceTurn(
        initialState: CombatState,
        rules: CombatRules = CombatRules()
    ): CombatResult {
        if (initialState.isGameOver) return CombatResult(initialState)

        val nextEnemies = mutableListOf<EnemyUnit>()
        val events = mutableListOf<CombatEvent>()
        var baseHp = initialState.baseHp.coerceIn(0, initialState.maxBaseHp)

        for (enemy in initialState.enemies.filter { it.isAlive }.sortedBy { it.id }) {
            if (enemy.slowTurns > 0) {
                nextEnemies += enemy.copy(slowTurns = enemy.slowTurns - 1)
                continue
            }

            val nextProgress = enemy.progress + 1
            if (nextProgress >= rules.pathLength) {
                val damage = enemy.attackDamage.coerceAtLeast(0)
                baseHp = (baseHp - damage).coerceAtLeast(0)
                events += CombatEvent(CombatEventType.BASE_DAMAGED, enemy.id, damage)
            } else {
                nextEnemies += enemy.copy(progress = nextProgress)
                events += CombatEvent(CombatEventType.ENEMY_ADVANCED, enemy.id, nextProgress)
            }
        }

        return CombatResult(
            state = initialState.copy(
                baseHp = baseHp,
                turn = initialState.turn + 1,
                turretStunTurns = (initialState.turretStunTurns - 1).coerceAtLeast(0),
                enemies = nextEnemies
            ),
            events = events
        )
    }
}
