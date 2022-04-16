package ru.dimsuz.unicorn2

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

inline fun <reified S : Any, E : Any> machine(
  init: MachineDsl<S, E>.() -> Unit
): Machine<S, E> {
  val events = MutableSharedFlow<E>(extraBufferCapacity = 12)
  val machineDsl = MachineDsl(events, S::class).apply(init)
  return buildMachine(
    createMachineConfig(
      machineDsl
    ),
    events,
  )
}

@PublishedApi
internal fun <S : Any, E : Any> buildMachine(
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

// TODO remove before release
private fun <S : Any, E : Any> buildStatesFlowLegacy(
  machineConfig: MachineConfig<S, E>,
  actionScope: ActionScope<E>,
): Flow<S> {
  return machineConfig.transitions
    .map { config ->
      config.payloadSource.map { payload -> payload to config }
    }
    .merge()
    .runningFold(
      { buildInitialState(machineConfig.initial, actionScope) }
    ) { accumulator, value -> produceResult(accumulator, value, actionScope) }
    .transform { result ->
      emit(result.state)
      result.action?.invoke()
    }
}

private fun <S : Any, E : Any> buildStatesFlow(
  machineConfig: MachineConfig<S, E>,
  actionScope: ActionScope<E>
): Flow<S> {
  return flow {
    var transitionResult = buildInitialState(machineConfig.initial, actionScope)
    emit(transitionResult.state)
    transitionResult.action?.invoke()
    machineConfig
      .transitions
      .map { config -> config.payloadSource.map { payload -> payload to config } }
      .merge()
      .collect { payloadBundle ->
        val result = produceResult(transitionResult, payloadBundle, actionScope)
        if (result != null) {
          val previousResult = transitionResult
          transitionResult = result
          if (previousResult.state != result.state) {
            emit(result.state)
          }
          result.action?.invoke()
        }
      }
  }
}

/**
 * A copy of runningFold implementation with the "lazy" initial value and skipping null values
 */
fun <T, R> Flow<T>.runningFold(initial: suspend () -> R, operation: suspend (accumulator: R, value: T) -> R?): Flow<R> {
  return flow {
    var accumulator: R = initial()
    emit(accumulator)
    collect { value ->
      val result = operation(accumulator, value)
      if (result != null) {
        accumulator = result
        emit(accumulator)
      }
    }
  }
}

private suspend fun <S : Any, E : Any> produceResult(
  stateBundle: TransitionResult<S>,
  payloadBundle: Pair<Any?, TransitionConfig<S, S, E>>,
  actionScope: ActionScope<E>,
): TransitionResult<S>? {
  val (payload, transitionConfig) = payloadBundle
  val previousState = stateBundle.state
  if (!transitionConfig.stateClass.isInstance(previousState)) {
    return null
  }
  val nextState = transitionConfig.transition(previousState, payload)
  val action = transitionConfig.action?.let {
    suspend { it(actionScope, previousState, nextState, payload) }
  }
  return TransitionResult(nextState, action)
}

private suspend fun <S : Any, E : Any> buildInitialState(
  config: Pair<suspend () -> S, (suspend ActionScope<E>.(S) -> Unit)?>,
  actionScope: ActionScope<E>,
): TransitionResult<S> {
  val s = config.first()
  return TransitionResult(
    state = s,
    action = { config.second?.invoke(actionScope, s) }
  )
}

internal data class TransitionResult<out S : Any>(
  val state: S,
  val action: (suspend () -> Unit)?
)
