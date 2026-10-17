import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support; org.jetbrains.kotlin.android is no longer
    // applied (and is rejected by AGP 9). Only the Compose compiler plugin is needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is only configured when a keystore is actually available, so the project
// still assembles (debug, and CI verification) on a machine with no signing material.
// CI supplies the keystore as base64 in ANDROID_KEYSTORE_BASE64 and writes it to
// keystore.properties before invoking Gradle.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProperties.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

android {
    namespace = "com.compressly"
    compileSdk = libs.versions.compileSdk.get().toInt()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.compressly"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        // Overridable from the command line / CI:
        //   ./gradlew assembleRelease -PversionName=1.0.1 -PversionCode=10001
        // The committed values are the fallback for local builds.
        versionName = providers.gradleProperty("versionName").getOrElse("1.0.0")
        versionCode = providers.gradleProperty("versionCode").getOrElse("1").toInt()

        // ffmpeg-kit ships arm64-v8a and x86_64 only, and its native libs dominate the APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed only when keystore material exists, so a verification build never
            // fails merely because a signing secret is absent.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
        // Required for 16 KB memory page alignment on Android 15+ (Google Play requirement).
        // Uncompressed native libs are page aligned; extracted ones are not.
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // ffmpeg-kit ships arm64-v8a and x86_64 only, and its native libs dominate the APK.
    // Splitting by ABI roughly halves the per-device download.
    bundle {
        abi { enableSplit = true }
        density { enableSplit = true }
        language { enableSplit = true }
    }

    lint {
        // Local builds report lint issues without failing; CI passes -PciLint to gate on them.
        abortOnError = providers.gradleProperty("ciLint").isPresent
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.ffmpeg.kit.full.gpl)

    testImplementation(libs.junit)
}