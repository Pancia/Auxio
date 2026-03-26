/*
 * Copyright (c) 2024 Auxio Project
 * ControlsWidgetComponent.kt is part of Auxio.
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

import android.content.Context
import javax.inject.Inject
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.Progression
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A component that manages the controls-only widget state. This is kept separate from the
 * [ControlsWidgetProvider] itself to prevent possible memory leaks.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class ControlsWidgetComponent
private constructor(
    private val context: Context,
    private val playbackManager: PlaybackStateManager,
    private val uiSettings: UISettings,
) : PlaybackStateManager.Listener, UISettings.Listener {
    class Factory
    @Inject
    constructor(
        private val playbackManager: PlaybackStateManager,
        private val uiSettings: UISettings,
    ) {
        fun create(context: Context) =
            ControlsWidgetComponent(context, playbackManager, uiSettings)
    }

    private val controlsWidgetProvider = ControlsWidgetProvider()

    fun attach() {
        playbackManager.addListener(this)
        uiSettings.registerListener(this)
    }

    /** Update [ControlsWidgetProvider] with the current playback state. */
    fun update() {
        val song = playbackManager.currentSong
        if (song == null) {
            L.d("No song, resetting controls widget")
            controlsWidgetProvider.update(context, uiSettings, null)
            return
        }

        val isPlaying = playbackManager.progression.isPlaying
        val repeatMode = playbackManager.repeatMode
        val isShuffled = playbackManager.isShuffled

        L.d("Updating controls widget with new playback state")
        val state =
            ControlsWidgetProvider.ControlsState(song, isPlaying, repeatMode, isShuffled)
        controlsWidgetProvider.update(context, uiSettings, state)
    }

    /** Release this instance, preventing any further events from updating the widget instances. */
    fun release() {
        playbackManager.removeListener(this)
        uiSettings.unregisterListener(this)
        controlsWidgetProvider.reset(context, uiSettings)
    }

    // --- CALLBACKS ---

    override fun onIndexMoved(index: Int) = update()

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) {
        if (change.type == QueueChange.Type.SONG) {
            update()
        }
    }

    override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) = update()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = update()

    override fun onProgressionChanged(progression: Progression) = update()

    override fun onRepeatModeChanged(repeatMode: RepeatMode) = update()

    override fun onRoundModeChanged() = update()
}
