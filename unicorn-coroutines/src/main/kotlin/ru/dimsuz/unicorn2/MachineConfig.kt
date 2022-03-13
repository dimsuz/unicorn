package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal data class MachineConfig<S : Any, E : Any>(
  val initial: suspend () -> S,
  val transitions: List<TransitionConfig<out S, S, E>>
) {
  companion object {
    fun <S : Any, E : Any> create(machineDsl: MachineDsl<S, E>): MachineConfig<S, E> {
      val initialStateConfig = machineDsl.initial
        ?: error("initial transition is missing")
      return MachineConfig(
        initialStateConfig,
        machineDsl.transitions.map { it.toTransitionConfig() }
      )
    }
  }
}

internal data class TransitionConfig<S : PS, PS : Any, E : Any>(
  val eventConfig: EventConfig,
  val transition: suspend (S, Any?) -> PS,
  val nextEvent: (suspend (S, PS, Any?) -> E?)?
) {
  sealed class EventConfig {
    data class Streamed(val payloadSource: Flow<Any?>) : EventConfig()
    data class Discrete(val eventSelector: KClass<*>) : EventConfig()
  }
}

@Suppress("UNCHECKED_CAST") // we know the types here
private fun <S : PS, PS : Any, P, E : Any> TransitionDsl<S, PS, P, E>.toTransitionConfig(): TransitionConfig<S, PS, E> {
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
    transition = { s : S, p: Any? ->
      this.transition?.invoke(s, p as P) ?: s
    },
    nextEvent = this.nextEvent as (suspend (S, PS, Any?) -> E?)?,
  )
}
