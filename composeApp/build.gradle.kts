import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)

    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)

            implementation("androidx.core:core-splashscreen:1.2.0")
            implementation("androidx.navigation:navigation-compose:2.9.8")
            implementation("com.google.firebase:firebase-messaging-ktx:24.1.2")
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.12.0"))
            implementation("com.google.firebase:firebase-analytics")
            implementation("com.google.firebase:firebase-crashlytics")
            implementation("com.google.android.gms:play-services-ads:25.2.0")

            implementation("androidx.camera:camera-core:1.6.0")
            implementation("androidx.camera:camera-camera2:1.6.0")
            implementation("androidx.camera:camera-lifecycle:1.6.0")
            implementation("androidx.camera:camera-view:1.6.0")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation("io.ktor:ktor-client-core:3.1.3")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.1.3")
        }
    }
}

android {
    namespace = "com.kmp.template"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kmp.template"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        val forceAbDisabled = (project.findProperty("forceAbDisabled")
            ?: System.getenv("FORCE_AB_DISABLED")
            ?: "false").toString().toBoolean()
        buildConfigField("boolean", "FORCE_AB_DISABLED", forceAbDisabled.toString())
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

afterEvaluate {
    tasks.named("uploadCrashlyticsMappingFileRelease")
        .configure { enabled = false }
}

afterEvaluate {
    if (System.getenv("CI_BUILD") != "true") {
        tasks.named("bundleRelease").configure {
            finalizedBy("removeProguardMap")
        }
    }
}

tasks.register("removeProguardMap") {
    val releaseDir = layout.projectDirectory.dir("release").asFile.absolutePath
    doLast {
        val aabFile = File("$releaseDir/composeApp-release.aab")
        val zipFile = File("$releaseDir/composeApp-release.zip")
        val savedProguardMapFile = File("$releaseDir/proguard.map")
        val tempZipFilePath = File("$releaseDir/composeApp-release-temp.zip")
        val targetFilePath = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"

        aabFile.renameTo(zipFile)

        val zf = ZipFile(zipFile)
        val zos = ZipOutputStream(tempZipFilePath.outputStream())
        try {
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipEntry
                if (entry.name != targetFilePath) {
                    zos.putNextEntry(ZipEntry(entry.name))
                    zf.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                } else {
                    zf.getInputStream(entry).use { input ->
                        savedProguardMapFile.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        } finally {
            zos.close()
            zf.close()
        }

        zipFile.delete()
        tempZipFilePath.renameTo(aabFile)
    }
}
