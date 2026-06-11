package dev.josephwilliams.freecasts.data.download

interface FavoriteEpisodeDownloadHandler {
    suspend fun markFavoriteDownloadsPendingAfterImport()

    suspend fun resumeFavoriteDownloadsIfNeeded(
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): Int

    suspend fun enqueueDownloadsForAllFavorites(
        onProgress: (current: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): Int

    suspend fun enqueueFavoriteDownloadIfNeeded(episodeId: Long): Boolean

    suspend fun clearPendingIfAllFavoritesDownloaded()
}
