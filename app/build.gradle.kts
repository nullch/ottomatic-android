plugins {
    id("com.android.application")
}

val sdlDir = rootProject.file("third_party/SDL")
val ottoDir = rootProject.file("third_party/OttoMatic")

android {
    namespace = "ru.nullch.ottomaticfork"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "ru.nullch.ottomaticfork"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "4.0.2-android0.1"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // SDL's Java side (SDLActivity & friends) is compiled straight from the SDL checkout
    sourceSets["main"].java.srcDir(sdlDir.resolve("android-project/app/src/main/java"))
    // Game Data folder is bundled as assets/Data/...
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/ottoAssets"))

    packaging {
        jniLibs { useLegacyPackaging = false }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Copy third_party/OttoMatic/Data -> build/generated/ottoAssets/Data
val copyOttoData by tasks.registering(Copy::class) {
    from(ottoDir.resolve("Data")) { into("Data") }
    into(layout.buildDirectory.dir("generated/ottoAssets"))
}
tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(copyOttoData)
}
