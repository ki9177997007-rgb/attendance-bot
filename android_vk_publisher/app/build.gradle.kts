plugins { id("com.android.application") }
android { namespace = "ru.videofactory.publisher"; compileSdk = 35
    defaultConfig { applicationId = "ru.videofactory.publisher"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
