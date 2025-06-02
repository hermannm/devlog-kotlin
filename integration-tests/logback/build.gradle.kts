dependencies {
  implementation(rootProject)
  implementation(libs.logback)
  implementation(libs.logstashLogbackEncoder)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitPlatformEngine)
  testRuntimeOnly(libs.junitPlatformLauncher)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
