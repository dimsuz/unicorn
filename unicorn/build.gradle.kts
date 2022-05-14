import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT

tasks.withType<Test> {
  useJUnitPlatform()

  testLogging {
    showStandardStreams = true
    events = setOf(STANDARD_OUT)
  }
}

dependencies {
  implementation(libs.bundles.coroutines)

  testImplementation(libs.bundles.koTestCommon)
  testImplementation(libs.bundles.koTestJvm)
  testImplementation(libs.turbine)
}
