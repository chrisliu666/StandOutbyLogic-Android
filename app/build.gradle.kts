plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 正式签名：CI 从 Secrets 还原 keystore 后通过环境变量注入。
// 本地没有这些变量时，release 构建保持未签名，不影响 debug 与单元测试。
val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_FILE")

android {
    namespace = "com.standoutbylogic.sudoku"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.standoutbylogic.sudoku"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "standoutbylogic"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
