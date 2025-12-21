// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Уберите дублирование с buildscript
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.dagger.hilt.android") version "2.47" apply false
}

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.47") // версия должна совпадать с app
    }
}
