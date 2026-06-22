import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val rapidOcrAar = file("libs/OcrLibrary-1.3.0-release.aar")

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

fun releaseSigningFromLocalProperties() = run {
    val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE") ?: return@run null
    val storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: return@run null
    val keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: return@run null
    val keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: return@run null
    val storeFile = rootProject.file(storeFilePath)
    if (!storeFile.isFile) return@run null
    mapOf(
        "storeFile" to storeFile,
        "storePassword" to storePassword,
        "keyAlias" to keyAlias,
        "keyPassword" to keyPassword,
    )
}

android {
    namespace = "com.example.schoolbuswidget"
    buildFeatures {
        buildConfig = true
    }
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.schoolbuswidget"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigningFromLocalProperties()?.let { signing ->
            create("release") {
                storeFile = signing["storeFile"] as java.io.File
                storePassword = signing["storePassword"] as String
                keyAlias = signing["keyAlias"] as String
                keyPassword = signing["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        jniLibs {
            pickFirsts += "lib/**/libonnxruntime.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    if (rapidOcrAar.isFile) {
        implementation(files(rapidOcrAar))
    } else {
        throw GradleException(
            "缺少 RapidOCR 引擎库。请将 OcrLibrary-1.3.0-release.aar 放入 app/libs/ 后再构建。\n" +
                "下载：https://github.com/RapidAI/RapidOcrAndroidOnnx/releases/download/1.3.0/OcrLibrary-1.3.0-release.aar",
        )
    }
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}