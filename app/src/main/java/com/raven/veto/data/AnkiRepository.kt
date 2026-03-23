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

@Singleton
class AnkiRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appRepository: AppRepository
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
            // Try getting due counts [learn, review, new] from decks table
            try {
                withTimeout(5000L) {
                    // Track daily reviews by comparing totalDue changes over time
                    val prefs =
                        context.getSharedPreferences("veto_anki_stats", Context.MODE_PRIVATE)

                    var lastTotalDue =
                        prefs.getInt("last_total_due", -1) // -1 indicates not initialized

                    val stats =
                        tryQueryDeckStats() ?: // Query failed. Return last known good state.
                        return@withTimeout AnkiStats(
                            totalDue = if (lastTotalDue == -1) 0 else lastTotalDue
                        )

                    // If this is the first successful query ever (or after clear data)
                    if (lastTotalDue == -1) {
                        lastTotalDue = stats.totalDue
                        prefs.edit {
                            putInt("last_total_due", lastTotalDue)
                        }
                    }

                    // Compare and update Balance
                    val diff = lastTotalDue - stats.totalDue
                    if (diff > 0) {
                        // User reviewed 'diff' cards. Add to balance.
                        appRepository.addEarnedTime(diff)
                    }

                    // Update lastTotalDue to current
                    if (lastTotalDue != stats.totalDue) {
                        lastTotalDue = stats.totalDue
                        prefs.edit {
                            putInt("last_total_due", lastTotalDue)
                        }
                    }

                    stats
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

    private fun tryQueryDeckStats(): AnkiStats? {
        try {
            val uri = FlashCardsContract.Deck.CONTENT_ALL_URI
            val projection = arrayOf(FlashCardsContract.Deck.DECK_COUNTS)

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor == null) {
                Log.e(TAG, "Deck query returned null cursor for $uri")
                return null
            }

            cursor.use {
                var totalNew = 0
                var totalLearn = 0
                var totalReview = 0

                val countsColIdx = it.getColumnIndex(FlashCardsContract.Deck.DECK_COUNTS)

                if (countsColIdx == -1) {
                    Log.e(
                        TAG,
                        "'${FlashCardsContract.Deck.DECK_COUNTS}' column not found in decks table"
                    )
                    return null
                }

                while (it.moveToNext()) {
                    val countsJson = it.getString(countsColIdx)

                    if (countsJson != null) {
                        try {
                            // format: [learn, review, new]
                            val counts = JSONArray(countsJson)
                            val learn = counts.optInt(0, 0)
                            val review = counts.optInt(1, 0)
                            val new = counts.optInt(2, 0)

                            totalLearn += learn
                            totalReview += review
                            totalNew += new
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing deck counts JSON: $countsJson", e)
                        }
                    }
                }
                val totalDue = totalLearn + totalReview + totalNew
                Log.d(TAG, "Total Due: $totalDue (L:$totalLearn, R:$totalReview, N:$totalNew)")
                return AnkiStats(
                    new = totalNew,
                    learn = totalLearn,
                    review = totalReview,
                    totalDue = totalDue
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deck counts query failed: ${e.message}")
            return null
        }
    }
}