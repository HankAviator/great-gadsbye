plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.hankaviator.greatgadsbye"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hankaviator.greatgadsbye"
        minSdk = 27
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")
    compileOnly("de.robv.android.xposed:api:82")
}
