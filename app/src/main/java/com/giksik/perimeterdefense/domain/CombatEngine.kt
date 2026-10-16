package com.giksik.perimeterdefense.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic combat rules. This class has no Android/UI dependencies,
 * so the turn logic can be tested without launching the app.
 */
object CombatEngine {
    const val PATH_LENGTH = 5

    fun newGame(): CombatState = spawnWave(CombatState(), wave = 1)

    fun registerMineDetonation(
        state: CombatState,
        baseDamage: Int = 5,
        stunTurns: Int = 2
    ): CombatResult {
        if (state.isGameOver) return CombatResult(state)
        val damage = baseDamage.coerceAtLeast(0)
        val nextHp = (state.baseHp - damage).coerceAtLeast(0)
        val nextStun = maxOf(state.turretStunTurns, stunTurns.coerceAtLeast(0))
        val events = buildList {
            if (damage > 0) add(CombatEvent(CombatEventType.BASE_DAMAGED, amount = state.baseHp - nextHp))
            if (nextStun > 0) add(CombatEvent(CombatEventType.TURRETS_STUNNED, amount = nextStun))
        }
        return CombatResult(state.copy(baseHp = nextHp, turretStunTurns = nextStun), events)
    }

    /** A captured mine is launched at the most advanced enemy. */
    fun launchCapturedMine(state: CombatState, damage: Int = 25): CombatResult {
        if (state.isGameOver) return CombatResult(state)
        val target = state.enemies
            .filter { it.isAlive }
            .sortedWith(compareByDescending<EnemyUnit> { it.progress }.thenBy { it.id })
            .firstOrNull() ?: return CombatResult(state)
        val nextHealth = (target.health - damage.coerceAtLeast(0)).coerceAtLeast(0)
        val events = mutableListOf(CombatEvent(
            CombatEventType.ENEMY_DAMAGED, target.id, minOf(target.health, damage.coerceAtLeast(0))
        ))
        var nextEnemies = state.enemies
        var nextChips = state.chips
        if (nextHealth == 0) {
            nextEnemies = nextEnemies.filterNot { it.id == target.id }
            val reward = target.rewardChips.coerceAtLeast(0)
            nextChips += reward
            events += CombatEvent(CombatEventType.ENEMY_DESTROYED, target.id, reward)
        } else {
            nextEnemies = nextEnemies.map { if (it.id == target.id) it.copy(health = nextHealth) else it }
        }
        return CombatResult(state.copy(enemies = nextEnemies, chips = nextChips), events)
    }

    fun repairBase(state: CombatState, amount: Int = 15): CombatResult {
        if (state.isGameOver) return CombatResult(state)
        val before = state.baseHp
        val after = (before + amount.coerceAtLeast(0)).coerceAtMost(state.maxBaseHp)
        val repaired = after - before
        return CombatResult(
            state = state.copy(baseHp = after),
            events = if (repaired > 0) listOf(CombatEvent(CombatEventType.BASE_REPAIRED, amount = repaired)) else emptyList()
        )
    }

    /** Damages every active enemy; rewards are granted only for destroyed units. */
    fun launchAirstrike(state: CombatState, damage: Int = 18): CombatResult {
        if (state.isGameOver || state.enemies.isEmpty()) return CombatResult(state)
        val appliedDamage = damage.coerceAtLeast(0)
        val events = mutableListOf<CombatEvent>()
        var chips = state.chips
        val survivors = mutableListOf<EnemyUnit>()

        for (enemy in state.enemies.filter { it.isAlive }.sortedBy { it.id }) {
            val remainingHealth = (enemy.health - appliedDamage).coerceAtLeast(0)
            events += CombatEvent(
                CombatEventType.ENEMY_DAMAGED,
                enemyId = enemy.id,
                amount = minOf(enemy.health, appliedDamage)
            )
            if (remainingHealth == 0) {
                val reward = enemy.rewardChips.coerceAtLeast(0)
                chips += reward
                events += CombatEvent(CombatEventType.ENEMY_DESTROYED, enemy.id, reward)
            } else {
                survivors += enemy.copy(health = remainingHealth)
            }
        }

        return CombatResult(
            state = state.copy(enemies = survivors, chips = chips),
            events = events
        )
    }

    private fun spawnWave(state: CombatState, wave: Int): CombatState {
        val safeWave = wave.coerceAtLeast(1)
        val count = (2 + safeWave).coerceAtMost(6)
        val enemies = List(count) { index ->
            val type = when {
                safeWave % 5 == 0 && index == count - 1 -> EnemyType.BOSS
                safeWave >= 3 && index % 3 == 2 -> EnemyType.ARMORED
                safeWave >= 2 && index % 2 == 1 -> EnemyType.RAIDER
                else -> EnemyType.SCOUT
            }
            val health = when (type) {
                EnemyType.SCOUT -> 18 + safeWave * 4
                EnemyType.RAIDER -> 28 + safeWave * 5
                EnemyType.ARMORED -> 45 + safeWave * 7
                EnemyType.BOSS -> 100 + safeWave * 12
            }
            val attack = when (type) {
                EnemyType.SCOUT -> 5 + safeWave
                EnemyType.RAIDER -> 7 + safeWave
                EnemyType.ARMORED -> 10 + safeWave
                EnemyType.BOSS -> 15 + safeWave * 2
            }
            val reward = when (type) {
                EnemyType.SCOUT -> 10 + safeWave * 3
                EnemyType.RAIDER -> 15 + safeWave * 4
                EnemyType.ARMORED -> 25 + safeWave * 5
                EnemyType.BOSS -> 75 + safeWave * 10
            }
            EnemyUnit(
                id = safeWave * 100L + index + 1,
                type = type,
                lane = (index * 2 + safeWave) % GridEngine.GRID_SIZE,
                progress = 0,
                health = health,
                maxHealth = health,
                attackDamage = attack,
                rewardChips = reward
            )
        }
        return state.copy(wave = safeWave, enemies = enemies)
    }

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
        rules: CombatRules = CombatRules(pathLength = PATH_LENGTH)
    ): CombatResult {
        if (initialState.isGameOver) return CombatResult(initialState)
        if (initialState.enemies.isEmpty()) {
            return CombatResult(
                state = spawnWave(initialState, initialState.wave + 1).copy(turn = initialState.turn + 1)
            )
        }

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
