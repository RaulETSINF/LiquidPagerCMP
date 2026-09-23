import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.maven.publish)
}

// Coordinates and POM metadata live in liquidpage/gradle.properties (GROUP, VERSION_NAME, POM_*).

kotlin {
    // Android
    androidLibrary {
        namespace = "com.raupime.liquidpage"
        compileSdk = 37
        minSdk = 23
        withHostTestBuilder {}
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    // iOS
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // The public API exposes Compose types (Modifier, Shape, AnimationSpec...), so these are `api`.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.ui)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
        }
    }
}

mavenPublishing {
    // Credentials and signing key come from Gradle properties outside the repo.
    publishToMavenCentral()
    // Sign only when a key is configured, so publishToMavenLocal works without one.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    if (hasSigningKey) signAllPublications()
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Empty(),
            sourcesJar = SourcesJar.Sources(),
        ),
    )
}
