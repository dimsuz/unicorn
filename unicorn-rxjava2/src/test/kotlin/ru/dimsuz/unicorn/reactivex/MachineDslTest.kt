package ru.dimsuz.unicorn.reactivex

import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicInteger

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
        createSubscribedTestObserver(m.transitionStream)
      observer.assertValue(TransitionResult(3, null))
    }
  }

  context("transitions") {
    should("perform transitions given streamed payloads") {
      val m = machine<List<Int>, Unit> {
        initial = listOf(3) to null
        onEach(Observable.just(10, 20, 30)) {
          transitionTo { state, payload -> state.plus(payload) }
        }
      }
      val observer =
        createSubscribedTestObserver(m.transitionStream.map { it.state })
      observer.assertValues(listOf(3), listOf(3, 10), listOf(3, 10, 20), listOf(3, 10, 20, 30))
    }

    should("perform transitions given discrete events") {
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
        createSubscribedTestObserver(m.transitionStream.map { it.state })

      // Act
      m.send(Event.E2("he"))
      m.send(Event.E1(10))
      m.send(Event.E2("llo"))
      m.send(Event.E1(20))

      // Assert
      observer.assertValues(
        listOf(3) to "",
        listOf(3) to "he",
        listOf(3, 10) to "he",
        listOf(3, 10) to "hello",
        listOf(3, 10, 20) to "hello"
      )
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
        createSubscribedTestObserver(m.transitionStream.map { it.state })
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
        createSubscribedTestObserver(m.transitionStream.map { it.state })

      m.send(Event.E1(24))

      observer.assertValues(listOf(3), listOf(3))
    }

    should("process both on and onEach sources when they are mixed") {
      val streamedSource = PublishSubject.create<String>()
      val m = machine<List<Int>, Event> {
        initial = listOf(3) to null
        on(Event.E1::class) {
          transitionTo { state, event -> state.plus(event.value) }
        }
        onEach(streamedSource) {
          transitionTo { state, payload -> state.plus(payload.toInt() * 10) }
        }
      }

      val observer =
        createSubscribedTestObserver(m.transitionStream.map { it.state })
      m.send(Event.E1(88))
      streamedSource.onNext("1")
      m.send(Event.E1(99))
      streamedSource.onNext("2")

      observer.assertValues(
        listOf(3),
        listOf(3, 88),
        listOf(3, 88, 10),
        listOf(3, 88, 10, 99),
        listOf(3, 88, 10, 99, 20)
      )
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
        createSubscribedTestObserver(m.transitionStream.map { it.state })
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

      createSubscribedTestObserver(m.transitionStream.map { it.state })

      count.get() shouldBe 1
    }
  }

  context("actions") {
    should("execute initial action if specified") {
      var executed = false
      val m = machine<Int, Unit> {
        initial = 3 to { executed = true }
      }
      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }

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

      m.transitionStream
        .subscribe { (_, actions) -> actions?.invoke() }
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
      m.transitionStream
        .subscribe { (s, actions) -> actions?.invoke(); states.add(s) }

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
      m.transitionStream
        .subscribe { (s, actions) -> actions?.invoke(); states.add(s) }

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
