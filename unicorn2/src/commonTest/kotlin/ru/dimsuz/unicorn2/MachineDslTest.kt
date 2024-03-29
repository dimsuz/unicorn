package ru.dimsuz.unicorn2

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MachineDslTest : ShouldSpec({
  context("initial state") {
    should("throw if not specified") {
      shouldThrowMessage("initial transition is missing") {
        machine<Unit> { }
      }
    }

    should("emit initial state first") {
      val m = machine<Int> {
        initial = 3 to null
      }

      m.states.test {
        awaitItem() shouldBe 3
        awaitComplete()
      }
    }
  }

  context("transitions") {
    should("perform transitions given streamed payloads") {
      checkAll(iterations = 100) { initialValue: List<Int>, payloads: List<Int> ->
        // Arrange
        val m = machine<List<Int>> {
          initial = initialValue to null
          onEach(payloads.asFlow()) {
            transitionTo { state, payload -> state.plus(payload) }
          }
        }

        // Assert
        val awaitedStates = mutableListOf(initialValue)
        payloads.mapTo(awaitedStates) { payload -> awaitedStates.last() + payload }

        m.states.test {
          awaitedStates.forEach { awaitedState ->
            awaitItem() shouldBe awaitedState
          }
          awaitComplete()
        }
      }
    }

    should("perform transitions given nullable streamed payloads") {
      // Arrange
      val payloads = flowOf(3, null, 4, null, 5)
      val m = machine<List<Int?>> {
        initial = mutableListOf<Int?>() to null
        onEach(payloads) {
          transitionTo { state, payload -> state.plus(payload) }
        }
      }

      // Assert
      val awaitedStates = mutableListOf(emptyList<Int?>())
      listOf(3, null, 4, null, 5).mapTo(awaitedStates) { payload -> awaitedStates.last() + payload }

      m.states.test {
        awaitedStates.forEach { awaitedState ->
          awaitItem() shouldBe awaitedState
        }
        awaitComplete()
      }
    }

    should("execute initial action") {
      val actionResult = MutableStateFlow<Int?>(null)
      val m = machine<Int> {
        initial = 3 to {
          actionResult.emit(it * 2)
        }
      }

      m.states.test {
        awaitItem() shouldBe 3
        awaitComplete()
        actionResult.value shouldBe 6
      }
    }

    should("not throw errors when no transitions in onEach-clause") {
      // such machine is valid because it can contain actions to execute
      val m = machine<List<Int>> {
        initial = listOf(3) to null
        onEach(flowOf(10)) {
          action { _, _, _ -> /* do something awesome */ }
        }
      }

      m.states.test {
        awaitItem() shouldBe listOf(3)
        awaitComplete()
      }
    }

    should("call transition block only for specific streaming payloads") {
      var firstBlockCallCount = 0
      var secondBlockCallCount = 0
      val m = machine<List<Int>> {
        initial = listOf(3) to null
        onEach(flowOf(10, 20, 30)) {
          transitionTo { state, i ->
            firstBlockCallCount += 1
            state.plus(i)
          }
        }
        onEach(flowOf("a", "b", "c")) {
          transitionTo { state, s ->
            secondBlockCallCount += 1
            state.plus(s.length)
          }
        }
      }

      m.states.test {
        repeat(7) { awaitItem() } // initial + 6 emissions
        awaitComplete()
        firstBlockCallCount shouldBe 3
        secondBlockCallCount shouldBe 3
      }
    }

    should("subscribe to streamed payloads only once") {
      val count = MutableStateFlow(0)
      val m = machine<List<Int>> {
        initial = listOf(3) to null
        onEach(flowOf(10, 20, 30).onStart { count.updateAndGet { it + 1 } }) {
          transitionTo { state, i -> state.plus(i) }
        }
      }

      m.states.test {
        repeat(4) { awaitItem() } // initial + 3 emissions
        awaitComplete()
      }

      count.value shouldBe 1
    }

    should("conflate state, but perform actions") {
      val callCount = MutableStateFlow(0)
      val m = machine<Int> {
        initial = 0 to null
        onEach(flowOf(1, 2, 3)) {
          transitionTo { state, payload ->
            42
          }

          action { _, _, _ ->
            callCount.update { it + 1 }
          }
        }
      }

      m.states.test {
        awaitItem() shouldBe 0
        awaitItem() shouldBe 42
        awaitComplete()

        callCount.value shouldBe 3
      }
    }
  }

  context("actions") {
    should("execute initial action if specified") {
      var executed = false
      val m = machine<Int> {
        initial = 3 to { executed = true }
      }

      m.states.test {
        // Act
        awaitItem()
        awaitComplete()

        // Assert
        executed shouldBe true
      }
    }

    should("execute action in onEach-clause on each emission") {
      var count = 0
      val m = machine<List<Int>> {
        initial = listOf(3) to null
        onEach(flowOf(1, 2, 3, 4)) {
          transitionTo { state, i -> state.plus(i) }
          action { _, _, _ -> count += 1 }
        }
      }

      m.states.test {
        // Act
        repeat(5) {
          awaitItem()
        }
        awaitComplete()

        // Assert
        count shouldBe 4
      }
    }

    should("execute action in onEach-clause with correct arguments") {
      val arguments: MutableList<ActionArgs<List<Int>, Int>> = arrayListOf()
      val m = machine<List<Int>> {
        initial = listOf(3) to null
        onEach(flowOf(1, 2, 3)) {
          transitionTo { state, payload -> state.plus(payload) }
          action { prevState, newState, payload ->
            arguments.add(
              ActionArgs(
                prevState,
                newState,
                payload
              )
            )
          }
        }
      }

      m.states.test {
        // Act
        repeat(4) {
          awaitItem()
        }
        awaitComplete()

        // Assert
        arguments shouldContainExactly listOf(
          ActionArgs(listOf(3), listOf(3, 1), 1),
          ActionArgs(
            listOf(3, 1),
            listOf(3, 1, 2),
            2
          ),
          ActionArgs(
            listOf(3, 1, 2),
            listOf(3, 1, 2, 3),
            3
          )
        )
      }
    }

    should("execute action in onEach only once per reduce") {
      var count = 0
      val m = machine<Int> {
        initial = 0 to null

        onEach(flowOf("1", "2", "3")) {
          action { _, _, _ ->
            count += 1
          }
        }
      }

      m.states.test {
        awaitItem()
        awaitComplete()

        count shouldBe 3
      }
    }
  }

  context("substate") {
    context("transitions") {
      should("perform transitions corresponding to substate branch") {
        val flow1 = flowOf(1, 2, 3)
        val flow2 = flowOf("bar", "baz").delayUntilCompletionOf(flow1)

        val machine = machine<ViewState> {
          initial = ViewState.A(value = 3) to null

          whenIn<ViewState.A> {
            onEach(flow1) {
              transitionTo { state, v -> if (v != 3) state.copy(value = state.value + v) else ViewState.B("foo") }
            }
          }

          whenIn<ViewState.B> {
            onEach(flow2) {
              transitionTo { state, v -> state.copy(value = state.value + v) }
            }
          }
        }

        machine.states.test {
          awaitItem() shouldBe ViewState.A(value = 3)
          awaitItem() shouldBe ViewState.A(value = 4)
          awaitItem() shouldBe ViewState.A(value = 6)
          awaitItem() shouldBe ViewState.B(value = "foo")
          awaitItem() shouldBe ViewState.B(value = "foobar")
          awaitItem() shouldBe ViewState.B(value = "foobarbaz")
          awaitComplete()
        }
      }

      should("ignore events for inactive sub-states") {
        val eventsI = MutableSharedFlow<Int>()
        val eventsS = MutableSharedFlow<String>()

        val machine = machine<ViewState> {
          initial = ViewState.B(value = "hi") to null

          whenIn<ViewState.A> {
            onEach(eventsI) {
              transitionTo { state, v -> if (v != 42) state.copy(value = v) else ViewState.B("foo") }
            }
          }

          whenIn<ViewState.B> {
            onEach(eventsS) {
              transitionTo { state, v -> if (v != "42") state.copy(value = v) else ViewState.A(42) }
            }
          }
        }

        machine.states.test {
          awaitItem() shouldBe ViewState.B("hi")

          eventsI.emit(1) // should be ignored
          eventsI.emit(2) // should be ignored
          eventsS.emit("foo")
          awaitItem() shouldBe ViewState.B("foo")

          eventsS.emit("42")
          awaitItem() shouldBe ViewState.A(42)

          eventsI.emit(1)
          eventsS.emit("hi")
          eventsS.emit("there")
          eventsI.emit(2)
          awaitItem() shouldBe ViewState.A(1)
          awaitItem() shouldBe ViewState.A(2)

          eventsI.emit(42)
          awaitItem() shouldBe ViewState.B("foo")
        }
      }

      should("trigger parent state transitions and actions on child events") {
        val events = MutableSharedFlow<String>()
        val actionCallCounts = hashMapOf<String, Int>()
        val m = machine<NestedState> {
          initial = NestedState.State1("a") to null

          onEach(events) {
            action { _, _, _ ->
              println("top-level receiving")
              // NOTE! This doesn't represent "onEntry". It's incremented when **event** is received!
              actionCallCounts.increment("top-level")
            }
          }

          whenIn<NestedState.State1> {
            onEach(events) {
              transitionTo { _, _ ->
                NestedState.State3.State3a(3)
              }

              action { _, _, _ ->
                // NOTE! This doesn't represent "onEntry". It's incremented when **event** comes!
                println("state1 receiving")
                actionCallCounts.increment("state1")
              }
            }
          }

          whenIn<NestedState.State2> {
            onEach(events) {
              action { _, _, _ ->
                actionCallCounts.increment("state2")
              }
            }
          }

          whenIn<NestedState.State3> {
            onEach(events) {
              action { _, _, _ ->
                println("state3 receiving")
                actionCallCounts.increment("state3")
              }
            }
          }

          whenIn<NestedState.State3.State3a> {
            onEach(events) {
              transitionTo { s, payload ->
                if (payload == "3b") NestedState.State3.State3b(1) else s
              }

              action { _, _, _ ->
                println("state3a receiving")
                actionCallCounts.increment("state3.state3a")
              }
            }
          }

          whenIn<NestedState.State3.State3b> {
            onEach(events) {
              action { _, _, _ ->
                actionCallCounts.increment("state3.state3b")
              }
            }
          }
        }

        m.states.test {
          awaitItem() shouldBe NestedState.State1("a")
          actionCallCounts shouldBe emptyMap()

          events.emit("foo")
          awaitItem().shouldBeInstanceOf<NestedState.State3.State3a>()
          actionCallCounts shouldBe mapOf(
            "top-level" to 1,
            "state1" to 1,
          )

          events.emit("foo") // staying in 3a, but updating counts
          actionCallCounts shouldBe mapOf(
            "top-level" to 2,
            "state1" to 1,
            "state3" to 1,
            "state3.state3a" to 1,
          )

          events.emit("3b")
          awaitItem().shouldBeInstanceOf<NestedState.State3.State3b>()
          actionCallCounts shouldBe mapOf(
            "top-level" to 3,
            "state1" to 1,
            "state3" to 2,
            "state3.state3a" to 2,
          )

          events.emit("foo")
          actionCallCounts shouldBe mapOf(
            "top-level" to 4,
            "state1" to 1,
            "state3" to 3,
            "state3.state3a" to 2,
            "state3.state3b" to 1,
          )
        }
      }

      should("keep only payload sources active in a current sub-state") {
        // TODO decide if the option to keep all connections is required
      }
    }
  }
})

private fun <K> MutableMap<K, Int>.increment(key: K): MutableMap<K, Int> {
  val v = (this[key] ?: 0) + 1
  this[key] = v
  return this
}

private fun <T> Flow<T>.delayUntilCompletionOf(other: Flow<*>): Flow<T> {
  return flow {
    other.collect()
    emitAll(this@delayUntilCompletionOf)
  }
}

private sealed class ViewState {
  data class A(val value: Int) : ViewState()
  data class B(val value: String) : ViewState()
}

private sealed class NestedState {
  data class State1(val name: String) : NestedState()
  data class State2(val price: Int) : NestedState()

  sealed class State3 : NestedState() {
    data class State3a(
      val i: Int,
    ) : State3()
    data class State3b(
      val i: Int,
    ) : State3()
  }
}

typealias ActionArgs<S, P> = Triple<S, S, P>
