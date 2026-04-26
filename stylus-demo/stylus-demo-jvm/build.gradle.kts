import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinCompose)
}

dependencies {
    implementation(projects.stylus)
    implementation(projects.stylusCompose)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.ui)
    implementation(compose.desktop.common)
    implementation(compose.desktop.currentOs)

    implementation(libs.calf.file.picker)
}

description = "Compose Desktop demo for the stylus library"

compose.desktop {
    application {
        mainClass = "com.mohamedrejeb.stylus.demo.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "stylus-demo"
            packageVersion = "1.0.0"
        }
    }
}
