dependencies {
  implementation(rootProject)
  implementation(libs.logback)
  implementation(libs.logstashLogbackEncoder)
  implementation(libs.kotlinxSerialization)

  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotest)
}

tasks.test { useJUnitPlatform() }
