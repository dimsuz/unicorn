package ru.dimsuz.unicorn.reactivex

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
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
      val observer =
        createSubscribedTestObserver(m.states)
      observer.assertValue(3)
    }
  }

  context("transitions") {
    should("perform transitions given streamed payloads") {
      checkAll { initialValue: List<Int>, payloads: List<Int> ->
        val m = machine<List<Int>, Unit> {
          initial = initialValue to null
          onEach(Observable.fromIterable(payloads)) {
            transitionTo { state, payload -> state.plus(payload) }
          }
        }
        val observer = createSubscribedTestObserver(m.states)
        val expectedStates = mutableListOf(initialValue)
        payloads.mapTo(expectedStates) { payload -> expectedStates.last() + payload }
        observer.assertValueSequence(expectedStates)
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
        val observer =
          createSubscribedTestObserver(m.states)

        // Act
        events.forEach { m.send(it) }

        val expectedStates = mutableListOf(listOf(3) to "")
        events.mapTo(expectedStates) { event ->
          val state = expectedStates.last()
          when (event) {
            is Event.E1 -> state.copy(first = state.first + event.value)
            is Event.E2 -> state.copy(second = state.second + event.value)
          }
        }

        // Assert
        observer.assertValueSequence(expectedStates)
      }
    }

    should("not throw errors when no transitions in onEach-clause") {
      // such machine is valid because it can contain actions to execute
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(10)) {
          action { _, _, _ -> /* do something awesome */ }
        }
      }
      val observer =
        createSubscribedTestObserver(m.states)
      observer.assertValues(listOf(3), listOf(3))
    }

    should("not throw errors when no transitions in on-clause") {
      // such machine is valid because it can contain actions to execute
      val m = machine<List<Int>, Event> {
        initial = listOf(3) to null
        on(Event.E1::class) {
          action { _, _, _ -> /* do something awesome */ }
        }
      }
      val observer =
        createSubscribedTestObserver(m.states)

      m.send(Event.E1(24))

      observer.assertValues(listOf(3), listOf(3))
    }

    should("process both on and onEach sources when they are mixed") {
      checkAll(Arb.list(Arb.events(), range = 0..20)) { events: List<Event> ->
        val streamedSource = PublishSubject.create<String>()
        val m = machine<List<Int>, Event> {
          initial = listOf(3) to null
          on(Event.E1::class) {
            transitionTo { state, event -> state.plus(event.value) }
          }
          onEach(streamedSource) {
            transitionTo { state, payload -> state.plus(payload.length * 10) }
          }
        }

        val observer =
          createSubscribedTestObserver(m.states)

        events.forEach { event ->
          when (event) {
            is Event.E1 -> m.send(event)
            is Event.E2 -> streamedSource.onNext(event.value)
          }
        }

        val expectedStates = mutableListOf(listOf(3))
        events.mapTo(expectedStates) { event ->
          val state = expectedStates.last()
          when (event) {
            is Event.E1 -> state + event.value
            is Event.E2 -> state + event.value.length * 10
          }
        }
        observer.assertValueSequence(expectedStates)
      }
    }

    should("call transition block only for specific streaming payloads") {
      var firstBlockCallCount = 0
      var secondBlockCallCount = 0
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(10, 20, 30)) {
          transitionTo { state, _ ->
            firstBlockCallCount += 1
            state
          }
        }
        onEach(Observable.just("a", "b", "c")) {
          transitionTo { state, _ ->
            secondBlockCallCount += 1
            state
          }
        }
      }

      val observer =
        createSubscribedTestObserver(m.states)
      observer.awaitTerminalEvent()

      firstBlockCallCount shouldBe 3
      secondBlockCallCount shouldBe 3
    }

    should("subscribe to streamed payloads only once") {
      val count = AtomicInteger(0)
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(10, 20, 30).doOnSubscribe { count.incrementAndGet() }) {
          transitionTo { state, _ -> state }
        }
      }

      createSubscribedTestObserver(m.states)

      count.get() shouldBe 1
    }
  }

  context("actions") {
    should("execute initial action if specified") {
      var executed = false
      val m = machine<Int, Unit> {
        initial = 3 to { executed = true }
      }
      m.states
        .subscribe()

      executed shouldBe true
    }

    should("execute action in onEach-clause on each emission") {
      var count = 0
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(1, 2, 3, 4)) {
          transitionTo { state, _ -> state }
          action { _, _, _ -> count += 1 }
        }
      }

      m.states
        .subscribe()

      count shouldBe 4
    }

    should("execute action in onEach-clause with correct arguments") {
      val arguments: MutableList<ActionArgs<List<Int>, Int>> = arrayListOf()
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(1, 2, 3)) {
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

      m.states
        .subscribe()

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

      m.states
        .subscribe()

      m.send(Event.E2("33"))
      m.send(Event.E1(88))

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

    should("merge actions in the onEach-clause and execute them sequentially") {
      val markers: MutableList<String> = arrayListOf()
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(1, 2)) {
          transitionTo { state, _ -> state }
          action { _, _, _ -> markers.add("action1") }
          action { _, _, _ -> markers.add("action2") }
          action { _, _, _ -> markers.add("action3") }
        }
      }

      m.states
        .subscribe()

      markers shouldContainExactly listOf(
        "action1",
        "action2",
        "action3",
        "action1",
        "action2",
        "action3"
      )
    }

    should("execute action in onEach only once per reduce") {
      var count = 0
      val m = machine<Int, Event> {
        initial = 0 to null

        onEach(Observable.just("1", "2", "3")) {
          action { _, _, _ ->
            count += 1
          }
        }
      }

      m.states
        .subscribe()

      count shouldBe 3
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

      m.states
        .subscribe()
      m.send(Event.E1(1))
      m.send(Event.E1(2))
      m.send(Event.E1(3))

      count shouldBe 3
    }

    should("execute corresponding event transition after onEach-action emits an event") {
      val m = machine<Int, Event> {
        initial = 0 to null

        onEach(Observable.just("he", "llo")) {
          actionWithEvent { _, _, _ ->
            Event.E1(88)
          }
        }

        on(Event.E1::class) {
          transitionTo { state, payload ->
            state + payload.value
          }
        }
      }

      val states = mutableListOf<Int>()
      m.states
        .subscribe { s -> states.add(s) }

      states shouldContainExactly listOf(
        0, // initial
        0, // after reducing "he", event E1 fired as a side-effect
        88, // after receiving E1, reducing it
        88, // after reducing "llo", event E1 fired as a side-effect
        176 // after receiving E1, reducing it
      )
    }

    should("execute corresponding event transition after on-action emits an event") {
      val m = machine<Int, Event> {
        initial = 0 to null

        on(Event.E2::class) {
          actionWithEvent { _, _, _ ->
            Event.E1(88)
          }
        }

        on(Event.E1::class) {
          transitionTo { state, payload ->
            state + payload.value
          }
        }
      }

      val states = mutableListOf<Int>()
      m.states
        .subscribe { s -> states.add(s) }

      m.send(Event.E2("he"))
      m.send(Event.E2("llo"))

      states shouldContainExactly listOf(
        0, // initial
        0, // after reducing "he", event E1 fired as a side-effect
        88, // after receiving E1, reducing it
        88, // after reducing "llo", event E1 fired as a side-effect
        176 // after receiving E1, reducing it
      )
    }

    should("run actions on current thread by default") {
      val testThreadId = Thread.currentThread().id
      val onEachActionThreadId = AtomicLong()
      val onActionThreadId = AtomicLong()
      val onActionWithEventThreadId = AtomicLong()
      val m = machine<Int, Event>() {
        initial = 3 to null
        onEach(Observable.just(10)) {
          action { _, _, _ -> onEachActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E1::class) {
          action { _, _, _ -> onActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E2::class) {
          actionWithEvent { _, _, _ -> onActionWithEventThreadId.set(Thread.currentThread().id); null }
        }
      }

      val observer =
        createSubscribedTestObserver(m.states)
      m.send(Event.E1(33))
      m.send(Event.E2("hello"))

      observer.awaitCount(4)
      onEachActionThreadId.get() shouldBe testThreadId
      onActionThreadId.get() shouldBe testThreadId
      onActionWithEventThreadId.get() shouldBe testThreadId
    }

    should("run actions on specified scheduler") {
      val testThreadId = Thread.currentThread().id
      val onEachActionThreadId = AtomicLong()
      val onActionThreadId = AtomicLong()
      val onActionWithEventThreadId = AtomicLong()
      val m = machine<Int, Event>(Schedulers.newThread()) {
        initial = 3 to null
        onEach(Observable.just(10)) {
          action { _, _, _ -> onEachActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E1::class) {
          action { _, _, _ -> onActionThreadId.set(Thread.currentThread().id) }
        }

        on(Event.E2::class) {
          actionWithEvent { _, _, _ -> onActionWithEventThreadId.set(Thread.currentThread().id); null }
        }
      }

      val observer =
        createSubscribedTestObserver(m.states)
      m.send(Event.E1(33))
      m.send(Event.E2("hello"))

      observer.awaitCount(4)
      onEachActionThreadId.get() shouldNotBe testThreadId
      onActionThreadId.get() shouldNotBe testThreadId
      onActionWithEventThreadId.get() shouldNotBe testThreadId
    }

    should("run actions on specified scheduler and keep source observable thread untouched") {
      val onEachActionThreadId = AtomicLong()
      val sourceThreadId = AtomicLong()
      val m = machine<Int, Event>(Schedulers.single()) {
        initial = 3 to null

        onEach(
          Observable.fromCallable { sourceThreadId.set(Thread.currentThread().id); 10 }.subscribeOn(Schedulers.io())
        ) {
          action { _, _, _ -> onEachActionThreadId.set(Thread.currentThread().id) }
        }
      }

      val observer =
        createSubscribedTestObserver(m.states)

      observer.awaitCount(2)
      onEachActionThreadId.get() shouldNotBe sourceThreadId.get()
    }

    // TODO error when 2 transitionTo blocks
    // TODO no error when no transitions and no actions
  }
})

private fun <T> createSubscribedTestObserver(
  source: Observable<T>,
  assertNoErrors: Boolean = true
): TestObserver<T> {
  return TestObserver<T>().apply {
    source.subscribe(this)
    if (assertNoErrors) {
      assertNoErrors()
    }
  }
}

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
