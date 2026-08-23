package com.lazysimulation.freecasts

import android.app.Application

/**
 * Lightweight application used by Robolectric unit tests.
 * Avoids initializing Koin and WorkManager for DAO tests.
 */
class TestApplication : Application()
