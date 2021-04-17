tasks.withType<Test> {
  useJUnitPlatform()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinCoroutines}")

  testImplementation("io.kotest:kotest-runner-junit5:${Versions.koTest}")
  testImplementation("io.kotest:kotest-assertions-core:${Versions.koTest}")
  testImplementation("io.kotest:kotest-property:${Versions.koTest}")
  testImplementation("app.cash.turbine:turbine:${Versions.turbine}")
}
