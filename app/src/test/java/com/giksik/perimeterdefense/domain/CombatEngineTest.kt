package com.giksik.perimeterdefense.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatEngineTest {
    private fun group(color: AmmoColor, size: Int = 3) = MatchGroup(
        ammoColor = color,
        cells = (0 until size).map { 0 to it }.toSet()
    )

    private fun enemy(
        id: Long = 1L,
        type: EnemyType = EnemyType.SCOUT,
        lane: Int = 2,
        progress: Int = 3,
        health: Int = 20,
        maxHealth: Int = health,
        attackDamage: Int = 10,
        rewardChips: Int = 25,
        slowTurns: Int = 0
    ) = EnemyUnit(id, type, lane, progress, health, maxHealth, attackDamage, rewardChips, slowTurns)

    @Test
    fun redMatchDamagesMostAdvancedEnemyAndAwardsChipsOnKill() {
        val state = CombatState(enemies = listOf(
            enemy(id = 1L, progress = 1, health = 20),
            enemy(id = 2L, progress = 4, health = 20, rewardChips = 40)
        ))

        val result = CombatEngine.resolveMatches(state, listOf(group(AmmoColor.RED)))

        assertEquals(listOf(1L), result.state.enemies.map { it.id })
        assertEquals(40, result.state.chips)
        assertTrue(result.events.any { it.type == CombatEventType.ENEMY_DESTROYED && it.enemyId == 2L })
    }

    @Test
    fun fourCellRedMatchUsesOnePointFiveDamageMultiplier() {
        val state = CombatState(enemies = listOf(enemy(health = 50, maxHealth = 50)))

        val result = CombatEngine.resolveMatches(state, listOf(group(AmmoColor.RED, size = 4)))

        assertEquals(20, result.state.enemies.single().health)
    }

    @Test
    fun blueMatchSlowsSurvivingEnemy() {
        val state = CombatState(enemies = listOf(enemy(health = 50, maxHealth = 50)))

        val result = CombatEngine.resolveMatches(state, listOf(group(AmmoColor.BLUE)))

        assertEquals(42, result.state.enemies.single().health)
        assertEquals(1, result.state.enemies.single().slowTurns)
        assertTrue(result.events.any { it.type == CombatEventType.ENEMY_SLOWED })
    }

    @Test
    fun yellowMatchDamagesNearbyEnemiesInSameLaneOnly() {
        val state = CombatState(enemies = listOf(
            enemy(id = 1L, lane = 2, progress = 4, health = 50, maxHealth = 50),
            enemy(id = 2L, lane = 2, progress = 3, health = 50, maxHealth = 50),
            enemy(id = 3L, lane = 1, progress = 4, health = 50, maxHealth = 50)
        ))

        val result = CombatEngine.resolveMatches(state, listOf(group(AmmoColor.YELLOW)))

        assertEquals(38, result.state.enemies.first { it.id == 1L }.health)
        assertEquals(38, result.state.enemies.first { it.id == 2L }.health)
        assertEquals(50, result.state.enemies.first { it.id == 3L }.health)
    }

    @Test
    fun greenMatchRepairsBaseWithoutExceedingMaximum() {
        val state = CombatState(baseHp = 90, maxBaseHp = 100)

        val result = CombatEngine.resolveMatches(state, listOf(group(AmmoColor.GREEN_HEAL, size = 4)))

        assertEquals(100, result.state.baseHp)
        assertTrue(result.events.any { it.type == CombatEventType.BASE_REPAIRED && it.amount == 10 })
    }

    @Test
    fun slowedEnemyLosesMovementTurnAndEnemyAtBaseDamagesShield() {
        val state = CombatState(baseHp = 30, enemies = listOf(
            enemy(id = 1L, progress = 2, slowTurns = 1),
            enemy(id = 2L, progress = 4, attackDamage = 12)
        ))

        val result = CombatEngine.advanceTurn(state)

        assertEquals(18, result.state.baseHp)
        assertEquals(2, result.state.enemies.first { it.id == 1L }.progress)
        assertEquals(0, result.state.enemies.first { it.id == 1L }.slowTurns)
        assertFalse(result.state.isGameOver)
        assertFalse(result.state.enemies.any { it.id == 2L })
    }

    @Test
    fun stunnedTurretsDoNotAttackButGreenStillRepairs() {
        val state = CombatState(
            baseHp = 50,
            turretStunTurns = 2,
            enemies = listOf(enemy(health = 50, maxHealth = 50))
        )

        val result = CombatEngine.resolveMatches(
            state,
            listOf(group(AmmoColor.RED), group(AmmoColor.GREEN_HEAL))
        )

        assertEquals(50, result.state.enemies.single().health)
        assertEquals(65, result.state.baseHp)
        assertTrue(result.events.any { it.type == CombatEventType.TURRETS_STUNNED })
    }
    @Test
    fun newGameCreatesFirstWaveAndEmptyWaveAdvances() {
        val initial = CombatEngine.newGame()
        assertEquals(1, initial.wave)
        assertEquals(3, initial.enemies.size)

        val next = CombatEngine.advanceTurn(initial.copy(enemies = emptyList()))
        assertEquals(2, next.state.wave)
        assertEquals(4, next.state.enemies.size)
    }

    @Test
    fun mineDetonationDamagesBaseAndStunsTurrets() {
        val result = CombatEngine.registerMineDetonation(CombatState(baseHp = 50))

        assertEquals(45, result.state.baseHp)
        assertEquals(2, result.state.turretStunTurns)
    }

    @Test
    fun capturedMineDamagesTheMostAdvancedEnemy() {
        val state = CombatState(enemies = listOf(
            enemy(id = 1L, progress = 1, health = 50, maxHealth = 50),
            enemy(id = 2L, progress = 3, health = 50, maxHealth = 50)
        ))

        val result = CombatEngine.launchCapturedMine(state)

        assertEquals(25, result.state.enemies.first { it.id == 2L }.health)
        assertEquals(50, result.state.enemies.first { it.id == 1L }.health)
    }

}
