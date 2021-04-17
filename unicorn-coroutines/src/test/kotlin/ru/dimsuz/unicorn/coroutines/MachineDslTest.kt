package ru.dimsuz.unicorn.coroutines

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime

@ExperimentalTime
class MachineDslTest : ShouldSpec({
  context("initial state") {
    should("throw if not specified") {
      shouldThrowMessage("initial state is missing") {
        machine<Unit, Unit> { }
      }
    }

    should("emit initial state first") {
      val m = machine<Int, Unit> {
        initial = 3 to null
      }

      m.transitionStream.test {
        expectItem() shouldBe TransitionResult(3, null)
        expectComplete()
      }
    }
  }

  context("transitions") {
    should("perform transitions given streamed payloads") {
      checkAll { initialValue: List<Int>, payloads: List<Int> ->
        // Arrange
        val m = machine<List<Int>, Unit> {
          initial = initialValue to null
          onEach(payloads.asFlow()) {
            transitionTo { state, payload -> state.plus(payload) }
          }
        }

        // Assert
        val expectedStates = mutableListOf(initialValue)
        payloads.mapTo(expectedStates) { payload -> expectedStates.last() + payload }

        m.transitionStream.map { it.state }.test {
          expectedStates.forEach { expectedState ->
            expectItem() shouldBe expectedState
          }
          expectComplete()
        }
      }
    }

    should("perform transitions given discrete events") {
      checkAll(Arb.list(Arb.events(), 0..20)) { events: List<Event> ->
        // Arrange
        val m = machine<Pair<List<Int>, String>, Event> {
          initial = (listOf(3) to "") to null
          on(Event.E1::class) {
            transitionTo { state, event -> state.copy(first = state.first.plus(event.value)) }
          }
          on(Event.E2::class) {
            transitionTo { state, event -> state.copy(second = state.second.plus(event.value)) }
          }
        }

        // Act
        events.forEach { m.send(it) }

        // Assert
        val expectedStates = mutableListOf(listOf(3) to "")
        events.mapTo(expectedStates) { event ->
          val state = expectedStates.last()
          when (event) {
            is Event.E1 -> state.copy(first = state.first + event.value)
            is Event.E2 -> state.copy(second = state.second + event.value)
          }
        }
        m.transitionStream.map { it.state }.test {
          expectedStates.forEach { expectedState ->
            expectItem() shouldBe expectedState
          }
          expectComplete()
        }
      }
    }

    should("not throw errors when no transitions in onEach-clause") {
      // such machine is valid because it can contain actions to execute
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(flowOf(10)) {
          action { _, _, _ -> /* do something awesome */ }
        }
      }

      m.transitionStream.map { it.state }.test {
        expectItem() shouldBe listOf(3)
        expectItem() shouldBe listOf(3)
      }
    }

    should("not throw errors when no transitions in on-clause") {
      // such machine is valid because it can contain actions to execute
      val m = machine<List<Int>, Event> {
        initial = listOf(3) to null
        on(Event.E1::class) {
          action { _, _, _ -> /* do something awesome */ }
        }
      }

      m.send(Event.E1(24))

      m.transitionStream.map { it.state }.test {
        expectItem() shouldBe listOf(3)
        expectItem() shouldBe listOf(3)
      }
    }
  }
})

private sealed class Event {
  data class E1(val value: Int) : Event()
  data class E2(val value: String) : Event()
}

private fun Arb.Companion.events(): Arb<Event> {
  return arbitrary { rs ->
    val useFirst = rs.random.nextBoolean()
    if (useFirst) {
      Event.E1(Arb.int().next(rs))
    } else {
      Event.E2(Arb.string().next(rs))
    }
  }
}