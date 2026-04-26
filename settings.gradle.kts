rootProject.name = "compose-stylus"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()

        // Kotlin/Wasm and Kotlin/JS targets bootstrap their toolchain by
        // downloading Node.js + Yarn from these Ivy-style endpoints. They have
        // to be declared at settings level (not project level) so they pass
        // FAIL_ON_PROJECT_REPOS / PREFER_SETTINGS validation.
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist/") {
                    name = "Node Distributions at https://nodejs.org/dist"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
                    metadataSources { artifact() }
                    content { includeModule("org.nodejs", "node") }
                }
            }
            filter { includeGroup("org.nodejs") }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn Distributions at https://github.com/yarnpkg/yarn/releases/download"
                    patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
                    metadataSources { artifact() }
                    content { includeModule("com.yarnpkg", "yarn") }
                }
            }
            filter { includeGroup("com.yarnpkg") }
        }
    }
}

include(":stylus")
include(":stylus-jni")
include(":stylus-compose")

include(":stylus-demo-jvm")
include(":stylus-demo-android")
include(":stylus-demo-web")

project(":stylus-demo-jvm").projectDir = file("stylus-demo/stylus-demo-jvm")
project(":stylus-demo-android").projectDir = file("stylus-demo/stylus-demo-android")
project(":stylus-demo-web").projectDir = file("stylus-demo/stylus-demo-web")
