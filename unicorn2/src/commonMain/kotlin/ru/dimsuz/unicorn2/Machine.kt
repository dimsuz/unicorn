/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow

public interface Machine<S : Any, E : Any> {
  public val initial: Pair<suspend () -> S, (suspend (S) -> Unit)?>
  public val states: Flow<S>

  /**
   * Sends event [e] to the machine, suspending in case event buffer is overflown.
   * See [trySend] for a non-suspending variant
   */
  public suspend fun send(e: E)

  /**
   * Tries to send an event [e] to the machine and returns `false` if calling [send]
   * instead of [trySend] would suspend
   */
  public fun trySend(e: E): Boolean
}
