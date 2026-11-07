// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hsh.meupdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hsh.meupdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Motor MuPDF (o mesmo do PyMuPDF): PDF, EPUB, XPS. Licença AGPL: ok para uso pessoal.
    implementation("com.artifex.mupdf:fitz:1.2+")

    // CBR (RAR): leitura em Java puro
    implementation("com.github.junrar:junrar:7.5.+")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
