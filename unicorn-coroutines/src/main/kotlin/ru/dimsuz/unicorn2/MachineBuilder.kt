package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

fun <S : Any, E : Any> machine(
  init: MachineDsl<S, E>.() -> Unit
): Machine<S, E> {
  val events = MutableSharedFlow<E>()
  val machineDsl = MachineDsl<S, E>(events).apply(init)
  return buildMachine(
    MachineConfig.create(
      machineDsl
    ),
    events,
  )
}

private fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, E>,
  eventFlow: MutableSharedFlow<E>,
): Machine<S, E> {
  return object : Machine<S, E> {
    private val actionScope = object : ActionScope<E> {
      override fun sendEvent(event: E) {
        eventFlow.tryEmit(event)
      }
    }

    override val initial: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?> = machineConfig.initial

    override val states: Flow<S> get() = buildStatesFlow(machineConfig, actionScope)

    override suspend fun send(e: E) {
      eventFlow.emit(e)
    }

    override fun trySend(e: E): Boolean {
      return eventFlow.tryEmit(e)
    }
  }
}

private fun <S : Any, E : Any> buildStatesFlow(
  machineConfig: MachineConfig<S, E>,
  actionScope: ActionScope<E>,
): Flow<S> {
  return machineConfig.transitions
    .map { transitionConfig ->
      transitionConfig.payloadSource.map { payload -> payload to transitionConfig }
    }
    .merge()
    .runningFold(
      suspend { buildInitialState(machineConfig.initial, actionScope) },
      ::produceResult,
    )
    .transform { result ->
      emit(result.state)
      result.actions?.invoke()
    }
}

/**
 * A copy of runningFold implementation with the "lazy" initial value
 */
fun <T, R> Flow<T>.runningFold(initial: suspend () -> R, operation: suspend (accumulator: R, value: T) -> R): Flow<R> {
  return flow {
    var accumulator: R = initial()
    emit(accumulator)
    collect { value ->
      accumulator = operation(accumulator, value)
      emit(accumulator)
    }
  }
}

private suspend fun <S : Any, E : Any> produceResult(
  stateBundle: TransitionResult<S>,
  payloadBundle: Pair<Any?, TransitionConfig<S, S, E>>,
): TransitionResult<S> {
  val payload = payloadBundle.first
  val previousState = stateBundle.state
  val nextState = payloadBundle.second.transition(previousState, payload)
//  val nextAction = payloadBundle.second.reduceActions(
//    previousState,
//    nextState,
//    payload,
//    discreteEventFlow
//  )
  return TransitionResult(nextState, null)
}

//private fun <S : Any, E : Any> TransitionConfig<S, S, E>.reduceActions(
//  previousState: S,
//  newState: S,
//  payload: Any?,
//  discreteEventFlow: MutableSharedFlow<Any>
//): (suspend () -> Unit)? {
//  return actions?.let { list ->
//    {
//      list.forEach { body ->
//        val event = body(previousState, newState, payload)
//        if (event != null) {
//          discreteEventFlow.emit(event)
//        }
//      }
//    }
//  }
//}

private suspend fun <S : Any, E : Any> buildInitialState(
  config: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>,
  actionScope: ActionScope<E>,
): TransitionResult<S> {
  val s = config.first()
  return TransitionResult(
    state = s,
    actions = { config.second?.invoke(actionScope, s) }
  )
}

internal data class TransitionResult<S : Any>(
  val state: S,
  val actions: (suspend () -> Unit)?
)
