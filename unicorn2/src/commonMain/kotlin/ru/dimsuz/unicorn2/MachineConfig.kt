/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal data class MachineConfig<S : Any>(
  val initial: Pair<S, (suspend (S) -> Unit)?>,
  val transitions: List<TransitionConfig<S, S>>,
)

@PublishedApi
internal fun <S : Any> createMachineConfig(machineDsl: MachineDsl<S>): MachineConfig<S> {
  val initialStateConfig = machineDsl.initial?.let { initial -> initial.first to initial.second }
    ?: error("initial transition is missing")
  return MachineConfig(
    initialStateConfig,
    machineDsl.transitions.entries.flatMap { (stateClass, value) -> value.map { it.toTransitionConfig(stateClass) } }
  )
}

internal data class TransitionConfig<S : PS, PS : Any>(
  val stateClass: KClass<*>,
  val payloadSource: Flow<Any?>,
  val transition: suspend (PS, Any?) -> PS,
  val action: (suspend (PS, PS, Any?) -> Unit)?,
)

@Suppress("UNCHECKED_CAST") // we know the types here
private fun <S : PS, PS : Any, P> TransitionDsl<S, PS, P>.toTransitionConfig(
  stateClass: KClass<out PS>,
): TransitionConfig<PS, PS> {
  return TransitionConfig(
    stateClass = stateClass,
    payloadSource = eventPayloads,
    transition = { s: PS, p: Any? ->
      // note that by casting "s" to "S" here it is assumed that this transition will only be run when in this
      // sub-state and that this is enforced during flow construction (see MachineBuilder.kt)
      this.transition?.invoke(s as S, p as P) ?: s
    },
    action = action as (suspend (PS, PS, Any?) -> Unit)?
  )
}
