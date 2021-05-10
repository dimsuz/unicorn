package ru.dimsuz.unicorn.coroutines

internal data class TransitionResult<S : Any>(
  val state: S,
  val actions: (suspend () -> Unit)?
)
