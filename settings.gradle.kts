rootProject.name = "devlog-kotlin"

include(
    ":integration-tests:logback",
    ":integration-tests:log4j",
    ":integration-tests:jul",
)

pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}
