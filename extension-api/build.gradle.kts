plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.egg.extension.api"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
