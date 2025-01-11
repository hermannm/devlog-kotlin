import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "dev.hermannm"

version = "0.3.0"

// Dependency versions are declared in Gradle version catalog (./gradle/libs.versions.toml)
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinxSerialization)
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        implementation(libs.kotlinxSerialization)
        implementation(libs.kotlinReflect)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.junitParams)
        runtimeOnly(libs.junitPlatformEngine)
        runtimeOnly(libs.junitPlatformLauncher)
        implementation(libs.kotest)
      }
    }
    jvmMain {
      dependencies {
        implementation(libs.slf4j)
        compileOnly(libs.logback)
        compileOnly(libs.logstashLogbackEncoder)
        compileOnly(libs.jackson)
      }
    }
    jvmTest {
      dependencies {
        runtimeOnly(libs.logback)
        runtimeOnly(libs.logstashLogbackEncoder)
        runtimeOnly(libs.jackson)
      }
    }
  }

  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_1_8
      // Needed due to https://youtrack.jetbrains.com/issue/KT-49746
      freeCompilerArgs.add("-Xjdk-release=1.8")
    }
  }

  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_2_0)
    languageVersion.set(KotlinVersion.KOTLIN_2_0)

    // Expected-actual classes are in beta (though almost stable):
    // https://kotlinlang.org/docs/multiplatform-expect-actual.html#expected-and-actual-classes
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }

  // Require explicit public modifiers, to avoid accidentally publishing internal APIs
  explicitApi()
}

tasks.withType<Test> { useJUnitPlatform() }

repositories { mavenCentral() }
