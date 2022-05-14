# Unicorn

A DSL for state machines based on reactive sources

# How to use

🚧 Section is under construction 🚧

## Simple state sample

``` kotlin
data class State(
  val name: String,
  val totalPrice: Int,
)

val namesSource = flowOf("Jacob", "Wacob", "Petrob")
val pricesSource = flowOf(160, 170, 115)

val m = machine<State, Unit> {
  onEach(namesSource) {
    transitionTo { state, name ->
      state.copy(name = name)
    }

    action { _, _, name ->
      println("received $name")
    }
  }

  onEach(pricesSource) {
    transitionTo { state, price ->
      state.copy(totalPrice = price + state.price)
    }
  }
}

fun main() {
  runBlocking {
    m.states.collect { "state was updated to $it" }
  }
}

```

## Sealed class state sample

``` kotlin
sealed class ViewState {
  object Loading : ViewState()
  data class Error(val text: String): ViewState()
  data class Content(val title: String, val subtitle: String) : ViewState()
}

val contentSource = flowOf("Hello" to "World")
val errorSource = emptyFlow<Throwable>() // empty... for now... ;)
val niceThingsSource = flowOf("Friends", "Trees", "Sky")

val screenMachine = machine<ViewState, Unit> {
  initial = ViewState.Loading to { /* no initial action */ }

  whenIn<ViewState.Loading> {
    onEach(contentSource) {
      transitionTo { state, (title, subtitle) ->
        ViewState.Content(title, subtitle)
      }

      action { 
        // analytics.record("received content")
      }
    }

    onEach(errorSource) {
      transitionTo { state, error ->
        ViewState.Error(error.message ?: "unknown error")
      }

      action { state, newState, error ->
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

fun main() {
  runBlocking {
    screenMachine.states.collect(::render)
  }
}

```

## Non reactive discreet events

``` kotlin
data class State(
  val name: String,
  val price: Int,
)

enum class Event { 
  DiscreteEventPriceChange,
  Random
}

val m = machine<State, Unit> {
  onEach(flowOf("Jacob", "Wacob", "Cheesecake")) {
    transitionTo { state, name ->
      state.copy(name = name)
    }

    action { _, _, name ->
      if (name == "Cheesecake") {
        sendEvent(Unit)
      }
    }
  }
  
  onEach(events.filter { it == Event.DiscreteEventPriceChange }) {
    transitionTo { state, event -> 
      state.copy(price = 160)
    }
  }
}
```

# Download

The library is available on Maven Central:

```
implementation "ru.dimsuz:unicorn2:2.0.0"
```

# License

```
Copyright 2021 Dmitry Suzdalev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
