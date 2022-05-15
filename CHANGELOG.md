# Changelog

## 2.1.0 - 2022-05-15
* Remove built-in support for events

    Previously this was nice to have bundled in, but now that `transitionTo`
    and `action` are suspend functions, nothing stops you from easily doing
    something like

    ```kotlin
    val events = MutableSharedFlow<Event>(extraBufferCapacity = 12)

    machine<State> {
      onEach(events) {
        action {
          events.emit(Event.Another)
        }
      }
    }
    ```

## 2.0.0 - 2022-05-14
* Remove `unicorn-lint` sub-project. It wasn't finished, wasn't used, wasn't working.
* Remove `unicorn-rxjava2` support. Use coroutine's rxjava adapters if you need to work with RxJava streams
* Add support for nested states, expressed through a `sealed class`
* `transitionTo` and `action` are now `suspend`-functions

## 0.11.0 - 2022-02-22
* Add `initial` property to the `Machine` interface

## 0.10.0 - 2021-12-09
* Made it possible to have a nullable payload in `onEach` blocks for coroutine-based machines

## 0.9.1 - 2020-05-25

* Added coroutines module/artifact
* Actions are now executed internally, only state stream is present in the API
