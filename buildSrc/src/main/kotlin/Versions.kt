object Versions {
  const val rxJava2 = "2.2.20"
  const val ktLint = "0.43.2"
  const val koTest = "5.1.0"
  const val kotlinCoroutines = "1.6.0"
  const val turbine = "0.7.0"

  private const val agpVersion = "4.1.1"
  // "For historical reasons, your lint version should correspond to the Android Gradle Plugin version + 23"
  val lintVersion = agpVersion.split('.').let { (major, minor, patch) -> "${major.toInt() + 23}.$minor.$patch" }
}
