package ru.dimsuz.unicorn.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, *>
): Machine<S, E> {
  return object : Machine<S, E> {
    override val transitionStream: Flow<TransitionResult<S>>
      get() = flowOf(buildInitialState(machineConfig.initial))

    override fun send(e: E) {
    }
  }
}

private fun <S : Any> buildInitialState(config: InitialStateConfig<S>): TransitionResult<S> {
  return TransitionResult(
    state = config.first,
    actions = config.second
  )
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
