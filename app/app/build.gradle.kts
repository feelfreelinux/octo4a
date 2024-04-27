plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    id("com.bugsnag.android.gradle")
    id("com.gladed.androidgitversion") version "0.4.14"
}

android {
    compileSdk = 28
    buildToolsVersion = "30.0.3"
    defaultConfig {
        applicationId = "com.octo4a"
        minSdk = 17
        targetSdk = 28
        versionName = "1.2.6"
        versionCode = 1002006
        multiDexEnabled = true

        ndk {
            abiFilters.add("x86")
            abiFilters.add("x86_64")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        //testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")
    }
    packagingOptions {
        jniLibs {
            pickFirsts += setOf("**/*.so")
        }
        resources {
            pickFirsts += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
    //    buildFeatures {
////        // Enables Jetpack Compose for this module
////        compose(true)
////    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.4")
    implementation("com.android.support:multidex:1.0.3")
    val lifecycleVersion = "2.3.0"
    val ktorVersion = "1.5.2"
    val koinVersion = "3.1.2"
    val cameraXVersion = "1.0.0"

    // Android lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // CameraX apis
    implementation("androidx.camera:camera-core") {
        version {
            strictly(cameraXVersion)
        }
        because("Umidigi S2 Lite")
    }

    implementation("androidx.camera:camera-camera2") {
        version {
            strictly(cameraXVersion)
        }
        because("Umidigi S2 Lite")
    }
    implementation ("androidx.camera:camera-lifecycle") {
        version {
            strictly(cameraXVersion)
        }
        because("Umidigi S2 Lite")
    }
    implementation("androidx.camera:camera-view:1.0.0-alpha22")
    implementation("androidx.camera:camera-extensions:1.0.0-alpha22")

    // Android-specific dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0-beta01")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.4.0-alpha01")

    // Serial driver
    implementation("com.github.mik3y:usb-serial-for-android:3.3.0")

    // YAML parser
    implementation ("com.github.bmoliveira:snake-yaml:v1.18-android")

    // Koin dependency injection
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-android:$koinVersion")

    // Ktor server & client libraries
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Bugsnag bug reporting
    implementation("com.bugsnag:bugsnag-android:5.12.0")

    // Test dependencies
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")

}
