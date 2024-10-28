plugins {
    alias(libs.plugins.kotlinter)
    id("com.android.application")
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinPluginSerialization)
    id("maplibre.gradle-make")
    id("maplibre.gradle-config")
    id("maplibre.gradle-checkstyle")
    id("maplibre.gradle-lint")
}

android {
    namespace = "com.microsoft.maps.v9.toolsapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.microsoft.maps.v9.toolsapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isJniDebuggable = true
            isDebuggable = true
            isTestCoverageEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    flavorDimensions += "renderer"
    productFlavors {
        create("legacy") {
            dimension = "renderer"
        }
        create("drawable") {
            dimension = "renderer"
        }
        create("vulkan") {
            dimension = "renderer"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":MapLibreAndroid"))
    implementation(libs.maplibreJavaTurf)

    implementation(libs.supportRecyclerView)
    implementation(libs.supportDesign)
    implementation(libs.supportConstraintLayout)
    implementation(libs.kotlinxSerializationJson)

    implementation(libs.multidex)
    implementation(libs.timber)
    implementation(libs.okhttp3)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.fasterxmlJacksonModuleKotlin)

    debugImplementation(libs.leakCanary)

    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.supportAnnotations)
    androidTestImplementation(libs.testRunner)
    androidTestImplementation(libs.testRules)
    androidTestImplementation(libs.testEspressoCore)
    androidTestImplementation(libs.testEspressoIntents)
    androidTestImplementation(libs.testEspressoContrib)
    androidTestImplementation(libs.testUiAutomator)
    androidTestImplementation(libs.appCenter)
    androidTestImplementation(libs.androidxTestExtJUnit)
    androidTestImplementation(libs.androidxTestCoreKtx)
    androidTestImplementation(libs.kotlinxCoroutinesTest)
}
