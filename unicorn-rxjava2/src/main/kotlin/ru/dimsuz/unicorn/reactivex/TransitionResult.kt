package ru.dimsuz.unicorn.reactivex

import io.reactivex.Single

internal data class TransitionResult<S : Any> internal constructor(
  val state: S,
  val actions: Single<List<Any>>?,
)
