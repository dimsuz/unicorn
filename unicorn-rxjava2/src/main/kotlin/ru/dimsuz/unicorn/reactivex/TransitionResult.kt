package ru.dimsuz.unicorn.reactivex

data class TransitionResult<S : Any> internal constructor(
  val state: S,
  val actions: (() -> Unit)?,
  internal val internalActions: (() -> List<Any>)? = null
)
