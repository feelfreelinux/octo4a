plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(28)
    buildToolsVersion("30.0.3")

    defaultConfig {
        applicationId = "com.octo4a"
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner("androidx.test.runner.AndroidJUnitRunner")

        ndk {
            moduleName = "hello-jni"
        }
    }
//    buildFeatures {
////        // Enables Jetpack Compose for this module
////        compose(true)
////    }
    packagingOptions {
        pickFirst("META-INF/INDEX.LIST")
        pickFirst("META-INF/io.netty.versions.properties")
    }
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
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.navigation:navigation-fragment-ktx:2.2.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.2.2")
    val lifecycleVersion = "2.3.0"
    val ktorVersion = "1.5.2"
    val koinVersion = "3.0.1-beta-1"

    // Android lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // Android-specific dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0-alpha2")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.4.0-alpha01")

    // Serial driver
    implementation("com.github.mik3y:usb-serial-for-android:3.3.0")

    // Koin dependency injection
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-android:$koinVersion")

    // Ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")

    // Test dependencies
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")

}