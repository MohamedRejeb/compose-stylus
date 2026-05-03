import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        publishLibraryVariants("release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.stylus)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Intermediate source set shared by all Skia/Skiko-backed targets
        // (Desktop JVM, iOS, Web wasmJs). Hosts the pure-Compose ink renderer
        // and the org.jetbrains.skia drawVertices tessellation pipeline that
        // is only meaningful on these targets — Android uses Jetpack Ink and
        // does not need any of this code, so keeping it out of commonMain
        // keeps the Android compilation lean and free of Skia symbols.
        val skikoMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(skikoMain)
        }
        iosMain {
            dependsOn(skikoMain)
        }
        wasmJsMain {
            dependsOn(skikoMain)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            // Pulls the skiko native runtime onto the test classpath so
            // tests that touch real Skia (ImageBitmap, drawVertices) can
            // load `libskiko-<host>` from disk. compose.desktop.common
            // is a multiplatform-metadata coordinate without the native
            // .dylib / .so / .dll attached.
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.ink.authoring.compose)
            implementation(libs.androidx.ink.brush)
            implementation(libs.androidx.ink.rendering)
            implementation(libs.androidx.ink.strokes)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.compose.ui.test.junit4.android)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
        }
    }
}

android {
    namespace = "com.mohamedrejeb.stylus.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "stylus-compose",
        version = project.version.toString(),
    )

    pom {
        name.set("stylus-compose")
        description.set("Compose Multiplatform integration for the stylus library — Modifier.penInput that taps native pen events on Desktop and Compose pointer events on Android, iOS, and Web.")
        url.set("https://github.com/MohamedRejeb/compose-stylus")
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("MohamedRejeb")
                name.set("Mohamed Rejeb")
                url.set("https://github.com/MohamedRejeb")
            }
        }
        scm {
            url.set("https://github.com/MohamedRejeb/compose-stylus")
            connection.set("scm:git:git://github.com/MohamedRejeb/compose-stylus.git")
            developerConnection.set("scm:git:ssh://git@github.com/MohamedRejeb/compose-stylus.git")
        }
    }
}
