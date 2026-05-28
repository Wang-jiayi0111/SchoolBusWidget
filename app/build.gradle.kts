plugins {
    alias(libs.plugins.android.application)
}

val rapidOcrAar = file("libs/OcrLibrary-1.3.0-release.aar")

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

    buildTypes {
        release {
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