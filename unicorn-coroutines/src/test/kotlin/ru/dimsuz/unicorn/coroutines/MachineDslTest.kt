package ru.dimsuz.unicorn.coroutines

import app.cash.turbine.test
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

      m.states.test {
        expectItem() shouldBe 3
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

        m.states.test {
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

        m.states.test {
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
          expectedStates.forEach { expectedState ->
            expectItem() shouldBe expectedState
          }
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

      m.states.test {
        expectItem() shouldBe listOf(3)
        expectItem() shouldBe listOf(3)
        expectComplete()
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

      m.states.test {
        m.send(Event.E1(24))
        expectItem() shouldBe listOf(3)
        expectItem() shouldBe listOf(3)
      }
    }

    should("process both on and onEach sources when they are mixed") {
      checkAll(Arb.list(Arb.events(), range = 0..20)) { events: List<Event> ->
        // Arrange
        val streamedSource = MutableSharedFlow<String>()
        val m = machine<List<Int>, Event> {
          initial = listOf(3) to null
          on(Event.E1::class) {
            transitionTo { state, event -> state.plus(event.value) }
          }
          onEach(streamedSource) {
            transitionTo { state, payload -> state.plus(payload.length * 10) }
          }
        }

        m.states.test {
          // Act
          events.forEach { event ->
            when (event) {
              is Event.E1 -> m.send(event)
              is Event.E2 -> streamedSource.emit(event.value)
            }
          }

          // Assert
          val expectedStates = mutableListOf(listOf(3))
          events.mapTo(expectedStates) { event ->
            val state = expectedStates.last()
            when (event) {
              is Event.E1 -> state + event.value
              is Event.E2 -> state + event.value.length * 10
            }
          }
          expectedStates.forEach { expectedState ->
            expectItem() shouldBe expectedState
          }
        }
      }
    }

    should("call transition block only for specific streaming payloads") {
      var firstBlockCallCount = 0
      var secondBlockCallCount = 0
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(flowOf(10, 20, 30)) {
          transitionTo { state, _ ->
            firstBlockCallCount += 1
            state
          }
        }
        onEach(flowOf("a", "b", "c")) {
          transitionTo { state, _ ->
            secondBlockCallCount += 1
            state
          }
        }
      }

      m.states.test {
        repeat(7) { expectItem() } // initial + 6 emissions
        expectComplete()
        firstBlockCallCount shouldBe 3
        secondBlockCallCount shouldBe 3
      }
    }

    should("subscribe to streamed payloads only once") {
      val count = AtomicInteger(0)
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(flowOf(10, 20, 30).onStart { count.incrementAndGet() }) {
          transitionTo { state, _ -> state }
        }
      }

      m.states.test {
        repeat(4) { expectItem() } // initial + 3 emissions
        expectComplete()
      }

      count.get() shouldBe 1
    }
  }

  context("actions") {
    should("execute initial action if specified") {
      var executed = false
      val m = machine<Int, Unit> {
        initial = 3 to { executed = true }
      }

      m.states.test {
        // Act
        expectItem()
        expectComplete()

        // Assert
        executed shouldBe true
      }
    }

    should("execute action in onEach-clause on each emission") {
      var count = 0
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(flowOf(1, 2, 3, 4)) {
          transitionTo { state, _ -> state }
          action { _, _, _ -> count += 1 }
        }
      }

      m.states.test {
        // Act
        repeat(5) {
          expectItem()
        }
        expectComplete()

        // Assert
        count shouldBe 4
      }
    }

    should("execute action in onEach-clause with correct arguments") {
      val arguments: MutableList<ActionArgs<List<Int>, Int>> = arrayListOf()
      val m = machine<List<Int>, Unit> {
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
          expectItem()
        }
        expectComplete()

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

    should("execute action in on-clause with correct arguments") {
      val arguments: MutableList<ActionArgs<List<Int>, Event>> = arrayListOf()
      val m = machine<List<Int>, Event> {
        initial = listOf(3) to null
        on(Event.E1::class) {
          transitionTo { state, event -> state.plus(event.value) }
          action { prevState, newState, event ->
            arguments.add(
              ActionArgs(
                prevState,
                newState,
                event
              )
            )
          }
        }
        on(Event.E2::class) {
          transitionTo { state, event -> state.plus(event.value.toInt() * 10) }
          action { prevState, newState, event ->
            arguments.add(
              ActionArgs(
                prevState,
                newState,
                event
              )
            )
          }
        }
      }

      m.states.test {
        m.send(Event.E2("33"))
        m.send(Event.E1(88))

        // Act
        repeat(3) {
          expectItem()
        }

        // Assert
        arguments shouldContainExactly listOf(
          ActionArgs(
            listOf(3),
            listOf(3, 330),
            Event.E2("33")
          ),
          ActionArgs(
            listOf(3, 330),
            listOf(3, 330, 88),
            Event.E1(88)
          )
        )
      }
    }

    should("merge actions in the onEach-clause and execute them sequentially") {
      val markers: MutableList<String> = arrayListOf()
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(flowOf(1, 2)) {
          transitionTo { state, _ -> state }
          action { _, _, _ -> markers.add("action1") }
          action { _, _, _ -> markers.add("action2") }
          action { _, _, _ -> markers.add("action3") }
        }
      }

      m.states.test {
        repeat(3) {
          expectItem()
        }
        expectComplete()

        markers shouldContainExactly listOf(
          "action1",
          "action2",
          "action3",
          "action1",
          "action2",
          "action3"
        )
      }
    }

    should("execute action in onEach only once per reduce") {
      var count = 0
      val m = machine<Int, Event> {
        initial = 0 to null

        onEach(flowOf("1", "2", "3")) {
          action { _, _, _ ->
            count += 1
          }
        }
      }

      m.states.test {
        repeat(4) {
          expectItem()
        }
        expectComplete()

        count shouldBe 3
      }
    }

    should("execute action in on-clause only once per reduce") {
      var count = 0
      val m = machine<Int, Event> {
        initial = 0 to null

        on(Event.E1::class) {
          action { _, _, _ ->
            count += 1
          }
        }
      }

      m.states.test {
        m.send(Event.E1(1))
        m.send(Event.E1(2))
        m.send(Event.E1(3))

        repeat(4) {
          expectItem()
        }

        count shouldBe 3
      }
    }

    should("execute corresponding event transition after onEach-action emits an event") {
      val m = machine<Int, Event> {
        initial = 0 to null

        onEach(flowOf(10, 20)) {
          actionWithEvent { _, _, payload ->
            Event.E1(payload)
          }
        }

        on(Event.E1::class) {
          transitionTo { state, payload ->
            state + payload.value
          }
        }
      }

      m.states.test {
        var lastState: Int? = null
        repeat(5) {
          lastState = expectItem()
        }

        lastState shouldBe 30
      }
    }

    should("execute corresponding event transition after on-action emits an event") {
      val m = machine<Int, Event> {
        initial = 0 to null

        on(Event.E2::class) {
          actionWithEvent { _, _, event ->
            Event.E1(if (event.value == "he") 10 else 20)
          }
        }

        on(Event.E1::class) {
          transitionTo { state, payload ->
            state + payload.value
          }
        }
      }

      m.states.test {
        m.send(Event.E2("he"))
        m.send(Event.E2("llo"))

        var lastState: Int? = null
        repeat(5) {
          val s = expectItem()
          lastState = s
        }

        lastState shouldBe 30
      }
    }

    // TODO this checks the current thread, but it really should check running in the current `CoroutineContext`!
    //   currently not sure how to check that
    should("run actions on current thread by default") {
      val testThreadId = Thread.currentThread().id
      val onEachActionThreadId = AtomicLong()
      val onActionThreadId = AtomicLong()
      val onActionWithEventThreadId = AtomicLong()
      val m = machine<Int, Event> {
        initial = 3 to null
        onEach(flowOf(10)) {
          action { _, _, _ -> onEachActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E1::class) {
          action { _, _, _ -> onActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E2::class) {
          actionWithEvent { _, _, _ -> onActionWithEventThreadId.set(Thread.currentThread().id); null }
        }
      }

      m.states.test {
        expectItem()
        expectItem()
        m.send(Event.E1(33))
        expectItem()
        m.send(Event.E2("hello"))
        expectItem()

        onEachActionThreadId.get() shouldBe testThreadId
        onActionThreadId.get() shouldBe testThreadId
        onActionWithEventThreadId.get() shouldBe testThreadId
      }
    }

    should("run actions on specified dispatcher") {
      val testThreadId = Thread.currentThread().id
      val onEachActionThreadId = AtomicLong()
      val onActionThreadId = AtomicLong()
      val onActionWithEventThreadId = AtomicLong()
      val m = machine<Int, Event>(Executors.newFixedThreadPool(8).asCoroutineDispatcher()) {
        initial = 3 to null
        onEach(flowOf(10)) {
          action { _, _, _ ->
            onEachActionThreadId.set(Thread.currentThread().id)
          }
        }

        on(Event.E1::class) {
          action { _, _, _ ->
            onActionThreadId.set(Thread.currentThread().id)
          }
        }

        on(Event.E2::class) {
          actionWithEvent { _, _, _ ->
            onActionWithEventThreadId.set(Thread.currentThread().id); null
          }
        }
      }

      m.states.test {
        expectItem()
        expectItem()
        m.send(Event.E1(33))
        expectItem()
        m.send(Event.E2("hello"))
        expectItem()

        onEachActionThreadId.get() shouldNotBe testThreadId
        onActionThreadId.get() shouldNotBe testThreadId
        onActionWithEventThreadId.get() shouldNotBe testThreadId
      }
    }

    // TODO error when 2 transitionTo blocks
    // TODO no error when no transitions and no actions
  }
})

private sealed class Event {
  data class E1(val value: Int) : Event()
  data class E2(val value: String) : Event()
}

typealias ActionArgs<S, P> = Triple<S, S, P>

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
