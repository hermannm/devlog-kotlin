dependencies {
  implementation(rootProject)
  implementation(libs.slf4jJdk14)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
