package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

fun <S : Any, E : Any> machine(
  init: MachineDsl<S, E>.() -> Unit
): Machine<S, E> {
  val machineDsl = MachineDsl<S, E>().apply(init)
  return buildMachine(
    MachineConfig.create(
      machineDsl
    ),
  )
}

private fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, E>,
): Machine<S, E> {
  return object : Machine<S, E> {
    override val initial: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?> = machineConfig.initial

    val discreteEventFlow = MutableSharedFlow<Any>()

    override val states: Flow<S>
      get() = TODO()

    override suspend fun send(e: E) {
      discreteEventFlow.emit(e)
    }

    override fun trySend(e: E): Boolean {
      return discreteEventFlow.tryEmit(e)
    }
  }
}
