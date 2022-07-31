/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow

public interface Machine<S : Any> {
  public val initial: Pair<S, (suspend (S) -> Unit)?>
  public val states: Flow<S>
}
