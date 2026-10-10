package org.cssnr.parking

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.cssnr.parking.data.HistoryRepository
import org.cssnr.parking.data.SettingsRepository
import org.cssnr.parking.data.db.AppDatabase
import org.cssnr.parking.ui.ParKingApp
import org.cssnr.parking.ui.theme.ParKingTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pruneExpiredHistory()
        enableEdgeToEdge()
        setContent {
            ParKingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParKingApp()
                }
            }
        }
    }

    /**
     * Deletes history older than the retention setting, once per cold start.
     *
     * Fire-and-forget on purpose: pruning is housekeeping nobody waits on, and
     * a failure only leaves stale rows until the next launch, so it is logged
     * and dropped. Disabled (null retention) skips the query entirely.
     */
    private fun pruneExpiredHistory() {
        lifecycleScope.launch {
            runCatching {
                val context = applicationContext
                val retention = SettingsRepository.retentionMillis(
                    SettingsRepository(context).historyDuration.first(),
                ) ?: return@launch
                val removed = HistoryRepository(AppDatabase.getDatabase(context))
                    .prune(System.currentTimeMillis() - retention)
                Log.d(TAG, "Pruned $removed expired history items")
            }.onFailure { Log.w(TAG, "history prune failed: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}

@Preview(showBackground = true)
@Composable
fun ParKingAppPreview() {
    ParKingTheme {
        ParKingApp()
    }
}