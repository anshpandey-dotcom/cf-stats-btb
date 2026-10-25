plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.anshpandey.pingai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anshpandey.pingai"
        minSdk = 21
        targetSdk = 35
        versionCode = 9
        versionName = "1.4"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/pingai.jks")
            storePassword = "pingai2026"
            keyAlias = "pingai"
            keyPassword = "pingai2026"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    flavorDimensions += "mode"

    productFlavors {
        create("lite") {
            dimension = "mode"
            buildConfigField("boolean", "AUTO", "false")
            resValue("string", "app_name", "PING AI")
        }
        create("auto") {
            dimension = "mode"
            applicationIdSuffix = ".auto"
            versionNameSuffix = "-auto"
            buildConfigField("boolean", "AUTO", "true")
            resValue("string", "app_name", "PING AI")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
