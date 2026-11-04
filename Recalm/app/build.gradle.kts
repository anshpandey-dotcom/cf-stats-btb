import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val sherpaVersion = "2.0.1"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")

val fetchSherpa by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        if (!sherpaAar.exists()) {
            sherpaAar.parentFile.mkdirs()
            URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar")
                .toURL().openStream().use { input -> sherpaAar.outputStream().use { input.copyTo(it) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchSherpa) }

android {
    namespace = "com.ansh.recalm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ansh.recalm"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            storeFile = file("recalm.keystore")
            storePassword = "recalm123"
            keyAlias = "recalm"
            keyPassword = "recalm123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("recalm.fixtures", System.getProperty("recalm.fixtures") ?: "")
            it.systemProperty("recalm.shots", System.getProperty("recalm.shots") ?: "")
            it.maxHeapSize = "3g"
            it.systemProperty("roborazzi.test.record", "true")
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/")
        }
    }
    packaging {
        resources.excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(files(sherpaAar))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media:media:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.40.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
