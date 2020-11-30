package ru.dimsuz.unicorn.reactivex

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import kotlin.reflect.KClass

@DslMarker
annotation class StateMachineDsl

@StateMachineDsl
class MachineDsl<S : Any, E : Any> {
  var initial: Pair<S, (() -> Unit)?>? = null
  internal val transitions: MutableList<TransitionDsl<S, Any, E>> = arrayListOf()

  fun <P : Any> onEach(eventPayloads: Observable<P>, init: TransitionDsl<S, P, E>.() -> Unit) {
    val transitionDsl =
      TransitionDsl<S, P, E>(eventPayloads)
    transitionDsl.init()
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, Any, E>)
  }

  fun <EE : E> on(eventSelector: KClass<out EE>, init: TransitionDsl<S, EE, E>.() -> Unit) {
    val transitionDsl =
      TransitionDsl<S, EE, E>(eventSelector)
    transitionDsl.init()
    @Suppress("UNCHECKED_CAST") // we know the types here, enforced by dsl
    transitions.add(transitionDsl as TransitionDsl<S, Any, E>)
  }
}

@StateMachineDsl
class TransitionDsl<S : Any, P : Any, E : Any> private constructor(
  internal val eventPayloads: Observable<P>?,
  internal val eventSelector: KClass<out E>?
) {
  constructor(eventPayloads: Observable<P>) : this(eventPayloads, null)
  constructor(eventSelector: KClass<out E>) : this(null, eventSelector)

  internal var reducer: ((S, P) -> S)? = null
  internal var actionBodies: MutableList<(S, S, P) -> Unit>? = null
  internal var actionBodiesWithEvent: MutableList<(S, S, P) -> E>? = null
  internal var actionBodiesDeferred: MutableList<(S, S, P) -> Completable>? = null
  internal var actionBodiesWithEventDeferred: MutableList<(S, S, P) -> Single<E>>? = null

  fun transitionTo(reducer: (state: S, payload: P) -> S) {
    this.reducer = reducer
  }

  fun action(body: (previousState: S, newState: S, payload: P) -> Unit) {
    if (actionBodies == null) actionBodies = arrayListOf()
    actionBodies!!.add(body)
  }

  fun actionWithEvent(body: (previousState: S, newState: S, payload: P) -> E) {
    if (actionBodiesWithEvent == null) actionBodiesWithEvent = arrayListOf()
    actionBodiesWithEvent!!.add(body)
  }

  fun actionDeferred(body: (previousState: S, newState: S, payload: P) -> Completable) {
    if (actionBodiesDeferred == null) actionBodiesDeferred = arrayListOf()
    actionBodiesDeferred!!.add(body)
  }

  fun actionWithEventDeferred(body: (previousState: S, newState: S, payload: P) -> Single<E>) {
    if (actionBodiesWithEventDeferred == null) actionBodiesWithEventDeferred = arrayListOf()
    actionBodiesWithEventDeferred!!.add(body)
  }
}

fun <S : Any, E : Any> machine(init: MachineDsl<S, E>.() -> Unit): Machine<S, E> {
  val machineDsl = MachineDsl<S, E>()
  machineDsl.init()
  return buildMachine(
    MachineConfig.create(
      machineDsl
    )
  )
}

typealias TransitionResult<S> = Pair<S, Completable?>
