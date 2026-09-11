import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * api_id / api_hash come from my.telegram.org. Resolution order:
 *   1. local.properties   (developer machine, git-ignored)
 *   2. gradle.properties / -P flags / ORG_GRADLE_PROJECT_* env (CI secrets)
 * When both are empty the app still builds and asks for the credentials on the
 * setup screen at runtime.
 */
fun secret(name: String): String {
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
        val props = Properties().apply { localProperties.inputStream().use(::load) }
        props.getProperty(name)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    }
    return (project.findProperty(name) as String?)?.trim().orEmpty()
}

android {
    namespace = "ir.tvgram.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.tvgram.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("int", "TELEGRAM_API_ID", secret("TELEGRAM_API_ID").ifEmpty { "0" })
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${secret("TELEGRAM_API_HASH")}\"")

    }

    flavorDimensions += "backend"
    productFlavors {
        create("real") {
            dimension = "backend"
            // Talks to Telegram through TDLib. Needs tdlib/libs/tdlib.aar.
        }
        create("mock") {
            dimension = "backend"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            // Runs the whole UI off generated sample data: no account, no
            // native library, works in a plain TV emulator.
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    lint {
        // A missing translation should not break a debug build on CI.
        disable += "MissingTranslation"
        // Media3's @UnstableApi is an annotation, not a Kotlin opt-in marker, so
        // it cannot be acknowledged with -opt-in. The player deliberately uses
        // those APIs (PlayerView, renderer configuration, custom DataSource).
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    implementation(project(":telegram"))
    "realImplementation"(project(":tdlib"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.zxing.core)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
