dependencies {
  implementation(rootProject)
  implementation(libs.slf4jJdk14)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitPlatformEngine)
  testRuntimeOnly(libs.junitPlatformLauncher)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
