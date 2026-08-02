package com.king.zxing.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Action-style Quick Settings tile. It does not toggle state; tapping opens
 * [MultiFormatScanActivity] (continuous multi-format scanning) directly.
 */
class QuickScanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.quick_scan_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.quick_scan_tile_subtitle)
            }
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // Camera should not be launched over a locked keyguard. The system handles
        // authentication first, then invokes the standard TileService launch API.
        unlockAndRun { launchContinuousScan() }
    }

    private fun launchContinuousScan() {
        val intent = Intent(this, MultiFormatScanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_SCAN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val REQUEST_SCAN = 1001
    }
}
