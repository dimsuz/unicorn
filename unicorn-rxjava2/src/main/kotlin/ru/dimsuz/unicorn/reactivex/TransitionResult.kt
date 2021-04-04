package ru.dimsuz.unicorn.reactivex

data class TransitionResult<S : Any>(
  val state: S,
  val actions: (() -> Unit)?
)
