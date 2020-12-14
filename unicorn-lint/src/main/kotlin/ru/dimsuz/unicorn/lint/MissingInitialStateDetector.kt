package ru.dimsuz.unicorn.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.USimpleNameReferenceExpression

@Suppress("UnstableApiUsage")
class MissingInitialStateDetector : Detector(), SourceCodeScanner {
  companion object {
    @JvmField
    val MISSING_INITIAL_STATE_ISSUE = Issue.create(
      id = "UnicornMissingInitialState",
      briefDescription = "Missing initial state specification",
      explanation = "Missing initial state specification. Add \"initial = ...\" to machine specification",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(
        MissingInitialStateDetector::class.java,
        Scope.JAVA_FILE_SCOPE
      ),
    )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(
      UCallExpression::class.java,
    )
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        if (node.methodIdentifier?.name == "machine" &&
          node.valueArgumentCount == 1 && node.valueArguments.single() is ULambdaExpression
        ) {
          val lambda = node.valueArguments.single() as ULambdaExpression
          val body = lambda.body as UBlockExpression
          if (body.expressions.none {
            it is UBinaryExpression &&
              it.leftOperand is USimpleNameReferenceExpression &&
              (it.leftOperand as USimpleNameReferenceExpression).identifier == "initial"
          }
          ) {
            context.report(
              MISSING_INITIAL_STATE_ISSUE,
              context.getLocation(lambda),
              MISSING_INITIAL_STATE_ISSUE.getExplanation(TextFormat.TEXT),
            )
          }
        }
      }
    }
  }
}
