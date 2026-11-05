// Чистый Kotlin/JVM: сюда нельзя подключить Android SDK, и это граница архитектуры.
// Всё, что можно проверить unit-тестом без устройства, живёт здесь.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
