plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.egg.example"
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
    implementation(project(":extension-api"))
}



// assembleEgg: build .egg (classes.dex + manifest.json zipped) from AAR
tasks.register("assembleEgg") {
    notCompatibleWithConfigurationCache("Uses ProcessBuilder + ant zip")
    dependsOn("assembleDebug")
    doLast {
        val aar = layout.buildDirectory.file("outputs/aar/example-extension-debug.aar").get().asFile
        require(aar.exists()) { "AAR not found: $aar" }

        val work = layout.buildDirectory.dir("egg-work").get().asFile
        work.deleteRecursively(); work.mkdirs()

        copy {
            from(zipTree(aar)) { include("classes.jar") }
            into(work)
        }
        val jar = File(work, "classes.jar")
        require(jar.exists()) { "classes.jar not found in AAR" }

        val dexOut = File(work, "dex"); dexOut.mkdirs()
        val localProps = rootProject.file("local.properties")
        val sdkPath = if (localProps.exists()) {
            localProps.readLines().firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("sdk.dir=")?.replace("\\:", ":")?.replace("\\\\", "\\")
        } else null
        requireNotNull(sdkPath) { "sdk.dir not found in local.properties" }
        val buildTools = File(sdkPath, "build-tools").listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
            ?: error("build-tools not found under $sdkPath")
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val d8 = File(buildTools, if (isWin) "d8.bat" else "d8")

        val pb = ProcessBuilder(d8.absolutePath, "--output", dexOut.absolutePath, jar.absolutePath)
        pb.inheritIO()
        val rc = pb.start().waitFor()
        require(rc == 0) { "d8 failed with exit $rc" }
        val dex = File(dexOut, "classes.dex")
        require(dex.exists()) { "classes.dex not produced" }

        val stage = File(work, "stage"); stage.deleteRecursively(); stage.mkdirs()
        copy {
            from("src/main/assets/manifest.json")
            from(dex) { rename { "classes.dex" } }
            into(stage)
        }

        val egg = layout.buildDirectory.file("example.egg").get().asFile
        egg.delete()
        ant.withGroovyBuilder {
            "zip"("destfile" to egg.absolutePath) {
                "zipfileset"("dir" to stage.absolutePath, "includes" to "manifest.json,classes.dex")
            }
        }
        println("EGG built: ${egg.absolutePath} (${egg.length()} bytes)")
    }
}
