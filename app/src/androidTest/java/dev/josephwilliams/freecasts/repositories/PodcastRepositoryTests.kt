package dev.josephwilliams.freecasts.repositories

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.josephwilliams.freecasts.model.PodcastDatabase
import dev.josephwilliams.freecasts.model.entities.Podcast
import junit.framework.TestCase.assertTrue
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

    private val testRepositoryModule = module {
        single { PodcastRepository(get(), get(), get()) }
    }

    private val testAppModules = listOf(testDatabaseModule, testRepositoryModule)

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

        // Test repository functions
        val podcast = Podcast(
            title = "Test Podcast",
            author = "Test Author",
            description = "Test Description",
            imageUrl = "https://example.com/test.jpg",
            feedUrl = "https://feeds.example.com/test"
        )

        val id = repository.addPodcast(podcast)

        assertTrue(id > 0)
    }
}