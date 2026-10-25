import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The garage model and its rules, in plain Kotlin: no Android, so it can be exported, imported and tested anywhere.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
}
