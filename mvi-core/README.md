# `:mvi-core`

The reusable half of the blueprint. No DI framework, no networking, no app code.

```kotlin
dependencies {
    implementation(project(":mvi-core"))
}
```

Only the `compose` package and `CollectNavigation` touch Compose; everything else is plain
Kotlin + Coroutines and works from a JVM module with no UI toolkit.

---

## Contracts

| Type | Purpose |
| --- | --- |
| `ViewState` | The complete description of one screen. |
| `Intent` | What the user can ask for. |
| `Effect` | A one-shot UI event. |
| `NoEffect` | Use as the `Effect` type for screens that emit none. |

## `MviViewModel<T : ViewState, I : Intent, E : Effect>`

The parent class. Owns the loop; installs plugins by marker.

```kotlin
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect>(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : ViewModel()
```

| Member | |
| --- | --- |
| `viewState: StateFlow<T>` | What the UI collects. **`by lazy`** — first access starts plugin `onCreate` and the intent collector. |
| `currentState: T` | Read state inside the ViewModel. Never from the UI. |
| `effect: SharedFlow<E>` | One-shot events. No replay, no buffer. |
| `processIntent(i)` | Called by the UI. Non-suspending, ordered, never dropped. |
| `initialState(): T` *abstract* | The state before anything loads. |
| `handleIntent(i)` *abstract, suspend* | Route one intent. **May suspend** — intents are serialized, so awaiting is correct. |
| `updateState { }` *protected* | Reduce, then broadcast `onStateChanged` to plugins. |
| `emitEffect(e)` *protected* | Emit on `dispatcherProvider.main`, then broadcast `onEffectEmitted`. |

Intents arrive on a `Channel(UNLIMITED)`. Plugins get first look via
`plugins.any { it.onIntent(intent) }`; a plugin returning `true` consumes the intent and
`handleIntent` never sees it.

## Plugins

```kotlin
interface MVIPlugin {
    fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope, dependencies: MviPluginDependencies) {}
    fun onIntent(intent: Intent): Boolean = false
    fun onStateChanged(oldState: ViewState, newState: ViewState) {}
    fun onEffectEmitted(effect: Effect) {}
    fun onCleared() {}
}
```

Install by implementing the marker; reach it through the accessor.

| Marker | Accessor | Plugin |
| --- | --- | --- |
| `HasLoadingPlugin` | `loading` | `LoadingPluginImpl` |
| `HasErrorPlugin` | `errors` | `ErrorPluginImpl` |
| `HasNavigationPlugin` | `navigation` | `NavigationPluginImpl` |
| `HasLoggingPlugin` | `logging` + `configureLogging(screenId)` | `LoggingPluginImpl` |

The accessors are extension properties **on the markers**, so an accessor only resolves
inside a class that declared the marker. Forgetting it is a compile error, not a runtime null.

**`LoadingPluginImpl`** — `isLoading: StateFlow<Boolean>`, `show()`, `hide()`,
`withLoading { }`. Prefer `withLoading`: its `finally` clears the spinner even when the
block throws or is cancelled.

**`ErrorPluginImpl`** — `error: StateFlow<OperationError?>`, `setError`, `clearError`,
`runCatchingError { }` (returns null on failure, rethrows `CancellationException`).

**`NavigationPluginImpl`** — `navigateTo(destination)`, `navigateBack(results)`,
`navigateBackWithResults(results)`, `popUpTo(routeClass, results, inclusive)`. Sends
`NavCommand`s to the app-wide `Navigator`; ViewModels never touch a `NavController`.

**`LoggingPluginImpl`** — `configure(screenId)` once, then `logButtonEvent(id)` /
`logFieldEvent(id)`. Logging before configuring throws, so events can't ship with a blank
screen id.

**`MviPluginDependencies`** — the one bag handed to every plugin's `onCreate`, carrying
`navigator`, `analyticsLogger` and `dispatcherProvider`. One bag rather than per-plugin
constructor args is what keeps installation automatic.

## Compose

```kotlin
CollectEffects(viewModel.effect) { effect -> /* suspend body */ }   // lifecycle-aware
CollectNavigation(navigator) { command -> /* apply to the NavHost */ }  // once, at the host
```

---

## Adding a fifth plugin

1. Implement `MVIPlugin`.
2. Add the marker to `PluginMarkers.kt`.
3. Add the accessor to `PluginAccessors.kt`.
4. Add the install line and `plugins` list entry in `MviViewModel`.

Step 4 means the plugin set is **closed** — a feature module cannot register its own. That
is the deliberate trade for zero per-screen wiring and non-null accessors.

---

## Testing

Every plugin is a plain class, so each tests on its own with no ViewModel and no Android:

```kotlin
@Test
fun `withLoading clears the spinner even when the block throws`() = runTest {
    val plugin = LoadingPluginImpl()
    runCatching { plugin.withLoading { error("boom") } }
    assertFalse(plugin.isLoading.value)
}
```

Tests that construct a real `MviViewModel` need `MainDispatcherRule` (`viewModelScope` runs
on `Dispatchers.Main`) and must touch `viewState` once to trigger the lazy init before
sending intents.

```bash
./gradlew :mvi-core:test
```
