dependencies {
  implementation(rootProject)
  runtimeOnly(libs.log4j)
  runtimeOnly(libs.log4jJson)
  runtimeOnly(libs.log4jSlf4j)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitPlatformEngine)
  testRuntimeOnly(libs.junitPlatformLauncher)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
