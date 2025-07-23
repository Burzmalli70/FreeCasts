package dev.josephwilliams.freecasts

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dev.josephwilliams.freecasts.fixtures.TestApp

class CustomTestRunner: AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application? {
        return super.newApplication(cl, TestApp::class.java.name, context)
    }
}