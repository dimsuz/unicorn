package ru.dimsuz.unicorn.reactivex

import io.reactivex.Completable
import io.reactivex.Single

internal data class TransitionResult<S : Any> internal constructor(
  val state: S,
  val actions: Completable?,
  val internalActions: Single<List<Any>>?,
)
