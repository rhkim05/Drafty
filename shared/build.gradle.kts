plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
    id("com.squareup.wire")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    // iOS targets (future)
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("com.squareup.wire:wire-runtime:5.1.0")
            implementation("com.benasher44:uuid:0.8.4")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }

        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            implementation("androidx.ink:ink-authoring:1.0.0-alpha01")
            implementation("androidx.ink:ink-rendering:1.0.0-alpha01")
            implementation("androidx.ink:ink-strokes:1.0.0-alpha01")
            implementation("androidx.ink:ink-brush:1.0.0-alpha01")
            implementation("androidx.ink:ink-geometry:1.0.0-alpha01")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }
    }
}

android {
    namespace = "com.drafty.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("DraftyDatabase") {
            packageName.set("com.drafty.db")
        }
    }
}

wire {
    kotlin {
    }
    sourcePath {
        srcDir("src/commonMain/proto")
    }
}
