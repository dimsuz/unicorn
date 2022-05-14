import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper

plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.spotless)
}

allprojects {
  buildscript {
    repositories {
      mavenCentral()
    }
  }
  repositories {
    mavenCentral()
  }
}

subprojects {
  plugins.withType<MavenPublishPlugin> {
    apply(plugin = "org.gradle.signing")

    plugins.withType<KotlinMultiplatformPluginWrapper> {
      apply(plugin = libs.plugins.dokka.get().pluginId)

      val dokkaHtml by tasks.existing(org.jetbrains.dokka.gradle.DokkaTask::class)

      val javadocJar by tasks.registering(Jar::class) {
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Assembles a jar archive containing the Javadoc API documentation."
        archiveClassifier.set("javadoc")
        from(dokkaHtml)
      }

      configure<KotlinMultiplatformExtension> {
        explicitApi()

        jvm {
          mavenPublication {
            artifact(javadocJar.get())
          }
        }

        ios()
      }
    }

    configure<PublishingExtension> {
      repositories {
        maven {
          name = "MavenCentral"
          val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
          val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
          val versionName: String by project
          url = if (versionName.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
          credentials {
            username = project.property("NEXUS_USERNAME")?.toString()
            password = project.property("NEXUS_PASSWORD")?.toString()
          }
        }
      }

      publications.withType<MavenPublication> {
        val versionName: String by project
        val pomGroupId: String by project
        groupId = pomGroupId
        version = versionName
        pom {
          val pomDescription: String by project
          val pomUrl: String by project
          val pomName: String by project
          description.set(pomDescription)
          url.set(pomUrl)
          name.set(pomName)
          scm {
            val pomScmUrl: String by project
            val pomScmConnection: String by project
            val pomScmDevConnection: String by project
            url.set(pomScmUrl)
            connection.set(pomScmConnection)
            developerConnection.set(pomScmDevConnection)
          }
          licenses {
            license {
              val pomLicenseName: String by project
              val pomLicenseUrl: String by project
              val pomLicenseDist: String by project
              name.set(pomLicenseName)
              url.set(pomLicenseUrl)
              distribution.set(pomLicenseDist)
            }
          }
          developers {
            developer {
              val pomDeveloperId: String by project
              val pomDeveloperName: String by project
              id.set(pomDeveloperId)
              name.set(pomDeveloperName)
            }
          }
        }
      }

      configure<SigningExtension> {
        sign(publications)
      }
    }
  }
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("!**/build/**/*.*")
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2", "max_line_length" to "120"))
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint(libs.versions.ktlint.get()).userData(mapOf("indent_size" to "2", "max_line_length" to "120"))
    trimTrailingWhitespace()
    endWithNewline()
  }
}
