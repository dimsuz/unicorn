tasks.withType<Test> {
  useJUnitPlatform()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("io.reactivex.rxjava2:rxjava:${Versions.rxJava2}")

  testImplementation("io.kotest:kotest-runner-junit5:${Versions.koTest}")
  testImplementation("io.kotest:kotest-assertions-core:${Versions.koTest}")
  testImplementation("io.kotest:kotest-property:${Versions.koTest}")
}
