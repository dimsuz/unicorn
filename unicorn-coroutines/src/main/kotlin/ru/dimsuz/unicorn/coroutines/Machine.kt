package ru.dimsuz.unicorn.coroutines

import kotlinx.coroutines.flow.Flow

interface Machine<S : Any, E : Any> {
  val transitionStream: Flow<TransitionResult<S>>
  fun send(e: E)
}
