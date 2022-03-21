package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow

interface Machine<S : Any, E : Any> {
  val initial: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>
  val states: Flow<S>

  /**
   * Sends event [e] to the machine, suspending in case event buffer is overflown.
   * See [trySend] for a non-suspending variant
   */
  suspend fun send(e: E)

  /**
   * Tries to send an event [e] to the machine and returns `false` if calling [send]
   * instead of [trySend] would suspend
   */
  fun trySend(e: E): Boolean
}
