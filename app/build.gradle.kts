plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tooltok.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tooltok.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            initWith(getByName("debug"))
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"ca-app-pub-3940256099942544/1033173712\""
            )
            buildConfigField("long", "ADMOB_MIN_MILLIS_BETWEEN_ADS", "5000L")
            buildConfigField("int", "ADMOB_ROUTE_CHANCE_PERCENT", "100")
            buildConfigField("int", "ADMOB_SCROLL_CHANCE_PERCENT", "100")
            buildConfigField("long", "ADMOB_SESSION_CHECK_INTERVAL_MILLIS", "8000L")
            buildConfigField("int", "ADMOB_SESSION_CHANCE_PERCENT", "100")
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-8382831211800454~7760950458"
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                "\"ca-app-pub-8382831211800454/3580589333\""
            )
            buildConfigField("long", "ADMOB_MIN_MILLIS_BETWEEN_ADS", "90000L")
            buildConfigField("int", "ADMOB_ROUTE_CHANCE_PERCENT", "30")
            buildConfigField("int", "ADMOB_SCROLL_CHANCE_PERCENT", "35")
            buildConfigField("long", "ADMOB_SESSION_CHECK_INTERVAL_MILLIS", "45000L")
            buildConfigField("int", "ADMOB_SESSION_CHANCE_PERCENT", "35")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.android.gms:play-services-ads:24.7.0")

    testImplementation("junit:junit:4.13.2")
}
