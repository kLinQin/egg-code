pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.library") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "typescript"

// Composite: ambil :extension-api dari repo Egg parent
includeBuild("../../../extension-api") {
    dependencySubstitution {
        substitute(module("com.egg:extension-api"))
            .using(project(":"))
    }
}
