import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost
import java.net.URI
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

  configure(
      KotlinMultiplatform(
          javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationJavadoc"),
          sourcesJar = true,
      ),
  )
  signAllPublications()
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
}

// Dependency versions are declared in Gradle version catalog (./gradle/libs.versions.toml)
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinxSerialization)
  alias(libs.plugins.spotless)
  alias(libs.plugins.gradleMavenPublish)
  alias(libs.plugins.binaryCompatibilityValidator)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokkaJavadoc)
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
    commonMain.dependencies {
      implementation(libs.kotlinxSerialization)
      implementation(libs.kotlinReflect)
    }
    commonTest.dependencies {
      implementation(libs.kotlinTest)
      implementation(libs.kotest)
    }
    jvmMain.dependencies {
      implementation(libs.slf4j)
      implementation(libs.jackson)
      compileOnly(libs.logback)
      compileOnly(libs.logstashLogbackEncoder)
    }
    jvmTest.dependencies {
      runtimeOnly(libs.logback)
      runtimeOnly(libs.logstashLogbackEncoder)
      runtimeOnly(libs.jackson)
    }
  }

  jvm {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_1_8
      // Needed due to https://youtrack.jetbrains.com/issue/KT-49746
      freeCompilerArgs.add("-Xjdk-release=1.8")
    }

    // To use JUnit 5 rather than JUnit 4:
    // https://kotlinlang.org/docs/gradle-configure-project.html#jvm-variants-of-kotlin-test
    testRuns["test"].executionTask.configure { useJUnitPlatform() }
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

  // Workaround to support single-target multiplatform projects in Dokka:
  // https://github.com/Kotlin/dokka/issues/3122
  if (gradle.startParameter.taskNames.contains("dokkaGeneratePublicationHtml")) {
    linuxX64()
  }
}

repositories { mavenCentral() }

// Task that runs the tests of the different logger implementations under /integration-tests
tasks.register<GradleBuild>("integrationTests") {
  group = "verification"
  tasks =
      listOf(
          ":check",
          ":integration-tests:logback:check",
          ":integration-tests:log4j:check",
          ":integration-tests:jul:check",
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

tasks.withType<Jar> {
  // Copy license into META-INF in output jars
  metaInf.with(copySpec().from("${project.rootDir}/LICENSE"))

  // Add Automatic-Module-Name to META-INF/MANIFEST for better Java module interop:
  // https://dev.java/learn/modules/automatic-module/
  manifest.attributes("Automatic-Module-Name" to "dev.hermannm.devlog")
}

// Provides `publishing/publishAllPublicationsToLocalTestRepository` task to check publication
// output before we publish to Maven Central
publishing {
  repositories {
    maven {
      name = "LocalTest"
      url = uri(layout.buildDirectory.dir("testPublication"))
    }
  }
}

// We only want to use the binary compatibility validator on the main library, not our
// integration test sub-projects
apiValidation { ignoredProjects.addAll(subprojects.map { it.name }) }

dokka {
  dokkaSourceSets.configureEach {
    // Embeds this Markdown file on the module documentation page
    includes.from("gradle/dokka-module-docs.md")

    sourceLink {
      // Links to the Git tag for the current version of the library
      remoteUrl = URI("https://github.com/hermannm/devlog-kotlin/tree/v${rootProject.version}/src")
      remoteLineSuffix = "#L"
      localDirectory = projectDir.resolve("src")
    }

    externalDocumentationLinks.register("kotlinx.serialization") {
      url = URI("https://kotlinlang.org/api/kotlinx.serialization/")
      packageListUrl = URI("https://kotlinlang.org/api/kotlinx.serialization/package-list")
    }
  }

  pluginsConfiguration.html {
    homepageLink = "https://hermannm.dev/devlog"
    footerMessage =
        """Developed by Hermann Mørkrid (<a href="https://hermannm.dev" style="color: inherit; text-decoration: underline">https://hermannm.dev</a>). Licensed under <a href="https://github.com/hermannm/devlog-kotlin/blob/main/LICENSE" style="color: inherit; text-decoration: underline">MIT</a>."""
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
