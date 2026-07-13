plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.shrivatsav.monomail.core.pgp"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        val secretsFile = rootProject.file("secrets.properties")
        val googleClientId = if (secretsFile.exists()) {
            secretsFile.readLines().firstOrNull { it.startsWith("GOOGLE_CLIENT_ID=") }?.substringAfter("=") ?: ""
        } else ""
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    
    // Security & PGP
    implementation(libs.androidx.security.crypto)
    implementation(libs.pgpainless.core)

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
