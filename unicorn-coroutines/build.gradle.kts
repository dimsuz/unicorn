import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT

tasks.withType<Test> {
  useJUnitPlatform()

  testLogging {
    showStandardStreams = true
    events = setOf(STANDARD_OUT)
  }
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}")

  testImplementation("io.kotest:kotest-runner-junit5:${Versions.koTest}")
  testImplementation("io.kotest:kotest-assertions-core:${Versions.koTest}")
  testImplementation("io.kotest:kotest-property:${Versions.koTest}")
  testImplementation("app.cash.turbine:turbine:${Versions.turbine}")
}
