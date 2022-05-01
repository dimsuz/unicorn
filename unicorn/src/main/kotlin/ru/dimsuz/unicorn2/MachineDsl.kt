/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class MachineDsl<S : Any, E : Any> @PublishedApi internal constructor(
  val events: Flow<E>,
  @PublishedApi internal val klass: KClass<out S>
) {
  var initial: Pair<S, (suspend ActionScope<E>.(S) -> Unit)?>? = null
  var initialLazy: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>? = null

  @PublishedApi
  internal val transitions: MutableMap<KClass<out S>, MutableList<TransitionDsl<out S, S, Any, E>>> = mutableMapOf()

  inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, S, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, P, E>(eventPayloads).apply(init)
    val list = transitions.getOrPut(klass) { arrayListOf() }
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    list += transitionDsl as TransitionDsl<S, S, Any, E>
  }

  inline fun <reified T : S> whenIn(init: StateDsl<T, S, E>.() -> Unit) {
    val stateDsl = StateDsl<T, S, E>(events).apply(init)
    val list = transitions.getOrPut(T::class) { arrayListOf() }
    list += stateDsl.transitions
  }
}

@StateMachineDsl
class StateDsl<S : PS, PS : Any, E : Any>(val events: Flow<E>) {
  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<out PS, PS, Any, E>> = arrayListOf()

  inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, PS, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, PS, P, E>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, PS, Any, E>)
  }
}

@StateMachineDsl
class TransitionDsl<S : PS, PS : Any, P, E : Any> @PublishedApi internal constructor(
  @PublishedApi internal val eventPayloads: Flow<P>,
) {

  internal var transition: (suspend (S, P) -> PS)? = null
  internal var action: (suspend (scope: ActionScope<E>, S, PS, P) -> Unit)? = null

  fun transitionTo(body: suspend (state: S, payload: P) -> PS) {
    this.transition = body
  }

  fun action(body: suspend ActionScope<E>.(state: S, newState: PS, payload: P) -> Unit) {
    this.action = body
  }
}

interface ActionScope<E : Any> {
  fun sendEvent(event: E)
}
