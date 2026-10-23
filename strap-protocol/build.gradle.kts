// Vendored from Ridge (AGPL-3.0-or-later, see NOTICE). Pure Kotlin/JVM, no Android dependency.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}
// Target Java 17 bytecode with whatever JDK runs Gradle (Android Studio's bundled one), so no toolchain download is needed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
