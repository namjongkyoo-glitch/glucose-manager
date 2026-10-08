import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun loadProperties(file: File): Properties? =
    if (file.isFile) Properties().apply { file.inputStream().use { load(it) } } else null

val localProps = loadProperties(rootProject.file("local.properties")) ?: Properties()
val kakaoKey: String = localProps.getProperty("KAKAO_NATIVE_APP_KEY", "").trim()

// Keystore lookup order: env var -> repo root (gitignored) -> %USERPROFILE%/.android-keys
val keystoreProps: Properties? = listOfNotNull(
    System.getenv("GLUCOSE_KEYSTORE_PROPERTIES")?.let { File(it) },
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".android-keys/keystore.properties"),
).firstNotNullOfOrNull { loadProperties(it) }

android {
    namespace = "com.jadennam.glucose"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jadennam.glucose"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        manifestPlaceholders["kakaoScheme"] = if (kakaoKey.isNotEmpty()) "kakao$kakaoKey" else "kakaodisabled"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Dependency/SDK version freshness is managed manually in libs.versions.toml.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kakao.user)
    implementation(libs.kakao.talk)

    testImplementation(libs.junit)
}
