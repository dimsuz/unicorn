package ru.dimsuz.unicorn.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class IssueRegistry : IssueRegistry() {
  override val issues: List<Issue> = listOf(
    MissingInitialStateDetector.MISSING_INITIAL_STATE_ISSUE
  )
}
