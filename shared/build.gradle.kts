import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    // SKIE (Swift-friendly bindings) — add once pinned to a version compatible with
    // this Kotlin (see iosApp/README.md). Kept out of the default build so the
    // currently-green build is not coupled to a SKIE/Kotlin version match.
    // alias(libs.plugins.skie)
}

kotlin {
    androidLibrary {
        namespace = "com.shrivatsav.monomail.shared"
        compileSdk = 35
        minSdk = 26
    }

    // Assembles shared.xcframework (consumed by iosApp via SPM binaryTarget).
    // Task: ./gradlew :shared:assembleSharedReleaseXCFramework (or ...DebugXCFramework)
    val xcf = XCFramework("shared")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.shrivatsav.monomail.shared.database")
        }
    }
}
