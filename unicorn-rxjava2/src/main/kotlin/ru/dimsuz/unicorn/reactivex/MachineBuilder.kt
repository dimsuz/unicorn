package ru.dimsuz.unicorn.reactivex

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import ru.dimsuz.unicorn.reactivex.TransitionConfig.EventConfig

internal fun <S : Any, E : Any> buildMachine(
  machineConfig: MachineConfig<S, *>,
  actionsScheduler: Scheduler?,
): Machine<S, E> {
  return object : Machine<S, E> {
    private val discreteEventSubject = PublishSubject.create<Any>().toSerialized()
    override val states =
      buildTransitionStream(
        machineConfig,
        discreteEventSubject,
        actionsScheduler
      )

    override fun send(e: E) {
      discreteEventSubject.onNext(e)
    }
  }
}

private fun <S : Any> buildTransitionStream(
  machineConfig: MachineConfig<S, *>,
  discreteEventSubject: Subject<Any>,
  actionsScheduler: Scheduler?,
): Observable<S> {
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
      buildInitialState(machineConfig.initial)
    ) { stateBundle: TransitionResult<S>, payloadBundle: Pair<Any, TransitionConfig<S, *>> ->
      val payload = payloadBundle.first
      val previousState = stateBundle.state
      val nextState = payloadBundle.second.reducer(previousState, payload)
      val nextAction = payloadBundle.second.reduceActions(previousState, nextState, payload)
      val nextInternalAction = payloadBundle.second.reduceInternalActions(previousState, nextState, payload)
      TransitionResult(nextState, nextAction, nextInternalAction)
    }
    .flatMapSingle { result ->
      val actions = when {
        result.actions != null && result.internalActions != null -> {
          result.actions.andThen(result.internalActions)
            .doAfterSuccess { events -> events.forEach { discreteEventSubject.onNext(it) } }
            .ignoreElement()
        }
        result.internalActions != null -> {
          result.internalActions
            .doAfterSuccess { events -> events.forEach { discreteEventSubject.onNext(it) } }
            .ignoreElement()
        }
        result.actions != null -> result.actions
        else -> Completable.complete()
      }
      actions
        .let { if (actionsScheduler != null) it.subscribeOn(actionsScheduler) else it }
        .andThen(Single.just(result.state))
    }
}

private fun <S : Any> buildInitialState(config: InitialStateConfig<S>): TransitionResult<S> {
  return TransitionResult(
    state = config.first,
    actions = config.second?.let { Completable.fromAction(it) },
    internalActions = null
  )
}

private fun <S : Any> TransitionConfig<S, *>.reduceActions(
  previousState: S,
  newState: S,
  payload: Any,
): Completable? {
  return actions?.let { list ->
    Completable.fromAction {
      list.forEach { body ->
        body(previousState, newState, payload)
      }
    }
  }
}

private fun <S : Any> TransitionConfig<S, *>.reduceInternalActions(
  previousState: S,
  newState: S,
  payload: Any,
): Single<List<Any>>? {
  return actionsWithEvent?.let { list ->
    Single.fromCallable {
      val events = mutableListOf<Any>()
      list.forEach { body ->
        val event = body(previousState, newState, payload)
        if (event != null) {
          events.add(event)
        }
      }
      events
    }
  }
}

fun <S : Any, E : Any> machine(actionsScheduler: Scheduler? = null, init: MachineDsl<S, E>.() -> Unit): Machine<S, E> {
  val machineDsl = MachineDsl<S, E>()
  machineDsl.init()
  return buildMachine(
    MachineConfig.create(
      machineDsl
    ),
    actionsScheduler
  )
}
