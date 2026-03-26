/*
 * Copyright (c) 2024 Auxio Project
 * ControlsWidgetProvider.kt is part of Auxio.
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
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.auxio.ui.UISettingsImpl
import org.oxycblt.auxio.util.newBroadcastPendingIntent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * The [AppWidgetProvider] for the "Controls Only" widget. This widget shows the current song
 * title/artist alongside playback control buttons, without album art.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class ControlsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        requestUpdate(context)
        // Revert to the default layout for now until we get a response from
        // ControlsWidgetComponent.
        reset(context, UISettingsImpl(context))
    }

    /**
     * Update the currently shown layout based on the given [ControlsState].
     *
     * @param context [Context] required to update the widget layout.
     * @param uiSettings [UISettings] to obtain round mode configuration.
     * @param state [ControlsState] to show, or null if no playback is going on.
     */
    fun update(context: Context, uiSettings: UISettings, state: ControlsState?) {
        if (state == null) {
            L.d("No state provided, returning to default")
            reset(context, uiSettings)
            return
        }

        val views = newRemoteViews(context, R.layout.widget_controls)
        views.setupBackground(uiSettings)
        views.setupSongInfo(context, state)
        views.setupFullControls(context, state)

        val component = ComponentName(context, this::class.java)
        try {
            AppWidgetManager.getInstance(context).updateAppWidget(component, views)
            L.d("Successfully updated controls widget RemoteViews layout")
        } catch (e: Exception) {
            L.w("Unable to update controls widget: $e")
            reset(context, uiSettings)
        }
    }

    /**
     * Revert to the default layout that displays "No music playing".
     *
     * @param context [Context] required to update the widget layout.
     * @param uiSettings [UISettings] to obtain round mode configuration.
     */
    fun reset(context: Context, uiSettings: UISettings) {
        L.d("Using default controls widget layout")
        val layout = newRemoteViews(context, R.layout.widget_controls_default)
        layout.setupBackground(uiSettings)
        AppWidgetManager.getInstance(context)
            .updateAppWidget(ComponentName(context, this::class.java), layout)
    }

    // --- INTERNAL METHODS ---

    /**
     * Request an update from [ControlsWidgetComponent].
     *
     * @param context [Context] required to send update request broadcast.
     */
    private fun requestUpdate(context: Context) {
        L.d("Sending controls widget update intent to PlaybackService")
        val intent =
            Intent(ACTION_CONTROLS_WIDGET_UPDATE).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        context.sendBroadcast(intent)
    }

    // --- LAYOUT SETUP ---

    private fun RemoteViews.setupBackground(uiSettings: UISettings): RemoteViews {
        val background =
            if (useRoundedRemoteViews(uiSettings)) {
                R.drawable.ui_widget_bg_round
            } else {
                R.drawable.ui_widget_bg_sharp
            }
        setBackgroundResource(android.R.id.background, background)
        return this
    }

    private fun RemoteViews.setupSongInfo(context: Context, state: ControlsState): RemoteViews {
        setTextViewText(R.id.widget_song, state.song.name.resolve(context))
        setTextViewText(R.id.widget_artist, state.song.artists.resolveNames(context))
        return this
    }

    private fun RemoteViews.setupBasicControls(
        context: Context,
        state: ControlsState,
    ): RemoteViews {
        setOnClickPendingIntent(
            R.id.widget_play_pause,
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_PLAY_PAUSE),
        )

        val icon: Int
        val background: Int
        if (state.isPlaying) {
            icon = R.drawable.ic_pause_24
            background = R.drawable.ui_remote_fab_container_playing
        } else {
            icon = R.drawable.ic_play_24
            background = R.drawable.ui_remote_fab_container_paused
        }

        setImageViewResource(R.id.widget_play_pause, icon)
        setBackgroundResource(R.id.widget_play_pause, background)

        return this
    }

    private fun RemoteViews.setupTimelineControls(
        context: Context,
        state: ControlsState,
    ): RemoteViews {
        setupBasicControls(context, state)
        setLayoutDirection(R.id.widget_controls, View.LAYOUT_DIRECTION_LTR)
        setOnClickPendingIntent(
            R.id.widget_skip_prev,
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_SKIP_PREV),
        )
        setOnClickPendingIntent(
            R.id.widget_skip_next,
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_SKIP_NEXT),
        )
        return this
    }

    private fun RemoteViews.setupFullControls(context: Context, state: ControlsState): RemoteViews {
        setupTimelineControls(context, state)

        setOnClickPendingIntent(
            R.id.widget_repeat,
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_INC_REPEAT_MODE),
        )
        setOnClickPendingIntent(
            R.id.widget_shuffle,
            context.newBroadcastPendingIntent(PlaybackActions.ACTION_INVERT_SHUFFLE),
        )

        val repeatRes =
            when (state.repeatMode) {
                RepeatMode.NONE -> R.drawable.ic_repeat_off_24
                RepeatMode.ALL -> R.drawable.ic_repeat_on_24
                RepeatMode.TRACK -> R.drawable.ic_repeat_one_24
            }
        setImageViewResource(R.id.widget_repeat, repeatRes)

        val shuffleRes =
            when {
                state.isShuffled -> R.drawable.ic_shuffle_on_24
                else -> R.drawable.ic_shuffle_off_24
            }
        setImageViewResource(R.id.widget_shuffle, shuffleRes)

        return this
    }

    private fun useRoundedRemoteViews(uiSettings: UISettings) =
        uiSettings.roundMode || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * A condensed form of the playback state for the controls-only widget.
     *
     * @param song The currently playing [Song].
     * @param isPlaying Whether playback is currently active.
     * @param repeatMode The current [RepeatMode].
     * @param isShuffled Whether shuffle is enabled.
     */
    data class ControlsState(
        val song: Song,
        val isPlaying: Boolean,
        val repeatMode: RepeatMode,
        val isShuffled: Boolean,
    )

    companion object {
        /**
         * Broadcast when [ControlsWidgetProvider] desires to update its widget with new
         * information. Responsible background tasks should intercept this and relay the message to
         * [ControlsWidgetComponent].
         */
        const val ACTION_CONTROLS_WIDGET_UPDATE =
            BuildConfig.APPLICATION_ID + ".action.CONTROLS_WIDGET_UPDATE"
    }
}
