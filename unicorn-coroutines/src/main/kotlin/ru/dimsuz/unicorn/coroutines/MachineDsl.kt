package ru.dimsuz.unicorn.coroutines

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class MachineDsl<S : Any, E : Any> {
  var initial: Pair<S, (suspend () -> Unit)?>? = null
  @PublishedApi
  internal val transitions: MutableList<TransitionDsl<S, Any, E>> = arrayListOf()

  inline fun <P : Any> onEach(eventPayloads: Flow<P>, init: TransitionDsl<S, P, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, P, E>(eventPayloads).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, Any, E>)
  }

  inline fun <EE : E> on(eventSelector: KClass<out EE>, init: TransitionDsl<S, EE, E>.() -> Unit) {
    val transitionDsl = TransitionDsl<S, EE, E>(eventSelector).apply(init)
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, Any, E>)
  }
}

@StateMachineDsl
class TransitionDsl<S : Any, P : Any, E : Any> private constructor(
  internal val eventPayloads: Flow<P>?,
  internal val eventSelector: KClass<out E>?
) {
  constructor(eventPayloads: Flow<P>) : this(eventPayloads, null)
  constructor(eventSelector: KClass<out E>) : this(null, eventSelector)

  internal var reducer: ((S, P) -> S)? = null
  internal var actionBodies: MutableList<(S, S, P) -> Unit>? = null
  internal var actionBodiesWithEvent: MutableList<(S, S, P) -> E?>? = null

  fun transitionTo(reducer: (state: S, payload: P) -> S) {
    this.reducer = reducer
  }

  fun action(body: (state: S, newState: S, payload: P) -> Unit) {
    if (actionBodies == null) actionBodies = arrayListOf()
    actionBodies!!.add(body)
  }

  fun actionWithEvent(body: (state: S, newState: S, payload: P) -> E?) {
    if (actionBodiesWithEvent == null) actionBodiesWithEvent = arrayListOf()
    actionBodiesWithEvent!!.add(body)
  }
}
