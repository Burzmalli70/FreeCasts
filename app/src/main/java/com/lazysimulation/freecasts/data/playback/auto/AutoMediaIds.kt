package com.lazysimulation.freecasts.data.playback.auto

object AutoMediaIds {
    const val ROOT = "node_root"
    const val SUBSCRIPTIONS = "node_subscriptions"
    const val DOWNLOADS = "node_downloads"
    const val PLAYLISTS = "node_playlists"

    private const val PODCAST_PREFIX = "podcast:"
    private const val PLAYLIST_PREFIX = "playlist:"

    fun podcast(podcastId: Long): String = "$PODCAST_PREFIX$podcastId"

    fun playlist(playlistId: Long): String = "$PLAYLIST_PREFIX$playlistId"

    fun parsePodcastId(mediaId: String): Long? =
        mediaId.removePrefix(PODCAST_PREFIX).toLongOrNull()

    fun parsePlaylistId(mediaId: String): Long? =
        mediaId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()

    fun parseEpisodeId(mediaId: String): Long? = mediaId.toLongOrNull()
}
