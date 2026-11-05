import java.util.Properties

// AGP 9: Kotlin встроен в Android-плагин, отдельный org.jetbrains.kotlin.android не нужен.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Подпись release: в CI — из переменных окружения (GitHub Secrets), локально — из
 * keystore.properties в корне (в .gitignore). Нет ни того, ни другого — release
 * собирается неподписанным. Ключ и пароли в репозиторий не попадают никогда.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(envName: String, propertyName: String): String? =
    providers.environmentVariable(envName).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)

val releaseStoreFile = signingValue("STANDBY_KEYSTORE_FILE", "storeFile")

// versionCode растёт с номером запуска CI, чтобы новая сборка ставилась поверх старой.
val ciVersionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
val ciVersionName = providers.environmentVariable("STANDBY_VERSION_NAME").orNull?.removePrefix("v")

android {
    namespace = "com.realfamousbae.standby.dock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.realfamousbae.standby.dock"
        minSdk = 31
        // targetSdk = версия ОС на S24 Ultra (Android 17), чтобы тестировать реальное поведение.
        targetSdk = 37
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "0.0.0-local"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("STANDBY_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("STANDBY_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("STANDBY_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
