import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "dev.hermannm"

version = "0.5.0"

mavenPublishing {
  coordinates(group.toString(), artifactId = "devlog-kotlin", version.toString())

  pom {
    name = "devlog-kotlin"
    description =
        "Logging library for Kotlin JVM, that thinly wraps SLF4J and Logback to provide a more ergonomic API."
    url = "https://hermannm.dev/devlog"
    inceptionYear = "2024"

    licenses {
      license {
        name = "MIT"
        url = "https://github.com/hermannm/devlog-kotlin/blob/main/LICENSE"
        distribution = "repo"
      }
    }

    developers {
      developer {
        id = "hermannm"
        name = "Hermann Mørkrid"
        url = "https://hermannm.dev"
      }
    }

    scm {
      url = "https://github.com/hermannm/devlog-kotlin"
      connection = "scm:git:https://github.com/hermannm/devlog-kotlin.git"
      developerConnection = "scm:git:https://github.com/hermannm/devlog-kotlin.git"
    }
  }

  signAllPublications()
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
}

// Dependency versions are declared in Gradle version catalog (./gradle/libs.versions.toml)
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinxSerialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.gradleMavenPublish)
  alias(libs.plugins.gradleVersions)
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

repositories { mavenCentral() }

tasks.withType<Test> { useJUnitPlatform() }

spotless {
  kotlin {
    toggleOffOn()
    ktfmt(libs.versions.ktfmt.get())
  }
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    val invalidVersionRegexes =
        listOf(
            Regex("(?i).*Alpha(?:-?\\d+)?"),
            Regex("(?i).*a(?:-?\\d+)?"),
            Regex("(?i).*Beta(?:-?\\d+)?"),
            Regex("(?i).*-B(?:-?\\d+)?"),
            Regex("(?i).*RC(?:-?\\d+)?"),
            Regex("(?i).*CR(?:-?\\d+)?"),
            Regex("(?i).*M(?:-?\\d+)?"),
        )
    return@rejectVersionIf invalidVersionRegexes.any { it.matches(candidate.version) }
  }
}
