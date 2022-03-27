package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow

internal data class MachineConfig<S : Any, E : Any>(
  val initial: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>,
  val transitions: List<TransitionConfig<S, S, E>>,
) {
  companion object {
    fun <S : Any, E : Any> create(machineDsl: MachineDsl<S, E>): MachineConfig<S, E> {
      val initialStateConfig = machineDsl.initialLazy
        ?: machineDsl.initial?.let { initial -> suspend { initial.first } to initial.second }
        ?: error("initial transition is missing")
      return MachineConfig(
        initialStateConfig,
        machineDsl.transitions.map { it.toTransitionConfig() }
      )
    }
  }
}

internal data class TransitionConfig<S : PS, PS : Any, E : Any>(
  val payloadSource: Flow<Any?>,
  val transition: suspend (S, Any?) -> PS,
  val action: (suspend (scope: ActionScope<E>, S, PS, Any?) -> Unit)?,
)

@Suppress("UNCHECKED_CAST") // we know the types here
private fun <S : PS, PS : Any, P, E : Any> TransitionDsl<S, PS, P, E>.toTransitionConfig(): TransitionConfig<S, PS, E> {
  return TransitionConfig(
    payloadSource = eventPayloads,
    transition = { s : S, p: Any? ->
      this.transition?.invoke(s, p as P) ?: s
    },
    action = action as (suspend (scope: ActionScope<E>, S, PS, Any?) -> Unit)?
  )
}
