@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.asnaeb"
version = "0.0.4"

kotlin {
    jvm()
    androidLibrary {
        namespace = "io.github.asnaeb.remotedata"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "remotedata", version.toString())

    pom {
        name = "RemoteData"
        description = "A data fetching library for Kotlin Multiplatform."
        inceptionYear = "2026"
        url = "https://github.com/asnaeb/remotedata"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "asnaeb"
                name = "Roberto De Lucia"
                url = "https://github.com/asnaeb"
            }
        }
        scm {
            url = "https://github.com/asnaeb/remotedata"
            connection = "scm:git:https://github.com/asnaeb/remotedata.git"
            developerConnection = "scm:git:ssh://git@github.com/asnaeb/remotedata.git"
        }
    }
}
