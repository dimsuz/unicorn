package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.dimsuz.unicorn.coroutines.machine
import kotlin.reflect.KClass

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class MachineDsl<S : Any, E : Any> {
  var initial: (suspend () -> S)? = null

  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<S, S, Any, E>> = arrayListOf()

  inline fun <P> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, S, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, P, E>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, S, Any, E>)
  }

  inline fun <EE : E> on(eventSelector: KClass<out EE>, init: TransitionDsl<S, S, EE, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, S, EE, E>(eventSelector).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, S, Any, E>)
  }
}

@StateMachineDsl
class TransitionDsl<S : PS, PS : Any, P, E : Any> @PublishedApi internal constructor(
  @PublishedApi internal val eventPayloads: Flow<P>?,
  @PublishedApi internal val eventSelector: KClass<out E>?
) {
  constructor(eventPayloads: Flow<P>) : this(eventPayloads, null)
  constructor(eventSelector: KClass<out E>) : this(null, eventSelector)

  internal var transition: (suspend (S, P) -> PS)? = null
  internal var action: (suspend (S, PS, P) -> Unit)? = null
  internal var nextEvent: (suspend (S, PS, P) -> E?)? = null

  @PublishedApi
  internal var subStateTransitions: MutableMap<KClass<out PS>, TransitionDsl<out PS, PS, P, E>>? = null

  fun transitionTo(body: suspend (state: S, payload: P) -> PS) {
    this.transition = body
  }

  fun action(body: suspend (state: S, newState: PS, payload: P) -> Unit) {
    this.action = body
  }

  fun event(body: suspend (state: S, newState: PS, payload: P) -> E?) {
    nextEvent = body
  }

  inline fun <reified T : PS> whenIn(init: TransitionDsl<T, PS, P, E>.() -> Unit) {
    if (subStateTransitions == null) {
      subStateTransitions = hashMapOf()
    }
    subStateTransitions!![T::class] = TransitionDsl<T, PS, P, E>(eventPayloads, eventSelector).apply(init)
  }
}
