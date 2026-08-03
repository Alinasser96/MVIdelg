# `:mvi-core`

The reusable half of the blueprint. No DI framework, no networking, no app code — drop the
module into any project and depend on it.

```kotlin
dependencies {
    implementation(project(":mvi-core"))
}
```

Only the `compose` package touches Compose; everything else is plain Kotlin + Coroutines
and works from a JVM module with no UI toolkit.

---

## API surface

### Contracts

| Type | Purpose |
| --- | --- |
| `MviIntent` | Marker for what the user can ask for. |
| `MviState` | Marker for the complete description of a screen. |
| `MviEffect` | Marker for something that must happen exactly once. |

### Base

```kotlin
abstract class MviViewModel<I : MviIntent, S : MviState, E : MviEffect>(initialState: S)
```

Acquires `StateStore`, `EffectEmitter` and `IntentReceiver` by delegation and wires the
intent stream into `handleIntent`. Subclasses implement one function.

| Member | |
| --- | --- |
| `state: StateFlow<S>` | What the UI collects. |
| `currentState: S` | Read state inside the ViewModel. Never from the UI. |
| `updateState { }` | Atomic reduce. **Must be pure** — may run twice under contention. |
| `effect: Flow<E>` | One-shot events. Collect once, via `CollectEffects`. |
| `sendEffect(e)` | Queue an effect. Non-suspending. |
| `onIntent(i)` | Called by the UI. Non-suspending, ordered, never dropped. |
| `handleIntent(i)` *abstract* | Route one intent. **Must not block** — start work, don't await it. |
| `onIntentReceived(i)` *open* | Runs before every intent. The logging / analytics seam. |
| `onIntentError(i, t)` *open* | Catches throws out of `handleIntent`. Default rethrows. |

### Primitives

Each is an interface plus one implementation, usable on its own.

| | |
| --- | --- |
| `StateStore<S>` / `StateStoreDelegate` | A `MutableStateFlow` with the mutable half private. |
| `EffectEmitter<E>` / `EffectEmitterDelegate` | A `Channel` — buffers while nobody collects, delivers to exactly one collector, never replays. |
| `IntentReceiver<I>` / `IntentReceiverDelegate` | A `Channel` of intents. Exposes `intents: Flow<I>` rather than taking a handler, because a delegate expression in a class header cannot reference `this`. |

### Feature delegates

Composed as private fields by a ViewModel, never inherited. All three take a
`CoroutineScope` and expose a `StateFlow` of their own state.

**`PaginationDelegate<T>`** — infinite scrolling.

```kotlin
private val paging = PaginationDelegate(viewModelScope, pageSize = 20) { page, size ->
    repository.users(page, size)
}
```

`refresh()` · `loadNextPage()` · `retry()` · `reset()` → `StateFlow<PagingState<T>>`
(`items`, `page`, `isRefreshing`, `isAppending`, `endReached`, `error`, `isEmpty`).

Guards against double-fetching, cancels in-flight pages on refresh, keeps the pages
already on screen when an append fails, and detects the end from a short page. In-flight
flags are set *synchronously*, so two scroll events in one frame fetch one page.

**`SearchQueryDelegate`** — debounced search.

```kotlin
private val search = SearchQueryDelegate(debounceMillis = 300)
```

`query: StateFlow<String>` drives the text field on every keystroke;
`debouncedQuery: Flow<String>` drives the network once the user pauses. The initial value
is dropped so it never duplicates the request `init` already made.

**`AsyncDelegate<T>`** — one suspending load.

```kotlin
private val user = AsyncDelegate(viewModelScope) { repository.user(id) }
```

`load(force = false)` · `retry()` · `reset()` → `StateFlow<Async<T>>`. Cancels the previous
attempt, carries the old value through `Loading` and `Failure` so the screen doesn't blink
or go blank on a failed refresh, and never swallows `CancellationException`.

**`Async<T>`** — `Idle` · `Loading(previous)` · `Success(value)` · `Failure(error, previous)`,
with `valueOrNull`, `isLoading`, `errorOrNull`. A sealed type instead of three loose
booleans, so "loading and failed" is unrepresentable.

### Compose

```kotlin
CollectEffects(viewModel.effect) { effect -> /* suspend body */ }
```

Lifecycle-aware effect collection. Stops below `STARTED` so navigation can't fire from the
back stack, uses `rememberUpdatedState` so a recreated `NavController` is not captured
forever, and is keyed so a fresh lambda per recomposition doesn't restart collection. The
handler is `suspend`, so `showSnackbar` works directly.

---

## Testing

The delegates take their scope as a parameter, so they test with `runTest`'s own scope —
no `Dispatchers.setMain`, no Android:

```kotlin
@Test
fun `loadNextPage appends`() = runTest {
    val paging = PaginationDelegate(this, pageSize = 3) { page, size -> fakePage(page, size) }
    paging.refresh(); advanceUntilIdle()
    paging.loadNextPage(); advanceUntilIdle()
    assertEquals(6, paging.state.value.items.size)
}
```

Only tests that construct a real `MviViewModel` need `MainDispatcherRule` (in the test
source set), because `viewModelScope` runs on `Dispatchers.Main`.

```bash
./gradlew :mvi-core:test
```
