package ru.dimsuz.unicorn.reactivex

import io.reactivex.Observable

interface Machine<S : Any, E : Any> {
  val initial: Pair<S, (() -> Unit)?>
  val states: Observable<S>
  fun send(e: E)
}
