buildscript {
    repositories{
        mavenCentral()
    }

    dependencies {
        classpath ("com.google.gms:google-services:4.4.4")
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("org.lsposed.lsparanoid") version "0.6.0"
}
