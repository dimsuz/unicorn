package ru.dimsuz.unicorn.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import io.kotest.core.spec.style.ShouldSpec

@Suppress("UnstableApiUsage")
class MissingInitialStateDetectorTest : ShouldSpec({
  should("report missing initial state when function has type args") {
    lint()
      .allowMissingSdk()
      .files(
        kotlin(
          //language=kotlin
          """
            import ru.dimsuz.unicorn.reactivex.machine
            fun main() {
              val machine = machine<Unit, Unit> { 
              }
            }
          """
        ).indented()
      )
      .issues(MissingInitialStateDetector.MISSING_INITIAL_STATE_ISSUE)
      .run()
      .expect(
        """
        |src/test.kt:3: Error: Missing initial state specification. Add "initial = ..." to machine specification [UnicornMissingInitialState]
        |  val machine = machine<Unit, Unit> { 
        |                                    ^
        |1 errors, 0 warnings
        """.trimMargin()
      )
  }

  should("report missing initial state when function has no type args") {
    lint()
      .allowMissingSdk()
      .files(
        kotlin(
          //language=kotlin
          """
            import ru.dimsuz.unicorn.reactivex.Machine
            import ru.dimsuz.unicorn.reactivex.machine
            fun main() {
              val machine: Machine<Unit, Unit> = machine { 
              }
            }
          """
        ).indented()
      )
      .issues(MissingInitialStateDetector.MISSING_INITIAL_STATE_ISSUE)
      .run()
      .expect(
        """
        |src/test.kt:4: Error: Missing initial state specification. Add "initial = ..." to machine specification [UnicornMissingInitialState]
        |  val machine: Machine<Unit, Unit> = machine { 
        |                                             ^
        |1 errors, 0 warnings
        """.trimMargin()
      )
  }

  should("give no error if initial state is specified") {
    lint()
      .allowMissingSdk()
      .files(
        kotlin(
          //language=kotlin
          """
            import ru.dimsuz.unicorn.reactivex.machine
            fun main() {
              val machine = machine<Unit, Unit> { 

                initial = Unit to null
              }
            }
          """
        ).indented()
      )
      .issues(MissingInitialStateDetector.MISSING_INITIAL_STATE_ISSUE)
      .run()
      .expectClean()
  }
})
