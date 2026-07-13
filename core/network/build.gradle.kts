plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.shrivatsav.monomail.core.network"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)

    // Networking
    // Providers in :app currently rely on retrofit2.HttpException, so we expose Retrofit via api.
    api(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // IMAP/SMTP — Eclipse Angus Mail (Jakarta Mail 2.x)
    api("org.eclipse.angus:angus-mail:2.0.3") {
        exclude(group = "jakarta.xml.soap")
    }
    api("org.eclipse.angus:angus-activation:2.0.2")

    // Hilt for DI (since network clients are usually provided via DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
