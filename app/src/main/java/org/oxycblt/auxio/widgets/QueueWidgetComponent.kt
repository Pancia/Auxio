/*
 * Copyright (c) 2024 Auxio Project
 * QueueWidgetComponent.kt is part of Auxio.
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
import org.oxycblt.auxio.playback.state.QueueChange
import org.oxycblt.auxio.ui.UISettings
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A component that manages the "Queue List" widget state. This is kept separate from the
 * [QueueWidgetProvider] itself to prevent possible memory leaks.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class QueueWidgetComponent
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
        fun create(context: Context) = QueueWidgetComponent(context, playbackManager, uiSettings)
    }

    private val queueWidgetProvider = QueueWidgetProvider()

    fun attach() {
        playbackManager.addListener(this)
        uiSettings.registerListener(this)
    }

    /** Update [QueueWidgetProvider] with the current queue state. */
    fun update() {
        val queue = playbackManager.queue
        val index = playbackManager.index
        if (queue.isEmpty() || playbackManager.currentSong == null) {
            L.d("No queue, resetting queue widget")
            queueWidgetProvider.update(context, uiSettings, null)
            return
        }

        val upcomingStart = index + 1
        val upcomingEnd = (index + 6).coerceAtMost(queue.size)
        if (upcomingStart >= queue.size) {
            L.d("No upcoming songs, resetting queue widget")
            queueWidgetProvider.update(context, uiSettings, null)
            return
        }

        val upcomingSongs = queue.subList(upcomingStart, upcomingEnd).toList()
        L.d("Updating queue widget with ${upcomingSongs.size} upcoming songs")
        queueWidgetProvider.update(context, uiSettings, QueueState(upcomingSongs, upcomingStart))
    }

    /** Release this instance, preventing any further events from updating the widget instances. */
    fun release() {
        playbackManager.removeListener(this)
        uiSettings.unregisterListener(this)
        queueWidgetProvider.reset(context, uiSettings)
    }

    // --- CALLBACKS ---

    override fun onIndexMoved(index: Int) = update()

    override fun onQueueChanged(queue: List<Song>, index: Int, change: QueueChange) = update()

    override fun onQueueReordered(queue: List<Song>, index: Int, isShuffled: Boolean) = update()

    override fun onNewPlayback(
        parent: MusicParent?,
        queue: List<Song>,
        index: Int,
        isShuffled: Boolean,
    ) = update()

    // Respond to settings changes that will affect the widget
    override fun onRoundModeChanged() = update()

    /**
     * A condensed form of the queue state for use in AppWidgets.
     *
     * @param songs The list of upcoming [Song]s to display (up to 5).
     */
    /**
     * @param songs The list of upcoming [Song]s to display (up to 5).
     * @param startIndex The absolute queue index of the first song in [songs].
     */
    data class QueueState(val songs: List<Song>, val startIndex: Int)
}
