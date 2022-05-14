/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal data class MachineConfig<S : Any, E : Any>(
  val initial: Pair<suspend () -> S, (suspend (S) -> Unit)?>,
  val transitions: List<TransitionConfig<S, S, E>>,
)

@PublishedApi
internal fun <S : Any, E : Any> createMachineConfig(machineDsl: MachineDsl<S, E>): MachineConfig<S, E> {
  val initialStateConfig = machineDsl.initialLazy
    ?: machineDsl.initial?.let { initial -> suspend { initial.first } to initial.second }
    ?: error("initial transition is missing")
  return MachineConfig(
    initialStateConfig,
    machineDsl.transitions.entries.flatMap { (stateClass, value) -> value.map { it.toTransitionConfig(stateClass) } }
  )
}

internal data class TransitionConfig<S : PS, PS : Any, E : Any>(
  val stateClass: KClass<*>,
  val payloadSource: Flow<Any?>,
  val transition: suspend (PS, Any?) -> PS,
  val action: (suspend (scope: ActionScope<E>, PS, PS, Any?) -> Unit)?,
)

@Suppress("UNCHECKED_CAST") // we know the types here
private fun <S : PS, PS : Any, P, E : Any> TransitionDsl<S, PS, P, E>.toTransitionConfig(
  stateClass: KClass<out PS>,
): TransitionConfig<PS, PS, E> {
  return TransitionConfig(
    stateClass = stateClass,
    payloadSource = eventPayloads,
    transition = { s: PS, p: Any? ->
      // note that by casting "s" to "S" here it is assumed that this transition will only be run when in this
      // sub-state and that this is enforced during flow construction (see MachineBuilder.kt)
      this.transition?.invoke(s as S, p as P) ?: s
    },
    action = action as (suspend (scope: ActionScope<E>, PS, PS, Any?) -> Unit)?
  )
}
