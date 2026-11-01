plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.kostyay.xiaomiappsbridge"
    compileSdk = 37

    packaging {
        resources.merges += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        resources.excludes += "META-INF/versions/**/OSGI-INF/MANIFEST.MF"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.kostyay.xiaomiappsbridge.diagnostic"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").getOrElse("4").toInt()
        versionName = providers.gradleProperty("VERSION_NAME").getOrElse("2.0.2-mitv-diagnostic.2")
    }

    val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

ktlint {
    version.set("1.8.0")
    android.set(true)
    outputToConsole.set(true)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("com.flyfishxu:kadb:2.1.5")
}
