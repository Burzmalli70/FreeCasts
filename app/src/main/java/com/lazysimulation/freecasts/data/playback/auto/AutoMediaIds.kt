package com.lazysimulation.freecasts.data.playback.auto

object AutoMediaIds {
    const val ROOT = "node_root"
    const val SUBSCRIPTIONS = "node_subscriptions"
    const val DOWNLOADS = "node_downloads"
    const val PLAYLISTS = "node_playlists"
    const val PLAY_RANDOM_FAVORITE = "action_play_random_favorite"

    private const val PODCAST_PREFIX = "podcast:"
    private const val PLAYLIST_PREFIX = "playlist:"

    fun podcast(podcastId: Long): String = "$PODCAST_PREFIX$podcastId"

    fun playlist(playlistId: Long): String = "$PLAYLIST_PREFIX$playlistId"

    fun parsePodcastId(mediaId: String): Long? =
        if (mediaId.startsWith(PODCAST_PREFIX)) {
            mediaId.removePrefix(PODCAST_PREFIX).toLongOrNull()
        } else {
            null
        }

    fun parsePlaylistId(mediaId: String): Long? =
        if (mediaId.startsWith(PLAYLIST_PREFIX)) {
            mediaId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
        } else {
            null
        }

    fun parseEpisodeId(mediaId: String): Long? = mediaId.toLongOrNull()
}
