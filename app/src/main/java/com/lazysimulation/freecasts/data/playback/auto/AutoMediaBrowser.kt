package com.lazysimulation.freecasts.data.playback.auto

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lazysimulation.freecasts.R
import com.lazysimulation.freecasts.data.local.dao.DownloadDao
import com.lazysimulation.freecasts.data.local.dao.EpisodeDao
import com.lazysimulation.freecasts.data.local.dao.PlaylistDao
import com.lazysimulation.freecasts.data.local.dao.PodcastDao
import com.lazysimulation.freecasts.data.local.entity.DownloadStatus
import com.lazysimulation.freecasts.data.local.entity.Episode
import com.lazysimulation.freecasts.data.local.entity.Playlist
import com.lazysimulation.freecasts.data.local.entity.Podcast
import com.lazysimulation.freecasts.data.playback.toMediaItem

class AutoMediaBrowser(
    private val context: Context,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadDao: DownloadDao,
    private val playlistDao: PlaylistDao,
) {

    fun createRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(AutoMediaIds.ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.app_name))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    suspend fun getRootChildren(): List<MediaItem> = listOf(
        createBrowsableFolder(
            AutoMediaIds.SUBSCRIPTIONS,
            context.getString(R.string.auto_browse_subscriptions),
            R.drawable.ic_subscriptions
        ),
        createBrowsableFolder(
            AutoMediaIds.DOWNLOADS,
            context.getString(R.string.auto_browse_downloads),
            R.drawable.ic_download
        ),
        createBrowsableFolder(
            AutoMediaIds.PLAYLISTS,
            context.getString(R.string.auto_browse_playlists),
            R.drawable.ic_playlists
        ),
    )

    suspend fun getChildren(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val limit = pageSize.coerceIn(1, AUTO_PAGE_SIZE)
        val offset = page * limit

        return when (parentId) {
            AutoMediaIds.ROOT -> getRootChildren()
            AutoMediaIds.SUBSCRIPTIONS -> {
                podcastDao.getSubscribed()
                    .drop(offset)
                    .take(limit)
                    .map { it.toBrowsableMediaItem() }
            }
            AutoMediaIds.DOWNLOADS -> loadDownloadedEpisodes(offset, limit)
            AutoMediaIds.PLAYLISTS -> {
                playlistDao.getAllOrderedByName()
                    .drop(offset)
                    .take(limit)
                    .map { it.toBrowsableMediaItem() }
            }
            else -> {
                AutoMediaIds.parsePodcastId(parentId)?.let { podcastId ->
                    return episodeDao.getAllByPodcastId(podcastId)
                        .drop(offset)
                        .take(limit)
                        .map { episode -> episode.toPlayableMediaItem() }
                }
                AutoMediaIds.parsePlaylistId(parentId)?.let { playlistId ->
                    val playlist = playlistDao.getPlaylistWithEpisodes(playlistId) ?: return emptyList()
                    return playlist.episodes
                        .drop(offset)
                        .take(limit)
                        .map { episode -> episode.toPlayableMediaItem() }
                }
                emptyList()
            }
        }
    }

    suspend fun getItem(mediaId: String): MediaItem? {
        AutoMediaIds.parsePodcastId(mediaId)?.let { podcastId ->
            return podcastDao.getById(podcastId)?.toBrowsableMediaItem()
        }
        AutoMediaIds.parsePlaylistId(mediaId)?.let { playlistId ->
            return playlistDao.getById(playlistId)?.toBrowsableMediaItem()
        }
        if (mediaId == AutoMediaIds.ROOT) {
            return createRootItem()
        }
        getRootChildren().firstOrNull { it.mediaId == mediaId }?.let { return it }
        AutoMediaIds.parseEpisodeId(mediaId)?.let { episodeId ->
            return episodeDao.getById(episodeId)?.toPlayableMediaItem()
        }
        return null
    }

    suspend fun search(query: String, page: Int, pageSize: Int): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val limit = pageSize.coerceIn(1, AUTO_PAGE_SIZE)
        val offset = page * limit
        return episodeDao.searchForAuto(query.trim(), limit + offset)
            .drop(offset)
            .take(limit)
            .map { episode -> episode.toPlayableMediaItem() }
    }

    private suspend fun loadDownloadedEpisodes(offset: Int, limit: Int): List<MediaItem> {
        val downloads = downloadDao.getCompleted(limit + offset)
        return downloads
            .drop(offset)
            .take(limit)
            .mapNotNull { download ->
                episodeDao.getById(download.episodeId)?.toPlayableMediaItem()
            }
    }

    private fun Podcast.toBrowsableMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(AutoMediaIds.podcast(id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(author)
                    .setArtworkUri(artworkUrl?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun Playlist.toBrowsableMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(AutoMediaIds.playlist(id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(drawableUri(R.drawable.ic_playlists))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private suspend fun Episode.toPlayableMediaItem(): MediaItem {
        val podcastTitle = podcastDao.getById(podcastId)?.title.orEmpty()
        val download = downloadDao.getByEpisodeId(id)
        val localFilePath = if (download?.status == DownloadStatus.COMPLETED) {
            download.localFilePath
        } else {
            null
        }
        return toMediaItem(
            podcastTitle = podcastTitle,
            localFilePath = localFilePath
        )
    }

    private fun createBrowsableFolder(
        id: String,
        title: String,
        @DrawableRes iconRes: Int,
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(drawableUri(iconRes))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    private fun drawableUri(@DrawableRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    companion object {
        const val AUTO_PAGE_SIZE = 50
        const val LEGACY_BROWSER_PACKAGE = "android.media.browse.MediaBrowserService"
    }
}
