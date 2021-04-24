package ru.dimsuz.unicorn.reactivex

import io.reactivex.Observable

interface Machine<S : Any, E : Any> {
  val states: Observable<S>
  fun send(e: E)
}
