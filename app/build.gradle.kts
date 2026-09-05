plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire.plugin)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.juhao.murexide"

    ndkVersion = "29.0.14033849"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.juhao.murexide"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    androidResources {
        localeFilters.clear()
        localeFilters.addAll(listOf("zh", "en"))
    }
    packaging {
        resources {
            excludes.addAll(
                listOf(
                    "META-INF/*.kotlin_module",
                    "META-INF/kotlin-tooling-metadata.json",
                    "META-INF/*.version",
                    "META-INF/versions/**",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/AL2.0",
                    "META-INF/LGPL2.1"
            ))
        }
        jniLibs {
            useLegacyPackaging = true
        }
        dex {
            useLegacyPackaging = true
        }
    }
}

wire {
    kotlin {
        javaInterop = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.viewpager2)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.openimage.base)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    implementation(libs.material.symbols.rounded)
    implementation(libs.material.symbols.rounded.filled)

    implementation(libs.wire.runtime)

    implementation(libs.datastore.preferences)
    implementation(libs.datastore)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)

    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)

    implementation(libs.multiplatform.markdown.renderer)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.coil2)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
