package com.giksgik.perimeterdefense.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.giksgik.perimeterdefense.domain.AmmoColor
import com.giksgik.perimeterdefense.domain.CellState
import com.giksgik.perimeterdefense.domain.CombatEngine
import com.giksgik.perimeterdefense.domain.CombatState
import com.giksgik.perimeterdefense.domain.EnemyType
import com.giksgik.perimeterdefense.domain.EnemyUnit
import com.giksgik.perimeterdefense.domain.GridCell
import com.giksgik.perimeterdefense.domain.GridEngine
import com.giksgik.perimeterdefense.domain.HangarUpgrades
import com.giksgik.perimeterdefense.domain.InteractionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.perimeterGameDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "perimeter_defense_state")

data class SavedGame(
    val grid: List<List<GridCell>>,
    val combat: CombatState,
    val mode: InteractionMode
)

/**
 * Persists the complete playable snapshot, Hangar upgrades, best wave record,
 * and audio preferences in a single DataStore. Supports seamless migration
 * from schema v1 saves.
 */
class GamePersistence(context: Context) {
    private val dataStore = context.applicationContext.perimeterGameDataStore
    private val stateKey = stringPreferencesKey("game_snapshot_v1")
    private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
    private val bestWaveKey = intPreferencesKey("best_wave")

    companion object {
        private const val MIN_SUPPORTED_VERSION = 1
        private const val SCHEMA_VERSION = 2
    }

    suspend fun load(): SavedGame? {
        return try {
            val raw = dataStore.data.first()[stateKey] ?: return null
            decode(raw)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(game: SavedGame) {
        try {
            dataStore.edit { preferences ->
                preferences[stateKey] = encode(game)
                val currentBest = preferences[bestWaveKey] ?: 1
                if (game.combat.wave > currentBest) {
                    preferences[bestWaveKey] = game.combat.wave
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Saving must not crash an active game if storage is temporarily unavailable.
        }
    }

    suspend fun loadSoundEnabled(): Boolean {
        return try {
            dataStore.data.first()[soundEnabledKey] ?: true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            true
        }
    }

    suspend fun saveSoundEnabled(enabled: Boolean) {
        try {
            dataStore.edit { preferences ->
                preferences[soundEnabledKey] = enabled
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preference write failures should not interrupt gameplay.
        }
    }

    suspend fun loadBestWave(): Int {
        return try {
            (dataStore.data.first()[bestWaveKey] ?: 1).coerceAtLeast(1)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            1
        }
    }

    internal fun encode(game: SavedGame): String {
        val rows = JSONArray()
        game.grid.forEach { row ->
            val cells = JSONArray()
            row.forEach { cell ->
                cells.put(
                    JSONObject()
                        .put("row", cell.row)
                        .put("col", cell.col)
                        .put("state", cell.state.name)
                        .put("hasMine", cell.hasMine)
                        .put("ammoColor", cell.ammoColor.name)
                        .put("adjacentMines", cell.adjacentMines)
                        .put("isJammed", cell.isJammed)
                )
            }
            rows.put(cells)
        }

        val enemies = JSONArray()
        game.combat.enemies.forEach { enemy ->
            enemies.put(
                JSONObject()
                    .put("id", enemy.id)
                    .put("type", enemy.type.name)
                    .put("lane", enemy.lane)
                    .put("progress", enemy.progress)
                    .put("health", enemy.health)
                    .put("maxHealth", enemy.maxHealth)
                    .put("attackDamage", enemy.attackDamage)
                    .put("rewardChips", enemy.rewardChips)
                    .put("slowTurns", enemy.slowTurns)
            )
        }

        val upgradesJson = JSONObject()
            .put("mortarLevel", game.combat.upgrades.mortarLevel)
            .put("cryoLevel", game.combat.upgrades.cryoLevel)
            .put("teslaLevel", game.combat.upgrades.teslaLevel)
            .put("shieldLevel", game.combat.upgrades.shieldLevel)

        val combat = JSONObject()
            .put("wave", game.combat.wave)
            .put("baseHp", game.combat.baseHp)
            .put("maxBaseHp", game.combat.maxBaseHp)
            .put("chips", game.combat.chips)
            .put("turn", game.combat.turn)
            .put("turretStunTurns", game.combat.turretStunTurns)
            .put("enemies", enemies)
            .put("upgrades", upgradesJson)

        return JSONObject()
            .put("version", SCHEMA_VERSION)
            .put("grid", rows)
            .put("combat", combat)
            .put("mode", game.mode.name)
            .toString()
    }

    internal fun decode(raw: String): SavedGame {
        val root = JSONObject(raw)
        val version = root.optInt("version", -1)
        require(version in MIN_SUPPORTED_VERSION..SCHEMA_VERSION) {
            "Unsupported save version: $version"
        }

        val rows = root.getJSONArray("grid")
        val decodedGrid = List(rows.length()) { rowIndex ->
            val cells = rows.getJSONArray(rowIndex)
            List(cells.length()) { colIndex ->
                val cell = cells.getJSONObject(colIndex)
                GridCell(
                    row = rowIndex,
                    col = colIndex,
                    state = CellState.valueOf(cell.getString("state")),
                    hasMine = cell.optBoolean("hasMine", false),
                    ammoColor = AmmoColor.valueOf(cell.getString("ammoColor")),
                    adjacentMines = cell.optInt("adjacentMines", 0).coerceIn(0, 8),
                    isJammed = cell.optBoolean("isJammed", false)
                )
            }
        }
        require(decodedGrid.size == GridEngine.GRID_SIZE)
        require(decodedGrid.all { it.size == GridEngine.GRID_SIZE })

        val combatJson = root.getJSONObject("combat")
        val enemiesJson = combatJson.optJSONArray("enemies") ?: JSONArray()
        val enemies = List(enemiesJson.length()) { index ->
            val enemy = enemiesJson.getJSONObject(index)
            val maxHealth = enemy.getInt("maxHealth").coerceAtLeast(1)
            EnemyUnit(
                id = enemy.getLong("id"),
                type = EnemyType.valueOf(enemy.getString("type")),
                lane = enemy.getInt("lane").coerceIn(0, GridEngine.GRID_SIZE - 1),
                progress = enemy.getInt("progress").coerceIn(0, CombatEngine.PATH_LENGTH - 1),
                health = enemy.getInt("health").coerceIn(0, maxHealth),
                maxHealth = maxHealth,
                attackDamage = enemy.getInt("attackDamage").coerceAtLeast(0),
                rewardChips = enemy.getInt("rewardChips").coerceAtLeast(0),
                slowTurns = enemy.optInt("slowTurns", 0).coerceAtLeast(0)
            )
        }
        val upgradesJson = combatJson.optJSONObject("upgrades")
        val upgrades = if (upgradesJson != null) {
            HangarUpgrades(
                mortarLevel = upgradesJson.optInt("mortarLevel", 0).coerceIn(0, HangarUpgrades.MAX_LEVEL),
                cryoLevel = upgradesJson.optInt("cryoLevel", 0).coerceIn(0, HangarUpgrades.MAX_LEVEL),
                teslaLevel = upgradesJson.optInt("teslaLevel", 0).coerceIn(0, HangarUpgrades.MAX_LEVEL),
                shieldLevel = upgradesJson.optInt("shieldLevel", 0).coerceIn(0, HangarUpgrades.MAX_LEVEL)
            )
        } else {
            HangarUpgrades()
        }

        val maxBaseHp = combatJson.optInt("maxBaseHp", 100).coerceAtLeast(1)
        val combat = CombatState(
            wave = combatJson.optInt("wave", 1).coerceAtLeast(1),
            baseHp = combatJson.optInt("baseHp", maxBaseHp).coerceIn(0, maxBaseHp),
            maxBaseHp = maxBaseHp,
            chips = combatJson.optInt("chips", 0).coerceAtLeast(0),
            turn = combatJson.optInt("turn", 0).coerceAtLeast(0),
            turretStunTurns = combatJson.optInt("turretStunTurns", 0).coerceAtLeast(0),
            enemies = enemies,
            upgrades = upgrades
        )
        val mode = InteractionMode.valueOf(
            root.optString("mode", InteractionMode.DRILL_REVEAL.name)
        )
        return SavedGame(
            grid = GridEngine.recalculateAdjacentMines(decodedGrid),
            combat = combat,
            mode = mode
        )
    }
}
