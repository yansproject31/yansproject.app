import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
}

android {
    namespace = "com.yansproject.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yansproject.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AAB Optimization (ABI Splits) to shrink bundle size and optimize delivery
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: System.getenv("CM_KEYSTORE_PATH")
            keyAlias = System.getenv("CM_KEY_ALIAS")
            keyPassword = System.getenv("CM_KEY_PASSWORD")
            storePassword = System.getenv("CM_KEYSTORE_PASSWORD")

            if (keystorePath.isNullOrEmpty() || keyAlias.isNullOrEmpty() || keyPassword.isNullOrEmpty() || storePassword.isNullOrEmpty()) {
                val isReleaseTask = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
                if (isReleaseTask) {
                    throw org.gradle.api.GradleException(
                        "ERROR: Release build is requested, but the official signing environment variables (CM_KEY_ALIAS, CM_KEY_PASSWORD, CM_KEYSTORE_PASSWORD) are not configured!"
                    )
                } else {
                    val defaultDebug = signingConfigs.getByName("debug")
                    storeFile = defaultDebug.storeFile
                    storePassword = defaultDebug.storePassword
                    keyAlias = defaultDebug.keyAlias
                    keyPassword = defaultDebug.keyPassword
                }
            } else {
                storeFile = file(keystorePath)
            }
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")

            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "false")
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.converter.moshi)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    implementation(libs.sqlcipher)
    implementation(libs.sqlite.ktx)

    implementation(libs.play.core.app.update)
    implementation(libs.play.core.app.update.ktx)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)

    implementation(libs.hilt.android)
    "ksp"(libs.hilt.compiler)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}