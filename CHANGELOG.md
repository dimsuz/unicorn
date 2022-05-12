# Changelog

## 2.0.0 - TODO
* Remove `unicorn-lint` sub-project. It wasn't finished, wasn't used, wasn't working.
* Remove `unicorn-rxjava2` support. Use coroutine's rxjava adapters if you need to work with RxJava streams
* Add support for nested states, expressed through a `sealed class`

## 0.11.0 - 2022-02-22
* Add `initial` property to the `Machine` interface

## 0.10.0 - 2021-12-09
* Made it possible to have a nullable payload in `onEach` blocks for coroutine-based machines

## 0.9.1 - 2020-05-25

* Added coroutines module/artifact
* Actions are now executed internally, only state stream is present in the API
