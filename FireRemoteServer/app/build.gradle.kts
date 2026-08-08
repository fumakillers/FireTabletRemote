plugins {
    id("com.android.application")
}

android {
    namespace = "com.fumakillers.fireremoteserver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fumakillers.fireremoteserver"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
}
