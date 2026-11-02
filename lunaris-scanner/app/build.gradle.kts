import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing. CI passes the keystore through the LUNARIS_KEYSTORE_FILE, LUNARIS_KEYSTORE_PASSWORD,
// LUNARIS_KEY_ALIAS and LUNARIS_KEY_PASSWORD environment variables; locally it comes from a
// keystore.properties kept outside the repo (LUNARIS_KEYSTORE_PROPERTIES, default
// ~/.keys/lunaris/keystore.properties). With neither, release builds fall back to the debug key.
val releaseKeystore: Properties? = System.getenv("LUNARIS_KEYSTORE_FILE")?.let { storeFile ->
    fun env(name: String) = requireNotNull(System.getenv(name)) { "$name is not set" }
    Properties().apply {
        setProperty("storeFile", storeFile)
        setProperty("storePassword", env("LUNARIS_KEYSTORE_PASSWORD"))
        setProperty("keyAlias", env("LUNARIS_KEY_ALIAS"))
        setProperty("keyPassword", env("LUNARIS_KEY_PASSWORD"))
    }
} ?: (System.getenv("LUNARIS_KEYSTORE_PROPERTIES")?.let(::File)
    ?: File(System.getProperty("user.home"), ".keys/lunaris/keystore.properties"))
    .takeIf { it.isFile }
    ?.let { props -> Properties().apply { props.inputStream().use(::load) } }

android {
    namespace = "io.github.martindavinci.lunaris.scanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.martindavinci.lunaris.scanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        releaseKeystore?.let { props ->
            create("release") {
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // No dependency metadata block in the APK: it is encrypted for Google only, and F-Droid and
    // IzzyOnDroid reject APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // The language can be switched in the app, so every translation must ship with it.
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
}
