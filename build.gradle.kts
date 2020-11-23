plugins {
  kotlin("jvm") version "1.4.20"
}

group = "ru.dimsuz"
version = "1.0-SNAPSHOT"

allprojects {
  repositories {
    mavenCentral()
    jcenter()
  }
}

val ktlint: Configuration by configurations.creating

dependencies {
  ktlint("com.pinterest:ktlint:${Versions.ktLint}")
}

val outputDir = "${project.buildDir}/reports/ktlint/"
val inputFiles = project.fileTree(mapOf("dir" to ".", "include" to "**/*.kt"))

val ktlintCheck by tasks.creating(JavaExec::class) {
  inputs.files(inputFiles)
  outputs.dir(outputDir)

  description = "Check Kotlin code style."
  classpath = ktlint
  group = "verification"
  main = "com.pinterest.ktlint.Main"
  args = listOf("**/*.kt")
}

val ktlintFormat by tasks.creating(JavaExec::class) {
  inputs.files(inputFiles)
  outputs.dir(outputDir)

  description = "Fix Kotlin code style deviations."
  classpath = ktlint
  group = "verification"
  main = "com.pinterest.ktlint.Main"
  args = listOf("-F", "**/*.kt")
}

subprojects {
  apply(plugin = "kotlin")

  tasks {
    compileKotlin {
      kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
      kotlinOptions.jvmTarget = "1.8"
    }
  }
}
