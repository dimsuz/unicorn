package ru.dimsuz.unicorn.reactivex

import io.reactivex.Observable
import kotlin.reflect.KClass

internal data class MachineConfig<S : Any, E : Any>(
  val initial: InitialStateConfig<S>,
  val transitions: List<TransitionConfig<S, E>>
) {
  companion object {
    fun <S : Any, E : Any> create(machineDsl: MachineDsl<S, E>): MachineConfig<S, E> {
      val initialStateConfig = machineDsl.initial
        ?: error("initial state is missing")
      return MachineConfig(
        initialStateConfig,
        machineDsl.transitions.map { it.toTransitionConfig() }
      )
    }
  }
}

typealias InitialStateConfig<S> = Pair<S, (() -> Unit)?>

internal data class TransitionConfig<S : Any, E : Any>(
  val eventConfig: EventConfig,
  val actions: List<(S, S, Any) -> E?>?,
  val reducer: (S, Any) -> S
) {
  sealed class EventConfig {
    data class Streamed(val payloadSource: Observable<*>) : EventConfig()
    data class Discrete(val eventSelector: KClass<*>) : EventConfig()
  }
}

private fun <S : Any, P : Any, E : Any> TransitionDsl<S, P, E>.toTransitionConfig(): TransitionConfig<S, E> {
  val reducer = when {
    reducer != null -> { s: S, p: Any ->
      @Suppress("UNCHECKED_CAST") // we know the type here
      this.reducer!!(s, p as P)
    }
    else -> { s: S, _: Any -> s }
  }
  return TransitionConfig(
    eventConfig = when {
      eventPayloads != null -> TransitionConfig.EventConfig.Streamed(
        eventPayloads
      )
      eventSelector != null -> TransitionConfig.EventConfig.Discrete(
        eventSelector
      )
      else -> error("payloads and selector cannot both be null")
    },
    reducer = reducer,
    actions = buildActions(),
  )
}

private fun <S : Any, P : Any, E : Any> TransitionDsl<S, P, E>.buildActions(): List<(S, S, Any) -> E?>? {
  val actions = mutableListOf<(S, S, Any) -> E?>()
  if (actionBodies != null) {
    actionBodies!!.mapTo(actions) { body ->
      { ps: S, ns: S, p: Any ->
        @Suppress("UNCHECKED_CAST") // we know the type here
        body(ps, ns, p as P)
        null
      }
    }
  }
  if (actionBodiesWithEvent != null) {
    actionBodiesWithEvent!!.mapTo(actions) { body ->
      { ps: S, ns: S, p: Any ->
        @Suppress("UNCHECKED_CAST") // we know the type here
        body(ps, ns, p as P)
      }
    }
  }
  return actions.takeIf { it.isNotEmpty() }
}
