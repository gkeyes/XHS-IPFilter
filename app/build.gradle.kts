// IPFilter v4.0.3 (Survival) - Vector/LSPosed Xposed Module
 // Target: 小红书 (com.xingin.xhs)
 // v4.0.1: 数据层删item + 移除setText hook
 // v4.0.2: 补ne8.c同步
 // v4.0.3: 修复PrefManager强制默认值bug，恢复文件>SP>默认值优先级
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.xposed.ipfilter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xposed.ipfilter"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "4.0.3"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Xposed libxposed stubs - compileOnly (not bundled into APK)
    compileOnly(project(":libxposed-stubs"))
}