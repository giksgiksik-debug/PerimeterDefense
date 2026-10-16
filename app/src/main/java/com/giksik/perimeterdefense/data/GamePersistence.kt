package com.giksik.perimeterdefense.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.giksik.perimeterdefense.domain.AmmoColor
import com.giksik.perimeterdefense.domain.CellState
import com.giksik.perimeterdefense.domain.CombatState
import com.giksik.perimeterdefense.domain.EnemyType
import com.giksik.perimeterdefense.domain.EnemyUnit
import com.giksik.perimeterdefense.domain.GridCell
import com.giksik.perimeterdefense.domain.GridEngine
import com.giksik.perimeterdefense.domain.InteractionMode
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
 * Persists the complete playable snapshot, not just high-level preferences.
 * DataStore writes are asynchronous and kept outside the domain engine.
 */
class GamePersistence(context: Context) {
    private val dataStore = context.applicationContext.perimeterGameDataStore
    private val stateKey = stringPreferencesKey("game_snapshot_v1")

    suspend fun load(): SavedGame? {
        return try {
            val raw = dataStore.data.first()[stateKey] ?: return null
            decode(raw)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A corrupt/older save should start a fresh game rather than crash the app.
            null
        }
    }

    suspend fun save(game: SavedGame) {
        try {
            dataStore.edit { preferences ->
                preferences[stateKey] = encode(game)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Saving must not crash an active game if storage is temporarily unavailable.
        }
    }

    private fun encode(game: SavedGame): String {
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

        val combat = JSONObject()
            .put("wave", game.combat.wave)
            .put("baseHp", game.combat.baseHp)
            .put("maxBaseHp", game.combat.maxBaseHp)
            .put("chips", game.combat.chips)
            .put("turn", game.combat.turn)
            .put("turretStunTurns", game.combat.turretStunTurns)
            .put("enemies", enemies)

        return JSONObject()
            .put("version", 1)
            .put("grid", rows)
            .put("combat", combat)
            .put("mode", game.mode.name)
            .toString()
    }

    private fun decode(raw: String): SavedGame {
        val root = JSONObject(raw)
        val rows = root.getJSONArray("grid")
        val decodedGrid = List(rows.length()) { rowIndex ->
            val cells = rows.getJSONArray(rowIndex)
            List(cells.length()) { colIndex ->
                val cell = cells.getJSONObject(colIndex)
                GridCell(
                    row = cell.optInt("row", rowIndex),
                    col = cell.optInt("col", colIndex),
                    state = CellState.valueOf(cell.getString("state")),
                    hasMine = cell.optBoolean("hasMine", false),
                    ammoColor = AmmoColor.valueOf(cell.getString("ammoColor")),
                    adjacentMines = cell.optInt("adjacentMines", 0),
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
            EnemyUnit(
                id = enemy.getLong("id"),
                type = EnemyType.valueOf(enemy.getString("type")),
                lane = enemy.getInt("lane"),
                progress = enemy.getInt("progress"),
                health = enemy.getInt("health"),
                maxHealth = enemy.getInt("maxHealth"),
                attackDamage = enemy.getInt("attackDamage"),
                rewardChips = enemy.getInt("rewardChips"),
                slowTurns = enemy.optInt("slowTurns", 0)
            )
        }
        val maxBaseHp = combatJson.optInt("maxBaseHp", 100).coerceAtLeast(1)
        val combat = CombatState(
            wave = combatJson.optInt("wave", 1).coerceAtLeast(1),
            baseHp = combatJson.optInt("baseHp", maxBaseHp).coerceIn(0, maxBaseHp),
            maxBaseHp = maxBaseHp,
            chips = combatJson.optInt("chips", 0).coerceAtLeast(0),
            turn = combatJson.optInt("turn", 0).coerceAtLeast(0),
            turretStunTurns = combatJson.optInt("turretStunTurns", 0).coerceAtLeast(0),
            enemies = enemies
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
