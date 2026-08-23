package com.lazysimulation.freecasts.data.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

/**
 * Builds Android Auto / notification / lock-screen skip controls.
 *
 * Android Auto reads legacy [androidx.media3.session.legacy.PlaybackStateCompat] actions from the
 * platform session. Track prev/next buttons appear whenever
 * [Player.COMMAND_SEEK_TO_PREVIOUS] / [Player.COMMAND_SEEK_TO_NEXT] are advertised. Auto only fills
 * those reserved slots with skip-interval controls when:
 * 1. Track-skip commands are removed from the **player's** available commands (so they leave
 *    PlaybackState), and
 * 2. Skip buttons are advertised as **custom session commands** via [CommandButton.setSessionCommand]
 *    (player-command buttons are not mirrored into Auto custom actions).
 */
@OptIn(UnstableApi::class)
object ExternalPlaybackControls {
    const val CUSTOM_ACTION_SEEK_BACK = "freecasts.SEEK_BACK"
    const val CUSTOM_ACTION_SEEK_FORWARD = "freecasts.SEEK_FORWARD"

    fun seekBackCommand(): SessionCommand =
        SessionCommand(CUSTOM_ACTION_SEEK_BACK, Bundle.EMPTY)

    fun seekForwardCommand(): SessionCommand =
        SessionCommand(CUSTOM_ACTION_SEEK_FORWARD, Bundle.EMPTY)

    fun mediaButtonPreferences(
        useSkipIntervals: Boolean,
        skipBackwardDurationMs: Long,
        skipForwardDurationMs: Long,
    ): List<CommandButton> {
        return if (useSkipIntervals) {
            val backSeconds = (skipBackwardDurationMs / 1000L).toInt()
            val forwardSeconds = (skipForwardDurationMs / 1000L).toInt()
            listOf(
                CommandButton.Builder(skipBackIconForInterval(backSeconds))
                    .setSessionCommand(seekBackCommand())
                    .setDisplayName("−${backSeconds}s")
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(skipForwardIconForInterval(forwardSeconds))
                    .setSessionCommand(seekForwardCommand())
                    .setDisplayName("+${forwardSeconds}s")
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build(),
            )
        } else {
            listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build(),
            )
        }
    }

    /**
     * Removes track prev/next from the player command set so Auto stops advertising
     * ACTION_SKIP_TO_PREVIOUS / ACTION_SKIP_TO_NEXT.
     */
    fun filterPlayerCommandsForSkipIntervals(defaultCommands: Player.Commands): Player.Commands {
        return defaultCommands.buildUpon()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()
    }

    /** @deprecated Use [filterPlayerCommandsForSkipIntervals]; kept for existing call sites/tests. */
    fun playerCommandsForExternalController(
        defaultCommands: Player.Commands,
        useSkipIntervals: Boolean,
    ): Player.Commands {
        if (!useSkipIntervals) return defaultCommands
        return filterPlayerCommandsForSkipIntervals(defaultCommands)
    }

    fun skipBackIconForInterval(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    fun skipForwardIconForInterval(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }
}
