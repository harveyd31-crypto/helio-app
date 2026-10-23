// Vendored from Ridge (AGPL-3.0-or-later, see NOTICE). Pure Kotlin/JVM, no Android dependency.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}
kotlin {
    jvmToolchain(17)
    explicitApi()
}
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
