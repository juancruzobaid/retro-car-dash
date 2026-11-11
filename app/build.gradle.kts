plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val localProperties = mutableMapOf<String, String>()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localFile.readLines().forEach { line ->
        val parts = line.split("=", limit = 2)
        if (parts.size == 2) {
            localProperties[parts[0].trim()] = parts[1].trim()
        }
    }
}
val stadiaApiKey = localProperties["STADIA_MAPS_API_KEY"] ?: ""

android {
    namespace = "com.cpobaid.retrocardash"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cpobaid.retrocardash"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "STADIA_MAPS_API_KEY", "\"$stadiaApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.maplibre.gl:android-sdk:11.13.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
