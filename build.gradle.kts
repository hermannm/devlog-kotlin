import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "dev.hermannm"

version = "0.3.0"

// Dependency versions are declared in Gradle version catalog (./gradle/libs.versions.toml)
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinxSerialization)
}

kotlin {
  jvm()

  sourceSets {
    commonMain { dependencies { implementation(libs.kotlinxSerialization) } }
    commonTest {
      dependencies {
        implementation(libs.junit)
        implementation(libs.junitParams)
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

  explicitApi()

  compilerOptions {
    apiVersion.set(KotlinVersion.KOTLIN_2_1)
    languageVersion.set(KotlinVersion.KOTLIN_2_1)

    // Expected-actual classes are in beta (though almost stable):
    // https://kotlinlang.org/docs/multiplatform-expect-actual.html#expected-and-actual-classes
    freeCompilerArgs.add("-Xexpect-actual-classes")
  }
}

tasks.withType<Test> { useJUnitPlatform() }

repositories { mavenCentral() }
