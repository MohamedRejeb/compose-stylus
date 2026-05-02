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
        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation(libs.kotlinx.coroutines.swing)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.mohamedrejeb.stylus.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
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
