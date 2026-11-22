package com.giksgik.perimeterdefense

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.giksgik.perimeterdefense.data.GamePersistence
import com.giksgik.perimeterdefense.data.SavedGame
import com.giksgik.perimeterdefense.domain.AmmoColor
import com.giksgik.perimeterdefense.domain.BoosterCosts
import com.giksgik.perimeterdefense.domain.CombatEngine
import com.giksgik.perimeterdefense.domain.CombatEvent
import com.giksgik.perimeterdefense.domain.CombatEventType
import com.giksgik.perimeterdefense.domain.CombatState
import com.giksgik.perimeterdefense.domain.EnemyUnit
import com.giksgik.perimeterdefense.domain.GridCell
import com.giksgik.perimeterdefense.domain.GridEngine
import com.giksgik.perimeterdefense.domain.GridResult
import com.giksgik.perimeterdefense.domain.HangarUpgradeType
import com.giksgik.perimeterdefense.domain.InteractionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val OPENING_HINT =
    "ПЕРВЫЙ ХОД: свайпни вертикально между двумя центральными цветными клетками."

data class PerimeterGameUiState(
    val isLoaded: Boolean = false,
    val soundEnabled: Boolean = true,
    val bestWave: Int = 1,
    val showPauseDialog: Boolean = false,
    val showHangarDialog: Boolean = false,
    val grid: List<List<GridCell>> = GridEngine.newGrid(),
    val mode: InteractionMode = InteractionMode.DRILL_REVEAL,
    val combat: CombatState = CombatEngine.newGame(),
    val status: String = OPENING_HINT,
    val visualEvents: List<CombatEvent> = emptyList(),
    val visualTargets: List<EnemyUnit> = emptyList(),
    val visualEffectKey: Int = 0
)

/**
 * Owns gameplay state, Hangar upgrades, booster economy, pause/dialog visibility,
 * and DataStore persistence outside of Compose UI.
 */
class PerimeterGameViewModel(application: Application) : AndroidViewModel(application) {
    private val persistence = GamePersistence(application.applicationContext)

    private val _uiState = MutableStateFlow(PerimeterGameUiState())
    val uiState: StateFlow<PerimeterGameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedSound = persistence.loadSoundEnabled()
            val savedBestWave = persistence.loadBestWave()
            TacticalSoundSynth.soundEnabled = savedSound
            val saved = persistence.load()
            _uiState.update { current ->
                if (saved != null) {
                    val migratedGrid = if (saved.combat.wave == 1 && saved.combat.turn <= 1) {
                        GridEngine.ensureOpeningMatch(saved.grid)
                    } else {
                        saved.grid
                    }
                    current.copy(
                        isLoaded = true,
                        soundEnabled = savedSound,
                        bestWave = maxOf(savedBestWave, saved.combat.wave),
                        grid = migratedGrid,
                        combat = saved.combat,
                        mode = saved.mode
                    )
                } else {
                    current.copy(
                        isLoaded = true,
                        soundEnabled = savedSound,
                        bestWave = savedBestWave
                    )
                }
            }
            persistCurrentState()
        }
    }

    fun openPause() {
        if (!_uiState.value.isLoaded) return
        _uiState.update { it.copy(showPauseDialog = true, showHangarDialog = false) }
    }

    fun closePause() {
        _uiState.update { it.copy(showPauseDialog = false) }
    }

    fun openHangar() {
        if (!_uiState.value.isLoaded || _uiState.value.combat.isGameOver) return
        _uiState.update { it.copy(showHangarDialog = true, showPauseDialog = false) }
    }

    fun closeHangar() {
        _uiState.update { it.copy(showHangarDialog = false) }
    }

    fun setMode(mode: InteractionMode) {
        if (!_uiState.value.isLoaded) return
        _uiState.update { it.copy(mode = mode) }
        persistCurrentState()
    }

    fun toggleSound() {
        val nextEnabled = !_uiState.value.soundEnabled
        TacticalSoundSynth.soundEnabled = nextEnabled
        _uiState.update { it.copy(soundEnabled = nextEnabled) }
        viewModelScope.launch {
            persistence.saveSoundEnabled(nextEnabled)
        }
    }

    fun onCellTap(row: Int, col: Int) {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver || state.showPauseDialog || state.showHangarDialog) return
        val result = if (state.mode == InteractionMode.DRILL_REVEAL) {
            GridEngine.reveal(state.grid, row, col)
        } else {
            GridEngine.capture(state.grid, row, col)
        }
        applyGridResult(result)
    }

    fun onCellLongPress(row: Int, col: Int) {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver || state.showPauseDialog || state.showHangarDialog) return
        val result = GridEngine.toggleFlag(state.grid, row, col)
        TacticalSoundSynth.playArmorClick()
        _uiState.update { it.copy(grid = result.grid, status = result.message) }
        persistCurrentState()
    }

    fun onSwipe(row1: Int, col1: Int, row2: Int, col2: Int) {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver || state.showPauseDialog || state.showHangarDialog) return
        val swap = GridEngine.swapAdjacent(state.grid, row1, col1, row2, col2)
        if (!swap.accepted) {
            TacticalSoundSynth.playErrorBuzz()
            _uiState.update { it.copy(status = swap.message) }
            return
        }
        applyGridResult(
            GridResult(
                grid = swap.grid,
                revealedCells = 2,
                turnConsumed = true,
                message = swap.message
            )
        )
    }

    fun useScanner() {
        val state = _uiState.value
        if (!state.isLoaded) return
        TacticalSoundSynth.playArmorClick()
        val mines = state.grid.flatten().count { it.hasMine }
        _uiState.update {
            it.copy(status = "СКАНЕР: мин на поле — $mines; целей на радаре — ${state.combat.enemies.size}.")
        }
    }

    fun launchAirstrike() {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver) return
        if (state.combat.enemies.isEmpty()) {
            TacticalSoundSynth.playErrorBuzz()
            _uiState.update { it.copy(status = "Авиаудар: на радаре нет целей.") }
            return
        }
        if (state.combat.chips < BoosterCosts.AIRSTRIKE) {
            TacticalSoundSynth.playErrorBuzz()
            _uiState.update { it.copy(status = "Авиаудар: нужно ${BoosterCosts.AIRSTRIKE} CR.") }
            return
        }
        val result = CombatEngine.activateAirstrikeBooster(state.combat) ?: return
        TacticalSoundSynth.playHeavyExplosion()
        val nextCombat = CombatEngine.advanceTurn(result.state).state
        val damaged = result.events.count { it.type == CombatEventType.ENEMY_DAMAGED }
        val destroyed = result.events.count { it.type == CombatEventType.ENEMY_DESTROYED }
        val targetIds = result.events.mapNotNull { it.enemyId }.toSet()

        _uiState.update { current ->
            current.copy(
                combat = nextCombat,
                bestWave = maxOf(current.bestWave, nextCombat.wave),
                status = "Авиаудар (-${BoosterCosts.AIRSTRIKE} CR): поражено $damaged, уничтожено $destroyed.",
                visualEvents = if (result.events.isNotEmpty()) result.events else current.visualEvents,
                visualTargets = if (result.events.isNotEmpty()) {
                    state.combat.enemies.filter { it.id in targetIds }
                } else current.visualTargets,
                visualEffectKey = if (result.events.isNotEmpty()) current.visualEffectKey + 1 else current.visualEffectKey
            )
        }
        persistCurrentState()
    }

    fun repairBase() {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver) return
        if (state.combat.baseHp >= state.combat.maxBaseHp) {
            TacticalSoundSynth.playErrorBuzz()
            _uiState.update { it.copy(status = "Ремонт: щит базы уже на максимуме.") }
            return
        }
        if (state.combat.chips < BoosterCosts.REPAIR) {
            TacticalSoundSynth.playErrorBuzz()
            _uiState.update { it.copy(status = "Ремонт: нужно ${BoosterCosts.REPAIR} CR.") }
            return
        }
        val result = CombatEngine.activateRepairBooster(state.combat) ?: return
        TacticalSoundSynth.playTurretFire(AmmoColor.GREEN_HEAL)
        val nextCombat = CombatEngine.advanceTurn(result.state).state
        val repaired = result.events
            .filter { it.type == CombatEventType.BASE_REPAIRED }
            .sumOf { it.amount }

        _uiState.update { current ->
            current.copy(
                combat = nextCombat,
                bestWave = maxOf(current.bestWave, nextCombat.wave),
                status = "Ремонт (-${BoosterCosts.REPAIR} CR): восстановлено +$repaired HP.",
                visualEvents = if (result.events.isNotEmpty()) result.events else current.visualEvents,
                visualTargets = if (result.events.isNotEmpty()) emptyList() else current.visualTargets,
                visualEffectKey = if (result.events.isNotEmpty()) current.visualEffectKey + 1 else current.visualEffectKey
            )
        }
        persistCurrentState()
    }

    fun buyUpgrade(type: HangarUpgradeType) {
        val state = _uiState.value
        if (!state.isLoaded || state.combat.isGameOver) return
        val cost = state.combat.upgrades.costOf(type)
        val result = CombatEngine.purchaseUpgrade(state.combat, type)
        if (result == null) {
            TacticalSoundSynth.playErrorBuzz()
            val msg = if (!state.combat.upgrades.canUpgrade(type)) {
                "Улучшение уже на максимальном уровне."
            } else {
                "Недостаточно чипов (нужно $cost CR)."
            }
            _uiState.update { it.copy(status = msg) }
            return
        }
        TacticalSoundSynth.playTurretFire(AmmoColor.GREEN_HEAL)
        val label = when (type) {
            HangarUpgradeType.MORTAR -> "Мортира"
            HangarUpgradeType.CRYO -> "Крио-луч"
            HangarUpgradeType.TESLA -> "Тесла"
            HangarUpgradeType.SHIELD -> "Щит базы"
        }
        val newLevel = result.state.upgrades.levelOf(type)
        _uiState.update { current ->
            current.copy(
                combat = result.state,
                status = "АНГАР: $label улучшена до ур. $newLevel (-$cost CR)."
            )
        }
        persistCurrentState()
    }

    fun restartGame() {
        _uiState.update { current ->
            current.copy(
                showPauseDialog = false,
                showHangarDialog = false,
                grid = GridEngine.newGrid(),
                combat = CombatEngine.newGame(),
                mode = InteractionMode.DRILL_REVEAL,
                status = OPENING_HINT,
                visualEvents = emptyList(),
                visualTargets = emptyList()
            )
        }
        persistCurrentState()
    }

    private fun applyGridResult(result: GridResult) {
        val state = _uiState.value
        if (!result.turnConsumed) {
            _uiState.update { it.copy(grid = result.grid, status = result.message) }
            return
        }

        var nextCombat = state.combat
        val turnVisualEvents = mutableListOf<CombatEvent>()
        val visualCandidates = LinkedHashMap<Long, EnemyUnit>().apply {
            putAll(state.combat.enemies.associateBy { it.id })
        }

        if (result.detonatedMines > 0) {
            TacticalSoundSynth.playHeavyExplosion()
            val detonation = CombatEngine.registerMineDetonation(nextCombat)
            nextCombat = detonation.state
            turnVisualEvents += detonation.events
        }
        if (result.capturedMines > 0) {
            TacticalSoundSynth.playMineCatapult()
            nextCombat = nextCombat.copy(chips = nextCombat.chips + result.capturedMines * 25)
            repeat(result.capturedMines) {
                visualCandidates.putAll(nextCombat.enemies.associateBy { it.id })
                val capturedShot = CombatEngine.launchCapturedMine(nextCombat)
                nextCombat = capturedShot.state
                turnVisualEvents += capturedShot.events
            }
        }

        val matchResolution = GridEngine.resolveMatches(result.grid)
        matchResolution.groups.take(3).forEach { group ->
            TacticalSoundSynth.playTurretFire(group.ammoColor)
        }
        if (matchResolution.groups.isEmpty() && result.detonatedMines == 0 && result.capturedMines == 0) {
            TacticalSoundSynth.playArmorClick()
        }

        visualCandidates.putAll(nextCombat.enemies.associateBy { it.id })
        val attackResult = CombatEngine.resolveMatches(nextCombat, matchResolution.groups)
        turnVisualEvents += attackResult.events
        visualCandidates.putAll(attackResult.state.enemies.associateBy { it.id })
        val advanceResult = CombatEngine.advanceTurn(attackResult.state)
        turnVisualEvents += advanceResult.events
        nextCombat = advanceResult.state

        val hasVisibleCombatFx = turnVisualEvents.any {
            it.type == CombatEventType.ENEMY_DAMAGED ||
                it.type == CombatEventType.ENEMY_DESTROYED ||
                it.type == CombatEventType.BASE_DAMAGED ||
                it.type == CombatEventType.BASE_REPAIRED ||
                it.type == CombatEventType.TURRETS_STUNNED
        }

        val statusBuilder = StringBuilder(result.message)
        if (matchResolution.groups.isNotEmpty()) {
            val matchedCells = matchResolution.groups.sumOf { it.size }
            statusBuilder.append(" Серии: ${matchResolution.groups.size}, клеток: $matchedCells.")
        }
        if (attackResult.events.any { it.type == CombatEventType.ENEMY_DESTROYED }) {
            statusBuilder.append(" Враг уничтожен.")
        }
        if (attackResult.events.any { it.type == CombatEventType.ENEMY_SLOWED }) {
            statusBuilder.append(" Враг замедлен.")
        }
        if (result.detonatedMines > 0) {
            statusBuilder.append(" Щит повреждён, турели перегружены.")
        }
        if (nextCombat.wave > state.combat.wave) {
            statusBuilder.append(" Волна ${nextCombat.wave}!")
        }
        if (nextCombat.isGameOver) {
            statusBuilder.append(" База разрушена.")
        }

        _uiState.update { current ->
            val targetIds = turnVisualEvents.mapNotNull { it.enemyId }.toSet()
            current.copy(
                grid = matchResolution.grid,
                combat = nextCombat,
                bestWave = maxOf(current.bestWave, nextCombat.wave),
                status = statusBuilder.toString(),
                visualEvents = if (hasVisibleCombatFx) turnVisualEvents.toList() else current.visualEvents,
                visualTargets = if (hasVisibleCombatFx) {
                    visualCandidates.values.filter { it.id in targetIds }
                } else current.visualTargets,
                visualEffectKey = if (hasVisibleCombatFx) current.visualEffectKey + 1 else current.visualEffectKey
            )
        }
        persistCurrentState()
    }

    private fun persistCurrentState() {
        val snapshot = _uiState.value
        if (!snapshot.isLoaded) return
        viewModelScope.launch {
            persistence.save(
                SavedGame(
                    grid = snapshot.grid,
                    combat = snapshot.combat,
                    mode = snapshot.mode
                )
            )
        }
    }
}
