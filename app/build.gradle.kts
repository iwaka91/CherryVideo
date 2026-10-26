plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "id.tege.video"
    compileSdk = 36
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "id.tege.video"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.2-beta3"
        manifestPlaceholders["appLabel"] = "Cherry Video"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        debug {
            // Keep the old application's pending recordings intact while testing this hotfix.
            applicationIdSuffix = ".hotfix"
            manifestPlaceholders["appLabel"] = "Cherry Video Uji"
            System.getenv("CHERRY_DEBUG_KEYSTORE")?.let { path ->
                signingConfig = signingConfigs.getByName("debug").apply { storeFile = file(path) }
            }
        }
        release { isMinifyEnabled = false }
    }
    lint { abortOnError = true; checkDependencies = true }
    testOptions { animationsDisabled = true }
    // MediaPipe ships 32-bit x86 libraries the app does not otherwise support; keep the APK small.
    packaging { jniLibs { excludes += setOf("lib/x86/libmediapipe_tasks_vision_jni.so") } }
}
dependencies {
    implementation(project(":camera"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
