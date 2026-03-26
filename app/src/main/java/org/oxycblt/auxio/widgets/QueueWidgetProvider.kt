/*
 * Copyright (c) 2024 Auxio Project
 * QueueWidgetProvider.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.service.PlaybackActions
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.auxio.ui.UISettingsImpl
import timber.log.Timber as L

/**
 * The [AppWidgetProvider] for the "Queue List" widget. This widget shows up to 5 upcoming songs in
 * the playback queue.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class QueueWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        requestUpdate(context)
        // Revert to the default layout for now until we get a response from QueueWidgetComponent.
        // If we don't, then we will stick with the default widget layout.
        reset(context, UISettingsImpl(context))
    }

    /**
     * Update the currently shown layout based on the given [QueueWidgetComponent.QueueState].
     *
     * @param context [Context] required to update the widget layout.
     * @param uiSettings [UISettings] to obtain round mode configuration.
     * @param state [QueueWidgetComponent.QueueState] to show, or null if no playback is going on.
     */
    fun update(context: Context, uiSettings: UISettings, state: QueueWidgetComponent.QueueState?) {
        if (state == null || state.songs.isEmpty()) {
            L.d("No queue state provided, returning to default")
            reset(context, uiSettings)
            return
        }

        L.d("Updating queue widget with ${state.songs.size} upcoming songs")
        val views = newRemoteViews(context, R.layout.widget_queue_list)
        setupBackground(views, uiSettings)

        val rowIds =
            intArrayOf(
                R.id.queue_row_1,
                R.id.queue_row_2,
                R.id.queue_row_3,
                R.id.queue_row_4,
                R.id.queue_row_5,
            )
        val songIds =
            intArrayOf(
                R.id.queue_song_1,
                R.id.queue_song_2,
                R.id.queue_song_3,
                R.id.queue_song_4,
                R.id.queue_song_5,
            )
        val artistIds =
            intArrayOf(
                R.id.queue_artist_1,
                R.id.queue_artist_2,
                R.id.queue_artist_3,
                R.id.queue_artist_4,
                R.id.queue_artist_5,
            )

        for (i in 0 until 5) {
            if (i < state.songs.size) {
                val song = state.songs[i]
                views.setViewVisibility(rowIds[i], View.VISIBLE)
                views.setTextViewText(songIds[i], song.name.resolve(context))
                views.setTextViewText(artistIds[i], song.artists.resolveNames(context))
                // Set up tap-to-jump: each row jumps to its absolute queue index
                val gotoIntent =
                    Intent(PlaybackActions.ACTION_GOTO_QUEUE_INDEX)
                        .putExtra(PlaybackActions.EXTRA_QUEUE_INDEX, state.startIndex + i)
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        state.startIndex + i,
                        gotoIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                views.setOnClickPendingIntent(rowIds[i], pendingIntent)
            } else {
                views.setViewVisibility(rowIds[i], View.GONE)
            }
        }

        AppWidgetManager.getInstance(context)
            .updateAppWidget(ComponentName(context, this::class.java), views)
    }

    /**
     * Revert to the default layout that displays "No upcoming songs".
     *
     * @param context [Context] required to update the widget layout.
     * @param uiSettings [UISettings] to obtain round mode configuration.
     */
    fun reset(context: Context, uiSettings: UISettings) {
        L.d("Using default queue widget layout")
        val layout = newRemoteViews(context, R.layout.widget_queue_default)
        setupBackground(layout, uiSettings)
        AppWidgetManager.getInstance(context)
            .updateAppWidget(ComponentName(context, this::class.java), layout)
    }

    // --- INTERNAL METHODS ---

    /**
     * Request an update from [QueueWidgetComponent].
     *
     * @param context [Context] required to send update request broadcast.
     */
    private fun requestUpdate(context: Context) {
        L.d("Sending queue widget update intent to PlaybackService")
        val intent =
            Intent(ACTION_QUEUE_WIDGET_UPDATE).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        context.sendBroadcast(intent)
    }

    private fun setupBackground(views: RemoteViews, uiSettings: UISettings) {
        val background =
            if (useRoundedRemoteViews(uiSettings)) {
                R.drawable.ui_widget_bg_round
            } else {
                R.drawable.ui_widget_bg_sharp
            }
        views.setBackgroundResource(android.R.id.background, background)
    }

    private fun useRoundedRemoteViews(uiSettings: UISettings) =
        uiSettings.roundMode || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    companion object {
        /**
         * Broadcast when [QueueWidgetProvider] desires to update its widget with new information.
         * Responsible background tasks should intercept this and relay the message to
         * [QueueWidgetComponent].
         */
        const val ACTION_QUEUE_WIDGET_UPDATE =
            BuildConfig.APPLICATION_ID + ".action.QUEUE_WIDGET_UPDATE"
    }
}
