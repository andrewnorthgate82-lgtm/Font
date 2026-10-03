package com.promptsaz.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt generates the dependency graph from here.
 */
@HiltAndroidApp
class PromptSazApplication : Application()
