package com.raven.veto.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.ichi2.anki.FlashCardsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import com.raven.veto.domain.AddEarnedTimeUseCase

@Singleton
class AnkiRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val addEarnedTimeUseCase: AddEarnedTimeUseCase
) {
    private val TAG = "AnkiRepository"
    private val mutex = Mutex()

    fun getAnkiStats(): Flow<AnkiStats> = callbackFlow {
        // Helper to fetch and send updates safely
        val update = suspend {
            trySend(queryAnkiStats())
        }

        // Initial emit
        launch(Dispatchers.IO) { update() }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) { update() }
            }
        }

        try {
            // Registering on decks URI for updates
            val uri = FlashCardsContract.Deck.CONTENT_ALL_URI
            context.contentResolver.registerContentObserver(uri, true, observer)
        } catch (e: Exception) {
            Log.w(TAG, "Observer registration failed: ${e.message}")
        }

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO)

    private fun getTodayKey(): String {
        val now = java.time.LocalDateTime.now()
        val adjustedDate = if (now.hour < 4) now.minusDays(1) else now
        return java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(adjustedDate)
    }

    fun getCachedStats(): AnkiStats {
        val prefs = context.getSharedPreferences("veto_anki_stats", Context.MODE_PRIVATE)
        // We only care about last_total_due now. Reset was handled by AppRepository for balance.
        return AnkiStats(
            totalDue = prefs.getInt("last_total_due", 0)
        )
    }

    suspend fun forceCheck(): AnkiStats {
        return queryAnkiStats()
    }

    private suspend fun queryAnkiStats(): AnkiStats = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                withTimeout(5000L) {
                    val prefs = context.getSharedPreferences("veto_anki_stats", Context.MODE_PRIVATE)
                    val lastDeckCountsStr = prefs.getString("last_deck_due_counts", "{}") ?: "{}"
                    val lastDeckCounts = org.json.JSONObject(lastDeckCountsStr)

                    val currentDeckCounts = tryQueryDeckCounts() ?: return@withTimeout AnkiStats(
                        totalDue = prefs.getInt("last_total_due", 0)
                    )

                    val diffs = mutableMapOf<Long, Int>()
                    var newTotalDue = 0

                    for ((deckId, count) in currentDeckCounts) {
                        newTotalDue += count
                        val lastCount = if (lastDeckCounts.has(deckId.toString())) lastDeckCounts.getInt(deckId.toString()) else -1

                        if (lastCount != -1) {
                            val diff = lastCount - count
                            if (diff > 0) {
                                diffs[deckId] = diff
                            }
                        }
                    }

                    val isFirstRun = prefs.getInt("last_total_due", -1) == -1
                    if (isFirstRun) {
                        prefs.edit {
                            putInt("last_total_due", newTotalDue)
                            putString("last_deck_due_counts", org.json.JSONObject(currentDeckCounts.mapKeys { it.key.toString() }).toString())
                        }
                        return@withTimeout AnkiStats(totalDue = newTotalDue)
                    }

                    if (diffs.isNotEmpty()) {
                        addEarnedTimeUseCase(diffs)
                    }

                    val lastTotalDue = prefs.getInt("last_total_due", -1)
                    if (lastTotalDue != newTotalDue || diffs.isNotEmpty()) {
                        prefs.edit {
                            putInt("last_total_due", newTotalDue)
                            putString("last_deck_due_counts", org.json.JSONObject(currentDeckCounts.mapKeys { it.key.toString() }).toString())
                        }
                    }

                    AnkiStats(totalDue = newTotalDue)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Query interrupted or failed: ${e.message}")
                // Return last valid state if possible
                val prefs = context.getSharedPreferences("veto_anki_stats", Context.MODE_PRIVATE)
                AnkiStats(
                    totalDue = prefs.getInt("last_total_due", 0)
                )
            }
        }
    }

    private fun tryQueryDeckCounts(): Map<Long, Int>? {
        try {
            val uri = FlashCardsContract.Deck.CONTENT_ALL_URI
            val projection = arrayOf(FlashCardsContract.Deck.DECK_ID, FlashCardsContract.Deck.DECK_COUNTS)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor == null) return null

            val map = mutableMapOf<Long, Int>()
            cursor.use {
                val idColIdx = it.getColumnIndex(FlashCardsContract.Deck.DECK_ID).takeIf { idx -> idx != -1 } ?: it.getColumnIndex("_id")
                val countsColIdx = it.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)

                if (countsColIdx == -1 || idColIdx == -1) {
                    Log.e(TAG, "Column not found. Available: ${it.columnNames.joinToString()}")
                    return null
                }

                while (it.moveToNext()) {
                    val deckId = it.getLong(idColIdx)
                    val countsJson = it.getString(countsColIdx)
                    var due = 0
                    if (countsJson != null) {
                        try {
                            val counts = JSONArray(countsJson)
                            due = counts.optInt(0, 0) + counts.optInt(1, 0) + counts.optInt(2, 0)
                        } catch (e: Exception) { }
                    }
                    map[deckId] = due
                }
            }
            return map
        } catch (e: Exception) {
            Log.e(TAG, "Deck counts query failed: ${e.message}")
            return null
        }
    }

    data class DeckInfo(val id: Long, val name: String)

    suspend fun getAvailableDecks(): List<DeckInfo> = withContext(Dispatchers.IO) {
        val decks = mutableListOf<DeckInfo>()
        val uri = FlashCardsContract.Deck.CONTENT_ALL_URI
        val projection = arrayOf("_id", "name", "deck_name") // cover bases for name

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null) // null projection to get all columns
            cursor?.use {
                val idIdx = it.getColumnIndex("_id").takeIf { idx -> idx != -1 } ?: it.getColumnIndex("deck_id")
                val nameIdx = it.getColumnIndex("name").takeIf { idx -> idx != -1 } ?: it.getColumnIndex("deck_name")
                
                if (idIdx != -1 && nameIdx != -1) {
                    while (it.moveToNext()) {
                        decks.add(DeckInfo(it.getLong(idIdx), it.getString(nameIdx)))
                    }
                } else {
                    Log.e(TAG, "Columns not found for deck info. Available: ${it.columnNames.joinToString()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching decks: ${e.message}")
        }
        return@withContext decks
    }
}