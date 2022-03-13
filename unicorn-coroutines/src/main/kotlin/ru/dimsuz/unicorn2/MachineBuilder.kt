package ru.dimsuz.unicorn2

fun <S : Any, E : Any> machine(
  init: MachineDsl<S, E>.() -> Unit
): Machine<S, E> {
  val machineDsl = MachineDsl<S, E>()
  machineDsl.init()
  TODO()
//  return buildMachine(
//    MachineConfig.create(
//      machineDsl
//    ),
//  )
}
