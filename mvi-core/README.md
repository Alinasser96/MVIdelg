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
    additionalPlugins: List<MVIPlugin> = emptyList(),
) : ViewModel()
```

| Member | |
| --- | --- |
| `viewState: StateFlow<T>` | What the UI collects. |
| `currentState: T` | Read state inside the ViewModel. Never from the UI. |
| `effect: Flow<E>` | One-shot events, over a `Channel`. Buffered, delivered once, never replayed. |
| `processIntent(i)` | Called by the UI. Non-suspending, ordered, never dropped. |
| `initialState(): T` *abstract* | The state before anything loads. Called lazily. |
| `handleIntent(i)` *abstract, suspend* | Route one intent. **May suspend** — intents are serialized, so awaiting is correct. |
| `updateState { }` *protected* | Atomic reduce via `getAndUpdate`, then broadcast `onStateChanged`. The reducer must be **pure** — it may run twice. |
| `emitEffect(e)` *protected* | Queue an effect, then broadcast `onEffectEmitted`. Non-suspending. |

Intents arrive on a `Channel(UNLIMITED)`. Every plugin sees every intent via
`plugins.map { it.onIntent(intent) }.any { it }` — mapped before reduced, so a plugin's
visibility never depends on its position in the list — and any plugin returning `true`
consumes the intent so `handleIntent` never sees it.

Plugins are created in `init`, before any subclass `init` runs, so they are usable from a
subclass constructor. Only `initialState()` is deferred, because calling an abstract member
from the base constructor would run before the subclass finished initializing.

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

## Adding a plugin from outside this module

No changes to `:mvi-core` required.

```kotlin
class UndoPlugin : MVIPlugin { /* override the hooks you need */ }

interface HasUndoPlugin

val HasUndoPlugin.undo: UndoPlugin
    get() = (this as MviViewModel<*, *, *>).requirePlugin()

class EditorViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
    undoPlugin: UndoPlugin = UndoPlugin(),
) : MviViewModel<S, I, E>(dispatcherProvider, pluginDependencies, listOf(undoPlugin)),
    HasUndoPlugin
```

| Lookup | |
| --- | --- |
| `requirePlugin<P>()` | The installed plugin, or throws naming the missing type. |
| `pluginOrNull<P>()` | Null when not installed, for genuinely optional plugins. |
| `pluginOrNull(KClass<P>)` | Non-reified member the two above delegate to. |

Worked example, written from a consumer module:
[CustomPluginTest.kt](../app/src/test/java/com/example/mvi/CustomPluginTest.kt).

**Only edit `:mvi-core`** to get marker-alone auto-installation like the built-in four —
add the marker to `PluginMarkers.kt`, the accessor to `PluginAccessors.kt`, and the install
line plus `plugins` entry in `MviViewModel`. That saves the explicit `additionalPlugins`
argument, at the cost of a base-class change and a new `:mvi-core` dependency.

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
