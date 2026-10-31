plugins { id("com.android.application") }
android {
 namespace = "ru.secondmemory"
 compileSdk = 35
 defaultConfig { applicationId = "ru.secondmemory.mobile"; minSdk = 28; targetSdk = 35; versionCode = 5; versionName = "0.5"; ndk { abiFilters += "arm64-v8a" } }
 ndkVersion = "27.2.12479018"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
dependencies {
 implementation("net.java.dev.jna:jna:5.18.1@aar")
 implementation("com.alphacephei:vosk-android:0.3.75@aar")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20240303")
}
