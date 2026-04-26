plugins {
    // Apply false so each subproject can opt into the plugins it needs without
    // re-resolving them on every build.
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
}
