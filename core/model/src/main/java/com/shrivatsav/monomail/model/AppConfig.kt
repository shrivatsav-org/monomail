package com.shrivatsav.monomail.model

object AppConfig {
    // TODO: Ideally inject this via DI or load from a properties file.
    // For now, this replaces the BuildConfig.IS_GITHUB_BUILD hardcoded value.
    const val IS_GITHUB_BUILD = false
    const val VERSION_NAME = "1.0.0"
    const val DEBUG = true
}
