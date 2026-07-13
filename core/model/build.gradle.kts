plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.shrivatsav.monomail.core.model"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("playstore") {
            dimension = "distribution"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Basic Android dependencies since we use android.net.Uri
    implementation(libs.androidx.core.ktx)

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
}
