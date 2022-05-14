/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@DslMarker
public annotation class StateMachineDsl

@StateMachineDsl
public class MachineDsl<S : Any, E : Any> @PublishedApi internal constructor(
  public val events: Flow<E>,
  @PublishedApi internal val klass: KClass<out S>
) {
  public var initial: Pair<S, (suspend (S) -> Unit)?>? = null
  public var initialLazy: Pair<suspend () -> S, (suspend (S) -> Unit)?>? = null

  @PublishedApi
  internal val transitions: MutableMap<KClass<out S>, MutableList<TransitionDsl<out S, S, Any, E>>> = mutableMapOf()

  public inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, S, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, P, E>(eventPayloads).apply(init)
    val list = transitions.getOrPut(klass) { arrayListOf() }
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    list += transitionDsl as TransitionDsl<S, S, Any, E>
  }

  public inline fun <reified T : S> whenIn(init: StateDsl<T, S, E>.() -> Unit) {
    val stateDsl = StateDsl<T, S, E>(events).apply(init)
    val list = transitions.getOrPut(T::class) { arrayListOf() }
    list += stateDsl.transitions
  }
}

@StateMachineDsl
public class StateDsl<S : PS, PS : Any, E : Any>(public val events: Flow<E>) {
  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<out PS, PS, Any, E>> = arrayListOf()

  public inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, PS, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, PS, P, E>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, PS, Any, E>)
  }
}

@StateMachineDsl
public class TransitionDsl<S : PS, PS : Any, P, E : Any> @PublishedApi internal constructor(
  @PublishedApi internal val eventPayloads: Flow<P>,
) {

  internal var transition: (suspend (S, P) -> PS)? = null
  internal var action: (suspend (scope: ActionScope<E>, S, PS, P) -> Unit)? = null

  public fun transitionTo(body: suspend (state: S, payload: P) -> PS) {
    this.transition = body
  }

  public fun action(body: suspend ActionScope<E>.(state: S, newState: PS, payload: P) -> Unit) {
    this.action = body
  }
}

public interface ActionScope<E : Any> {
  public fun sendEvent(event: E)
}
