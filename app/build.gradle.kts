// IPFilter v4.0.2 (Survival) - Vector/LSPosed Xposed Module
 // Target: 小红书 (com.xingin.xhs)
 // v4.0.1: 数据层删item（修复4.0.0清空content无效）+ 移除setText hook
 // v4.0.2: 补ne8.c同步 — 展开X条回复计数联动，防止"展开5条→点开0条"
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
        versionCode = 6
        versionName = "4.0.2"
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