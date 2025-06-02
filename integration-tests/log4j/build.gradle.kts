dependencies {
  implementation(rootProject)
  runtimeOnly(libs.log4j)
  runtimeOnly(libs.log4jJson)
  runtimeOnly(libs.log4jSlf4j)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
