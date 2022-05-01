package ru.dimsuz.unicorn2

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

sealed class CompoundState {
  data class State1(val name: String) : CompoundState()
  data class State2(val price: Int) : CompoundState()

  sealed class State3 : CompoundState() {
    data class State3a(
      val i: Int,
    ) : State3()
  }
}

data class State(
  val name: String,
  val price: Int,
)

val m1 = machine<CompoundState, Unit> {
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
      transitionTo { state, name ->
        CompoundState.State2(price = 3)
      }

      action { state: CompoundState.State1, newState: CompoundState, payload: String ->
        sendEvent(Unit)
      }
    }
  }

  whenIn<CompoundState.State2> {
    onEach(flowOf("Noah")) {
      transitionTo { s, payload ->
        s.copy(price = s.price + 110)
      }
    }

    onEach(events) {
      transitionTo { state: CompoundState.State2, payload: Unit ->
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

val m2 = machine<State, Unit> {
  onEach(flowOf("Jacob")) {
    transitionTo { state, name ->
      delay(3000)
      state.copy(name = name)
    }

    action { _, _, _ ->
      delay(3000)
      sendEvent(Unit)
    }
  }
}
