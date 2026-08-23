package com.lazysimulation.freecasts.data.download

object NoOpFavoriteEpisodeDownloadHandler : FavoriteEpisodeDownloadHandler {
    override suspend fun markFavoriteDownloadsPendingAfterImport() = Unit

    override suspend fun resumeFavoriteDownloadsIfNeeded(
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Int = 0

    override suspend fun enqueueDownloadsForAllFavorites(
        onProgress: (current: Int, total: Int, label: String) -> Unit
    ): Int = 0

    override suspend fun enqueueFavoriteDownloadIfNeeded(episodeId: Long): Boolean = false

    override suspend fun clearPendingIfAllFavoritesDownloaded() = Unit
}
