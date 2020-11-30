package ru.dimsuz.unicorn.reactivex

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import ru.dimsuz.unicorn.reactivex.TransitionConfig.EventConfig

internal fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, *>
): Machine<S, E> {
  return object : Machine<S, E> {
    private val discreteEventSubject = PublishSubject.create<Any>().toSerialized()
    override val transitionStream =
      buildTransitionStream(
        machineConfig,
        discreteEventSubject
      )
    override fun send(e: E) {
      discreteEventSubject.onNext(e)
    }
  }
}

private fun <S : Any> buildTransitionStream(
  machineConfig: MachineConfig<S, *>,
  discreteEventSubject: Subject<Any>
): Observable<TransitionResult<S>> {
  val discreteEventSources = machineConfig.transitions
    .filter { it.eventConfig is EventConfig.Discrete }
    .map { transitionConfig ->
      val eventSelector = (transitionConfig.eventConfig as EventConfig.Discrete).eventSelector
      discreteEventSubject
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
  // first subscribing to discreteEventSources, because they are based on discreteEventSubject
  // and subscription to it needs to happen first, so that if any transition with
  // actionWithEvent exists, subject will already be subscribed to when emission happens
  return Observable
    .merge(discreteEventSources)
    .mergeWith(Observable.merge(streamedEventSources))
    .scan(
      buildInitialState(machineConfig.initial),
      { stateBundle: Pair<S, Completable?>, payloadBundle: Pair<Any, TransitionConfig<S, *>> ->
        val payload = payloadBundle.first
        val previousState = stateBundle.first
        val nextState = payloadBundle.second.reducer(previousState, payload)
        val nextAction = payloadBundle.second.reduceActions(
          previousState,
          nextState,
          payload,
          discreteEventSubject
        )
        nextState to nextAction
      }
    )
}

private fun <S : Any> buildInitialState(config: InitialStateConfig<S>): Pair<S, Completable?> {
  return config.first to config.second?.let { Completable.fromAction(it) }
}

private fun <S : Any> TransitionConfig<S, *>.reduceActions(
  previousState: S,
  newState: S,
  payload: Any,
  discreteEventSubject: Subject<Any>
): Completable? {
  return actions
    ?.map { body ->
      body(previousState, newState, payload)
        .doOnSuccess { discreteEventSubject.onNext(it) }
        .ignoreElement()
    }
    ?.let { Completable.concat(it) }
}
