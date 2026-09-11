plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "ir.tvgram.tdlib"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

// TDLib itself is fetched by scripts/fetch-tdlib.sh; it is never committed
// (Telegram publishes it, and it is tens of megabytes of native code per ABI).
// Two upstream layouts are supported, see the script for details.
val tdlibAar: File = layout.projectDirectory.file("libs/tdlib.aar").asFile
val tdlibSources: File = layout.projectDirectory.file("src/main/java/org/drinkless/tdlib/TdApi.java").asFile

fun tdlibPresent(): Boolean = tdlibAar.exists() || tdlibSources.exists()

// Configuration must stay silent when TDLib is absent, otherwise the mock
// flavour — which never touches it — could not be configured either. The check
// is deferred to the point where this module is actually built.
val verifyTdlibArtifact by tasks.registering {
    outputs.upToDateWhen { tdlibPresent() }
    doFirst {
        if (!tdlibPresent()) {
            throw GradleException(
                "\n\nTDLib is not installed in the :tdlib module.\n" +
                    "  * run ./scripts/fetch-tdlib.sh to download the official Telegram build, or\n" +
                    "  * build the mock flavour instead: ./gradlew assembleMockDebug\n"
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(verifyTdlibArtifact) }

dependencies {
    api(project(":telegram"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    if (tdlibAar.exists()) {
        api(files(tdlibAar))
    }
}
