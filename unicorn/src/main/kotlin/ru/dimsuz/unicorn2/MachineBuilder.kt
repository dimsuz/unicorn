/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

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

    override val initial: Pair<suspend () -> S, (suspend (S) -> Unit)?> = machineConfig.initial

    override val states: Flow<S> = buildStatesFlow(machineConfig, actionScope)

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
  actionScope: ActionScope<E>
): Flow<S> {
  return channelFlow {
    var transitionResult = buildInitialState(machineConfig.initial)
    send(transitionResult.state)
    transitionResult.action?.invoke()

    var subscribeToNextState = true

    while (subscribeToNextState) {
      val job = async {
        machineConfig
          .transitions
          .filter {
            it.stateClass.isInstance(transitionResult.state)
          }
          .map { config -> config.payloadSource.map { payload -> payload to config } }
          .merge()
          .collect { payloadBundle ->
            val result = produceResult(transitionResult, payloadBundle, actionScope)
            if (result != null) {
              val previousResult = transitionResult
              transitionResult = result
              if (previousResult.state != result.state) {
                send(result.state)
              }
              result.action?.invoke()
              if (!payloadBundle.second.stateClass.isInstance(result.state)) {
                this.cancel(message = "sub_state_switch")
              }
            }
          }
      }
      subscribeToNextState = try {
        job.await()
        // either all collects are finished (when no hot flows) or some non-cancellation exception thrown
        false
      } catch (e: CancellationException) {
        e.message == "sub_state_switch"
      } catch (e: Throwable) {
        throw e
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

private suspend fun <S : Any> buildInitialState(
  config: Pair<suspend () -> S, (suspend (S) -> Unit)?>,
): TransitionResult<S> {
  val s = config.first()
  return TransitionResult(
    state = s,
    action = { config.second?.invoke(s) }
  )
}

internal data class TransitionResult<out S : Any>(
  val state: S,
  val action: (suspend () -> Unit)?
)
