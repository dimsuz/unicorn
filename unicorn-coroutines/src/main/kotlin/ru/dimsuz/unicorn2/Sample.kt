package ru.dimsuz.unicorn2

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

sealed class CompoundState {
  data class State1(val name: String) : CompoundState()
  data class State2(val price: Int) : CompoundState()

  sealed class State3 : CompoundState() {
    object State3a : State3()
  }
}

data class State(
  val name: String,
  val price: Int,
)

val m1 = machine<CompoundState, Unit> {
  onEach(flowOf("Noah")) {
    whenIn<CompoundState.State1> {
      transitionTo { state, name ->
        CompoundState.State2(price = 3)
      }
    }

    whenIn<CompoundState.State2> {
      transitionTo { s, payload ->
        s.copy(price = s.price + 110)
      }
    }

    whenIn<CompoundState.State3> {
      whenIn<CompoundState.State3.State3a> {

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
    }

    event { _, _, _ -> delay(100); Unit }
  }
}
