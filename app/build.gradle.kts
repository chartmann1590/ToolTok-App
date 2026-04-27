import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

fun localString(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)?.trim().orEmpty().ifBlank { defaultValue }

fun localBoolean(name: String, defaultValue: Boolean = false): Boolean =
    localProperties
        .getProperty(name)
        ?.trim()
        ?.lowercase()
        ?.let { it == "true" }
        ?: defaultValue

fun escapeForBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

val adsEnabled = localBoolean("tooltok.ads.enabled", false)
val configuredAppId = localString("tooltok.ads.appId")
val configuredAppOpenAdUnitId = localString("tooltok.ads.appOpenAdUnitId")
val configuredBannerAdUnitId = localString("tooltok.ads.bannerAdUnitId")
val ciVersionCodeOverride = (
    findProperty("tooltokCiVersionCode") as String?
        ?: localString("tooltok.ci.versionCode")
).toIntOrNull()
    ?: System.getenv("TOOLTOK_CI_VERSION_CODE")?.toIntOrNull()
val releaseSigningStoreFile = localString("tooltok.signing.storeFile")
val releaseSigningStorePassword = localString("tooltok.signing.storePassword")
val releaseSigningKeyAlias = localString("tooltok.signing.keyAlias")
val releaseSigningKeyPassword = localString("tooltok.signing.keyPassword")
val hasReleaseSigningConfig =
    releaseSigningStoreFile.isNotBlank() &&
        releaseSigningStorePassword.isNotBlank() &&
        releaseSigningKeyAlias.isNotBlank() &&
        releaseSigningKeyPassword.isNotBlank()

val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testAppOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/9214589741"

if (adsEnabled &&
    (configuredAppId.isBlank() ||
        configuredAppOpenAdUnitId.isBlank() ||
        configuredBannerAdUnitId.isBlank())
) {
    logger.warn(
        "AdMob release IDs are missing in local.properties. Release builds will fall back to Google test IDs."
    )
}

if (!hasReleaseSigningConfig) {
    logger.warn(
        "Release signing properties are missing in local.properties. Release builds will fall back to the debug keystore."
    )
}

if (System.getenv("CI") == "true" && ciVersionCodeOverride == null) {
    logger.warn(
        "CI versionCode override is missing. Falling back to static versionCode from Gradle."
    )
}

android {
    namespace = "com.tooltok.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tooltok.app"
        minSdk = 26
        targetSdk = 34
        versionCode = ciVersionCodeOverride ?: 2
        versionName = "1.1.0"

        testInstrumentationRunner = "com.tooltok.app.ToolTokTestRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = rootProject.file(releaseSigningStoreFile)
                storePassword = releaseSigningStorePassword
                keyAlias = releaseSigningKeyAlias
                keyPassword = releaseSigningKeyPassword
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField(
                "String",
                "ADMOB_APP_OPEN_AD_UNIT_ID",
                "\"${escapeForBuildConfig(if (adsEnabled) testAppOpenAdUnitId else "")}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"${escapeForBuildConfig(if (adsEnabled) testBannerAdUnitId else "")}\""
            )
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] =
                if (adsEnabled) configuredAppId.ifBlank { testAppId } else testAppId
            buildConfigField(
                "String",
                "ADMOB_APP_OPEN_AD_UNIT_ID",
                "\"${escapeForBuildConfig(if (adsEnabled) configuredAppOpenAdUnitId.ifBlank { testAppOpenAdUnitId } else "")}\""
            )
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                "\"${escapeForBuildConfig(if (adsEnabled) configuredBannerAdUnitId.ifBlank { testBannerAdUnitId } else "")}\""
            )
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
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.google.android.gms:play-services-ads:25.2.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
