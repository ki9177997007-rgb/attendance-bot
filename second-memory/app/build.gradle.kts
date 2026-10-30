plugins { id("com.android.application") }
android {
 namespace = "ru.secondmemory"
 compileSdk = 35
 defaultConfig { applicationId = "ru.secondmemory"; minSdk = 28; targetSdk = 35; versionCode = 3; versionName = "0.3" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
}
dependencies {
 implementation("net.java.dev.jna:jna:5.18.1@aar")
 implementation("com.alphacephei:vosk-android:0.3.75@aar")
 testImplementation("junit:junit:4.13.2")
}
