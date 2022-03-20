package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.reflect.KClass

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class MachineDsl<S : Any, E : Any> {
  var initial: Pair<S, (suspend ActionScope<E>.(S) -> Unit)?>? = null
  var initialLazy: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>? = null

  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<S, S, Any, E>> = arrayListOf()

  val events: Flow<E> = MutableSharedFlow()

  inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, S, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, P, E>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, S, Any, E>)
  }
}

@StateMachineDsl
class TransitionDsl<S : PS, PS : Any, P, E : Any> @PublishedApi internal constructor(
  @PublishedApi internal val eventPayloads: Flow<P>,
) {

  internal var transition: (suspend (S, P) -> PS)? = null
  internal var action: (suspend (scope: ActionScope<E>, S, PS, P) -> Unit)? = null

  @PublishedApi
  internal var subStateTransitions: MutableMap<KClass<out PS>, TransitionDsl<out PS, PS, P, E>>? = null

  fun transitionTo(body: suspend (state: S, payload: P) -> PS) {
    this.transition = body
  }

  fun action(body: suspend ActionScope<E>.(state: S, newState: PS, payload: P) -> Unit) {
    this.action = body
  }

  inline fun <reified T : PS> whenIn(init: TransitionDsl<T, PS, P, E>.() -> Unit) {
    if (subStateTransitions == null) {
      subStateTransitions = hashMapOf()
    }
    subStateTransitions!![T::class] = TransitionDsl<T, PS, P, E>(eventPayloads).apply(init)
  }
}

interface ActionScope<E : Any> {
  fun sendEvent(event: E)
}
