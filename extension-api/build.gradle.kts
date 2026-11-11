// HARDCODED_PLUGINS: pakai versi eksplisit supaya bisa dipakai via includeBuild
plugins {
    id("com.android.library") version "9.4.1"
    `maven-publish`
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("eggApi") {
                groupId = "com.egg"
                artifactId = "extension-api"
                version = "1.0.0"
                afterEvaluate {
                    from(components["release"])
                }
            }
        }
    }
}

android {
    namespace = "com.egg.extension.api"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    publishing {
        singleVariant("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
