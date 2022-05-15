/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@DslMarker
public annotation class StateMachineDsl

@StateMachineDsl
public class MachineDsl<S : Any> @PublishedApi internal constructor(
  @PublishedApi internal val klass: KClass<out S>
) {
  public var initial: Pair<S, (suspend (S) -> Unit)?>? = null
  public var initialLazy: Pair<suspend () -> S, (suspend (S) -> Unit)?>? = null

  @PublishedApi
  internal val transitions: MutableMap<KClass<out S>, MutableList<TransitionDsl<out S, S, Any>>> = mutableMapOf()

  public inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, S, P>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, P>(eventPayloads).apply(init)
    val list = transitions.getOrPut(klass) { arrayListOf() }
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    list += transitionDsl as TransitionDsl<S, S, Any>
  }

  public inline fun <reified T : S> whenIn(init: StateDsl<T, S>.() -> Unit) {
    val stateDsl = StateDsl<T, S>().apply(init)
    val list = transitions.getOrPut(T::class) { arrayListOf() }
    list += stateDsl.transitions
  }
}

@StateMachineDsl
public class StateDsl<S : PS, PS : Any> @PublishedApi internal constructor() {
  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<out PS, PS, Any>> = arrayListOf()

  public inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, PS, P>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, PS, P>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, PS, Any>)
  }
}

@StateMachineDsl
public class TransitionDsl<S : PS, PS : Any, P> @PublishedApi internal constructor(
  @PublishedApi internal val eventPayloads: Flow<P>,
) {

  internal var transition: (suspend (S, P) -> PS)? = null
  internal var action: (suspend (S, PS, P) -> Unit)? = null

  public fun transitionTo(body: suspend (state: S, payload: P) -> PS) {
    this.transition = body
  }

  public fun action(body: suspend (state: S, newState: PS, payload: P) -> Unit) {
    this.action = body
  }
}
