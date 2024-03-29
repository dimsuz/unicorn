/*
 * Copyright 2022 Dmitry Suzdalev. Use of this source code is governed by the Apache 2.0 license.
 */
package ru.dimsuz.unicorn2

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

public sealed class CompoundState {
  public data class State1(val name: String) : CompoundState()
  public data class State2(val price: Int) : CompoundState()

  public sealed class State3 : CompoundState() {
    public data class State3a(
      val i: Int,
    ) : State3()
  }
}

public data class State(
  val name: String,
  val price: Int,
)

public val m1: Machine<CompoundState> = machine {

  val events = MutableSharedFlow<Unit>(extraBufferCapacity = 12)

  initial = CompoundState.State1("hello") to null

  onEach(events) {
    transitionTo { state, payload ->
      println("in 'root' state $state, having event $payload")
      state
    }
  }

  onEach(flowOf("Noah")) {
    action { _, _, _ ->
      // should fire up each time any of sub states change, because it's the root!
      //  (flowOf should actually be some shared hot flow of course)
      println("in 'root state")
    }
  }

  whenIn<CompoundState.State1> {
    onEach(flowOf("Noah")) {
      transitionTo { _, _ ->
        CompoundState.State2(price = 3)
      }

      action { _: CompoundState.State1, _: CompoundState, _: String ->
        events.emit(Unit)
      }
    }
  }

  whenIn<CompoundState.State2> {
    onEach(flowOf("Noah")) {
      transitionTo { s, _ ->
        s.copy(price = s.price + 110)
      }
    }

    onEach(events) {
      transitionTo { _: CompoundState.State2, _: Unit ->
        CompoundState.State3.State3a(i = 33)
      }
    }
  }

  whenIn<CompoundState.State3> {
    onEach(flowOf("Noah")) {
      action { _, _, _ ->
        // should fire up each time State3a is active!
        println("in State3")
      }
    }
  }

  whenIn<CompoundState.State3.State3a> {
    onEach(flowOf("Noah")) {
      transitionTo { state, payload ->
        println("in State3a, having a $payload")
        state
      }
    }
  }
}

public val m2: Machine<State> = machine {
  onEach(flowOf("Jacob")) {
    transitionTo { state, name ->
      delay(3000)
      state.copy(name = name)
    }

    action { _, _, _ ->
      delay(3000)
      println("action done")
    }
  }
}

public sealed class ViewState {
  public object Loading : ViewState()
  public data class Error(val text: String) : ViewState()
  public data class Content(val title: String, val subtitle: String) : ViewState()
}

public val contentSource: Flow<Pair<String, String>> = flowOf("Hello" to "World")
public val errorSource: Flow<Throwable> = emptyFlow()
public val niceThingsSource: Flow<String> = flowOf("Friends", "Trees", "Sky")

public val screenMachine: Machine<ViewState> = machine {
  initial = ViewState.Loading to null

  whenIn<ViewState.Loading> {
    onEach(contentSource) {
      transitionTo { _, (title, subtitle) ->
        ViewState.Content(title, subtitle)
      }
    }

    onEach(errorSource) {
      transitionTo { _, error ->
        ViewState.Error(error.message ?: "unknown error")
      }

      action { _, _, error ->
        error.printStackTrace()
      }
    }
  }

  whenIn<ViewState.Content> {
    onEach(niceThingsSource) {
      transitionTo { state, thing ->
        state.copy(subtitle = thing)
      }
    }
  }

  whenIn<ViewState.Error> {
    // onEach(retryClicks) { /* configuration of recover transition */ }
  }
}
