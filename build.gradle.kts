import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
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

  configure(KotlinMultiplatform(javadocJar = JavadocJar.Dokka("dokkaGenerate"), sourcesJar = true))
  signAllPublications()
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
}

// Dependency versions are declared in Gradle version catalog (./gradle/libs.versions.toml)
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinxSerialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.gradleMavenPublish)
  alias(libs.plugins.dokka)
  alias(libs.plugins.gradleVersions)
  signing
}

subprojects {
  apply(plugin = rootProject.libs.plugins.kotlinJvm.get().pluginId)
  apply(plugin = rootProject.libs.plugins.kotlinxSerialization.get().pluginId)
  apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

  repositories { mavenCentral() }
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
        implementation(libs.jackson)
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

// Task that runs the tests of the different logger implementations under /integration-tests
tasks.register<GradleBuild>("integrationTests") {
  group = "verification"
  tasks =
      listOf(
          ":clean",
          ":spotlessApply",
          ":allTests",
          ":integration-tests:logback:clean",
          ":integration-tests:logback:spotlessApply",
          ":integration-tests:logback:test",
          ":integration-tests:log4j:clean",
          ":integration-tests:log4j:spotlessApply",
          ":integration-tests:log4j:test",
          ":integration-tests:jul:clean",
          ":integration-tests:jul:spotlessApply",
          ":integration-tests:jul:test",
      )
}

spotless {
  kotlin {
    toggleOffOn()
    ktfmt(libs.versions.ktfmt.get())
  }
}

// Use GPG agent for signing Maven Central publication
signing { useGpgCmd() }

// Provides `publishing/publishAllPublicationsToTestRepository` task to check publication output
// before we publish to Maven Central
publishing {
  repositories {
    maven {
      name = "Test"
      url = uri(layout.buildDirectory.dir("testPublication"))
    }
  }
}

tasks.dependencyUpdates {
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
