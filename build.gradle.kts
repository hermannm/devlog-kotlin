import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import java.net.URI
import nl.littlerobots.vcu.plugin.versionSelector
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "dev.hermannm"

version = "0.8.0"

mavenPublishing {
  coordinates(group.toString(), artifactId = "devlog-kotlin", version.toString())

  pom {
    name = "devlog-kotlin"
    description =
        "Structured logging library for Kotlin, that aims to provide a developer-friendly API with minimal runtime overhead."
    url = "https://devlog-kotlin.hermannm.dev"
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
  publishToMavenCentral(automaticRelease = false)
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
  alias(libs.plugins.versionCatalogUpdate)
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
      jvm {
        compilerOptions {
          jvmTarget = JvmTarget.JVM_1_8
          // We set this in addition to jvmTarget, as it gives us some additional verification that
          // we don't use more modern JDK features:
          // https://kotlinlang.org/docs/compiler-reference.html#xjdk-release-version
          freeCompilerArgs.add("-Xjdk-release=1.8")
        }
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlinTest)
        implementation(libs.kotest)
      }
      // kotest 6 requires JDK 11
      jvm {
        compilerOptions {
          jvmTarget = JvmTarget.JVM_11
          freeCompilerArgs.add("-Xjdk-release=11")
        }
      }
    }

    jvmMain.dependencies {
      implementation(libs.slf4j)
      implementation(libs.jackson)
      // Optional dependency: This library works for any SLF4J logger implementation, but makes some
      // optimizations for Logback. If the user chooses Logback as their logger implementation, we
      // can apply these optimizations, but if they don't, then we don't want to load Logback, as
      // that can interfere with other SLF4J logger implementations on the classpath.
      compileOnly(libs.logback)
      // Optional dependency - we only need this if the user:
      // - Has chosen Logback as their logger implementation
      // - Uses logstash-logback-encoder for encoding logs as JSON
      // - Wants to use our JsonContextFieldWriter
      compileOnly(libs.logstashLogbackEncoder)
    }
    jvmTest.dependencies {
      runtimeOnly(libs.logback)
      runtimeOnly(libs.logstashLogbackEncoder)
    }
  }

  jvm {
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
  if (
      // To match both dokkaGeneratePublicationHtml and :dokkaGeneratePublicationHtml, both valid
      gradle.startParameter.taskNames.any { taskName ->
        taskName.endsWith("dokkaGeneratePublicationHtml")
      }
  ) {
    linuxX64()
  }
}

repositories { mavenCentral() }

spotless {
  kotlin {
    target("src/**/*.kt", "integration-tests/**/*.kt")
    // Check for new versions here here: https://github.com/facebook/ktfmt
    ktfmt("0.59")
    toggleOffOn()
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
    includes.from("gradle/dokka/module-docs.md")

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

    // Require docstrings for all public APIs
    reportUndocumented = true
  }

  pluginsConfiguration.html {
    homepageLink = "https://github.com/hermannm/devlog-kotlin"
    footerMessage =
        """Developed by Hermann Mørkrid (<a href="https://hermannm.dev" target="_blank" style="color: inherit; text-decoration: underline">https://hermannm.dev</a>). Licensed under <a href="https://github.com/hermannm/devlog-kotlin/blob/main/LICENSE" target="_blank" style="color: inherit; text-decoration: underline">MIT</a>."""
    // Overrides the default homepage link icon with a GitHub icon
    customAssets.from("gradle/dokka/github-icon.svg")
    customStyleSheets.from("gradle/dokka/custom-styles.css")
  }

  dokkaPublications.html { failOnWarning = true }
}

versionCatalogUpdate {
  sortByKey = false // We order dependencies by importance in `libs.versions.toml`

  versionSelector { module ->
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
    return@versionSelector invalidVersionRegexes.none { it.matches(module.candidate.version) }
  }
}
