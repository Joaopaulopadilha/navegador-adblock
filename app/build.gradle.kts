plugins {
    id("com.android.application")
}

android {
    namespace = "com.pessoal.navegador"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pessoal.navegador"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Uso pessoal: assina com a chave de debug
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}