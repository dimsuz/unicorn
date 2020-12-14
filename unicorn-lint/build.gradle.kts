repositories {
  google()
}

tasks.withType<Test> {
  useJUnitPlatform()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(project(":unicorn-rxjava2"))
  implementation("com.android.tools.lint:lint-api:${Versions.lintVersion}")

  testImplementation("io.kotest:kotest-runner-junit5:${Versions.koTest}")
  testImplementation("io.kotest:kotest-assertions-core:${Versions.koTest}")
  testImplementation("io.kotest:kotest-property:${Versions.koTest}")
  testImplementation("com.android.tools.lint:lint-tests:${Versions.lintVersion}")

//  val lintChecks = "com.android.tools.lint:lint-checks:$lintVersion"
//  val lint = "com.android.tools.lint:lint:$lintVersion"
}
