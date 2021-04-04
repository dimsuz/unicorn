package ru.dimsuz.unicorn.coroutines

data class TransitionResult<S : Any>(
  val state: S,
  val actions: (suspend () -> Unit)
)
