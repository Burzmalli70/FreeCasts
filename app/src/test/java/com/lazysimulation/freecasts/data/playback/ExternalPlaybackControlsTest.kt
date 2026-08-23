package com.lazysimulation.freecasts.data.playback

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExternalPlaybackControlsTest {

    private fun defaultCommandsWithTrackAndSeek(): Player.Commands {
        return Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .build()
    }

    @Test
    fun filterPlayerCommandsForSkipIntervals_hidesTrackNavigation() {
        val filtered = ExternalPlaybackControls.filterPlayerCommandsForSkipIntervals(
            defaultCommandsWithTrackAndSeek()
        )

        assertFalse(filtered.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(filtered.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(filtered.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(filtered.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(filtered.contains(Player.COMMAND_SEEK_BACK))
        assertTrue(filtered.contains(Player.COMMAND_SEEK_FORWARD))
        assertTrue(filtered.contains(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun playerCommandsForExternalController_whenSkipDisabled_keepsTrackNavigation() {
        val defaults = defaultCommandsWithTrackAndSeek()
        val filtered = ExternalPlaybackControls.playerCommandsForExternalController(
            defaultCommands = defaults,
            useSkipIntervals = false,
        )

        assertEquals(defaults, filtered)
    }

    @Test
    fun mediaButtonPreferences_whenSkipEnabled_useCustomSessionCommands() {
        val buttons = ExternalPlaybackControls.mediaButtonPreferences(
            useSkipIntervals = true,
            skipBackwardDurationMs = 30_000L,
            skipForwardDurationMs = 30_000L,
        )

        assertEquals(2, buttons.size)
        assertEquals(
            ExternalPlaybackControls.CUSTOM_ACTION_SEEK_BACK,
            buttons[0].sessionCommand?.customAction
        )
        assertEquals(
            ExternalPlaybackControls.CUSTOM_ACTION_SEEK_FORWARD,
            buttons[1].sessionCommand?.customAction
        )
        assertEquals(CommandButton.ICON_SKIP_BACK_30, buttons[0].icon)
        assertEquals(CommandButton.ICON_SKIP_FORWARD_30, buttons[1].icon)
        assertTrue(buttons[0].iconResId != 0)
        assertTrue(buttons[1].iconResId != 0)
        assertNotNull(buttons[0].sessionCommand)
        assertNotNull(buttons[1].sessionCommand)
    }

    @Test
    fun mediaButtonPreferences_whenSkipDisabled_usesTrackNavigationPlayerCommands() {
        val buttons = ExternalPlaybackControls.mediaButtonPreferences(
            useSkipIntervals = false,
            skipBackwardDurationMs = 30_000L,
            skipForwardDurationMs = 30_000L,
        )

        assertEquals(2, buttons.size)
        assertEquals(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, buttons[0].playerCommand)
        assertEquals(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, buttons[1].playerCommand)
    }

    @Test
    fun skipIcons_useGenericSkipWhenIntervalHasNoDedicatedIcon() {
        assertEquals(
            CommandButton.ICON_SKIP_BACK,
            ExternalPlaybackControls.skipBackIconForInterval(45)
        )
        assertEquals(
            CommandButton.ICON_SKIP_FORWARD,
            ExternalPlaybackControls.skipForwardIconForInterval(60)
        )
    }
}
