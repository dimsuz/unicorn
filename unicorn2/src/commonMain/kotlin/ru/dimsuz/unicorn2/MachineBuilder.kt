/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.reflect.KClass

public inline fun <reified S : Any> machine(
  init: MachineDsl<S>.() -> Unit
): Machine<S> {
  val machineDsl = MachineDsl(S::class).apply(init)
  return buildMachine(
    createMachineConfig(
      machineDsl
    )
  )
}

public inline fun <S : Any> machine(
  stateClass: KClass<S>,
  init: MachineDsl<S>.() -> Unit
): Machine<S> {
  val machineDsl = MachineDsl(stateClass).apply(init)
  return buildMachine(
    createMachineConfig(
      machineDsl
    )
  )
}

@PublishedApi
internal fun <S : Any> buildMachine(
  machineConfig: MachineConfig<S>
): Machine<S> {
  return object : Machine<S> {
    override val initial: Pair<S, (suspend (S) -> Unit)?> = machineConfig.initial
    override val states: Flow<S> = buildStatesFlow(machineConfig)
  }
}

private fun <S : Any> buildStatesFlow(
  machineConfig: MachineConfig<S>
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
            val result = produceResult(transitionResult, payloadBundle)
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

private suspend fun <S : Any> produceResult(
  stateBundle: TransitionResult<S>,
  payloadBundle: Pair<Any?, TransitionConfig<S, S>>
): TransitionResult<S>? {
  val (payload, transitionConfig) = payloadBundle
  val previousState = stateBundle.state
  if (!transitionConfig.stateClass.isInstance(previousState)) {
    return null
  }
  val nextState = transitionConfig.transition(previousState, payload)
  val action = transitionConfig.action?.let {
    suspend { it(previousState, nextState, payload) }
  }
  return TransitionResult(nextState, action)
}

private suspend fun <S : Any> buildInitialState(
  config: Pair<S, (suspend (S) -> Unit)?>
): TransitionResult<S> {
  return TransitionResult(
    state = config.first,
    action = { config.second?.invoke(config.first) }
  )
}

internal data class TransitionResult<out S : Any>(
  val state: S,
  val action: (suspend () -> Unit)?
)
