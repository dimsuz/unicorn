package ru.dimsuz.unicorn.reactivex

import io.reactivex.Observable

interface Machine<S : Any, E : Any> {
  val transitionStream: Observable<TransitionResult<S>>
  fun send(e: E)
}
