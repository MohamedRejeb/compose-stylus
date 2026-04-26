import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    compilerOptions {
        // expect/actual classes are still flagged as Beta in 2.2.x; we use them
        // intentionally to keep the surface API symmetric across targets.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }

    // The JVM source set bundles the native JNI library produced by :stylus-jni
    // as a runtime resource so NativeLoader can extract + System.load() it.
    sourceSets.named("jvmMain") {
        resources.srcDir(layout.buildDirectory.dir("native-resources"))
    }
}

// Wire stylus-jni → jvm processResources so the native lib lands in the jar.
//
// Pass `-PskipNativeBuild` to skip the native compile step — used by CI's
// `package-jar` job after it's downloaded prebuilt artifacts from the per-OS
// `build-native` matrix. Without this, the package job would try to rebuild
// libstylus.dylib/.so/.dll for whatever single OS it happens to run on, and
// would fail or partially overwrite the artifacts we already have.
val copyNativeLib by tasks.registering(Copy::class) {
    if (!project.hasProperty("skipNativeBuild")) {
        dependsOn(":stylus-jni:assembleNative")
    }
    from(rootProject.layout.projectDirectory.dir("stylus-jni/build/native"))
    into(layout.buildDirectory.dir("native-resources/native"))
}

tasks.named("jvmProcessResources") {
    dependsOn(copyNativeLib)
}

android {
    namespace = "com.mohamedrejeb.stylus"
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = project.group.toString(),
        artifactId = "stylus",
        version = project.version.toString(),
    )

    pom {
        name.set("stylus")
        description.set("Pressure-sensitive stylus input library for Kotlin Multiplatform and Compose Multiplatform.")
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
