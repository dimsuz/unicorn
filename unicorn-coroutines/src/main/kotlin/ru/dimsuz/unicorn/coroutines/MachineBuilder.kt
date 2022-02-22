package ru.dimsuz.unicorn.coroutines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import ru.dimsuz.unicorn.coroutines.TransitionConfig.EventConfig
import kotlin.coroutines.CoroutineContext

internal fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, *>,
  actionsContext: CoroutineContext? = null,
): Machine<S, E> {
  return object : Machine<S, E> {
    override val initial: Pair<S, (suspend () -> Unit)?> = machineConfig.initial

    val discreteEventFlow = MutableSharedFlow<Any>()

    override val states: Flow<S>
      get() = buildTransitionStream(
        machineConfig,
        discreteEventFlow,
        actionsContext,
      )

    override suspend fun send(e: E) {
      discreteEventFlow.emit(e)
    }

    override fun trySend(e: E): Boolean {
      return discreteEventFlow.tryEmit(e)
    }
  }
}

private fun <S : Any> buildTransitionStream(
  machineConfig: MachineConfig<S, *>,
  discreteEventFlow: MutableSharedFlow<Any>,
  actionsContext: CoroutineContext?,
): Flow<S> {
  val discreteEventSources = machineConfig.transitions
    .filter { it.eventConfig is EventConfig.Discrete }
    .map { transitionConfig ->
      val eventSelector = (transitionConfig.eventConfig as EventConfig.Discrete).eventSelector
      discreteEventFlow
        .filter { it::class == eventSelector }
        .map { event -> event to transitionConfig }
    }
  val streamedEventSources = machineConfig.transitions
    .filter { it.eventConfig is EventConfig.Streamed }
    .map { transitionConfig ->
      val payloadSource = (transitionConfig.eventConfig as EventConfig.Streamed).payloadSource
      payloadSource
        .map { payload -> payload to transitionConfig }
    }
  return (discreteEventSources + streamedEventSources)
    .merge()
    .scan(
      buildInitialState(machineConfig.initial)
    ) { stateBundle: TransitionResult<S>, payloadBundle: Pair<Any?, TransitionConfig<S, out Any>> ->
      val payload = payloadBundle.first
      val previousState = stateBundle.state
      val nextState = payloadBundle.second.reducer(previousState, payload)
      val nextAction = payloadBundle.second.reduceActions(
        previousState,
        nextState,
        payload,
        discreteEventFlow
      )
      TransitionResult(nextState, nextAction)
    }
    // no analog for doAfterSuccess, so using transform (inspired by Flow.onEach() implementation)
    .transform { result ->
      emit(result.state)
      if (actionsContext != null) {
        withContext(actionsContext) {
          result.actions?.invoke()
        }
      } else {
        result.actions?.invoke()
      }
    }
}

private fun <S : Any> buildInitialState(config: InitialStateConfig<S>): TransitionResult<S> {
  return TransitionResult(
    state = config.first,
    actions = config.second
  )
}

private fun <S : Any> TransitionConfig<S, *>.reduceActions(
  previousState: S,
  newState: S,
  payload: Any?,
  discreteEventFlow: MutableSharedFlow<Any>
): (suspend () -> Unit)? {
  return actions?.let { list ->
    {
      list.forEach { body ->
        val event = body(previousState, newState, payload)
        if (event != null) {
          discreteEventFlow.emit(event)
        }
      }
    }
  }
}

fun <S : Any, E : Any> machine(
  actionsContext: CoroutineContext? = null,
  init: MachineDsl<S, E>.() -> Unit
): Machine<S, E> {
  val machineDsl = MachineDsl<S, E>()
  machineDsl.init()
  return buildMachine(
    MachineConfig.create(
      machineDsl
    ),
    actionsContext
  )
}
