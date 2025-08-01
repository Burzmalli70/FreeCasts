package dev.josephwilliams.freecasts.repositories

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.josephwilliams.freecasts.model.PodcastDatabase
import dev.josephwilliams.freecasts.model.entities.Episode
import dev.josephwilliams.freecasts.model.entities.Playlist
import dev.josephwilliams.freecasts.model.entities.Podcast
import dev.josephwilliams.freecasts.setupItunesApi
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.inject

@RunWith(AndroidJUnit4::class)
class PodcastRepositoryTests {
    private val testDatabaseModule = module {
        single {
            Room.inMemoryDatabaseBuilder(get(), PodcastDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
        single { get<PodcastDatabase>().podcastDao() }
        single { get<PodcastDatabase>().episodeDao() }
        single { get<PodcastDatabase>().playlistDao() }
    }

    private val testApiModule = module {
        single { setupItunesApi() }
    }

    private val testRepositoryModule = module {
        single { PodcastRepository(get(), get(), get(), get()) }
    }

    private val testAppModules = listOf(testApiModule, testDatabaseModule, testRepositoryModule)

    @Before
    fun setup() {
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(testAppModules)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testAddPodcast() = runTest {
        val repository: PodcastRepository by inject(PodcastRepository::class.java)

        val podcast = Podcast(
            title = "Test Podcast",
            author = "Test Author",
            description = "Test Description",
            smallImageUrl = "https://example.com/test.jpg",
            feedUrl = "https://feeds.example.com/test"
        )

        val id = repository.addPodcast(podcast)

        assertTrue(id > 0)
    }

    @Test
    fun testPlaylistCreation() = runBlocking {
        val repository: PodcastRepository by inject(PodcastRepository::class.java)

        var playlist = Playlist(name = "Test Playlist")
        val playlistId = repository.createPlaylist(playlist)

        assertTrue(playlistId > 0)

        playlist = repository.getPlaylistById(playlistId) ?: error("Playlist not found")

        var episode = Episode(
            podcastId = null,
            title = "Test Episode",
            description = "Test Description",
            audioUrl = "https://example.com/test.mp3",
            duration = 300,
        )

        val episodeId = repository.addEpisode(episode)

        assertTrue(episodeId > 0)

        episode = repository.getEpisodeById(episodeId) ?: error("Episode not found")

        repository.addEpisodeToPlaylist(playlistId, episodeId, 0)

        val playlistWithEpisodes = repository.getPlaylistWithEpisodes(playlistId.toInt())

        assertTrue(playlistWithEpisodes.episodes.contains(episode))
    }
}