# `:mvi-core`

The library. Six files, no plugins, no DI framework, no networking, no app code.

```kotlin
dependencies {
    implementation(project(":mvi-core"))
}
```

Only `compose/` touches Compose; the rest is plain Kotlin + Coroutines and works from a
JVM module with no UI toolkit.

```
MviMarkers.kt                     ViewState · Intent · Effect · NoEffect
MviViewModel.kt                   the loop + four seams — no plugin concept at all
plugins/MVIPlugin.kt              the extension interface — no implementations
plugins/PluggableMviViewModel.kt  MviViewModel with the seams wired to a plugin list
plugins/PluginLookup.kt           requirePlugin() · pluginOrNull()
compose/MviCompose.kt             CollectEffects
```

**There are deliberately no built-in plugins.** Loading, errors, navigation and analytics
are app concerns; `:app/plugins` shows one way to write them.

---

## Contracts

| Type | Purpose |
| --- | --- |
| `ViewState` | The complete description of one screen. |
| `Intent` | What the user can ask for. |
| `Effect` | A one-shot UI event. |
| `NoEffect` | Use as the `Effect` type for screens that emit none. |

## `MviViewModel<T : ViewState, I : Intent, E : Effect>`

The loop. **No constructor arguments, and no notion of a plugin.**

```kotlin
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect> : ViewModel()
```

| Member | |
| --- | --- |
| `viewState: StateFlow<T>` | What the UI collects. |
| `currentState: T` | Read state inside the ViewModel. Never from the UI. |
| `effect: Flow<E>` | One-shot events, over a `Channel`. Buffered, delivered once, never replayed. |
| `processIntent(i)` | Called by the UI. Non-suspending, ordered, never dropped. |
| `initialState(): T` *abstract* | The state before anything loads. Called lazily. |
| `handleIntent(i)` *abstract, suspend* | Route one intent. **May suspend** — intents are serialized, so awaiting is correct. |
| `updateState { }` *protected* | Atomic reduce via `getAndUpdate`, then run `afterStateUpdate`. The reducer must be **pure** — it may run twice. |
| `emitEffect(e)` *protected* | Queue an effect, then run `afterEffect`. Non-suspending. |

Intents arrive on a `Channel(UNLIMITED)` and are handled one at a time, in order. Only
`initialState()` is deferred, because calling an abstract member from the constructor would
run before the subclass finished initializing.

### Seams

Four `protected open` no-ops, typed in your own `T`/`I`/`E`. Override directly for one-off
behaviour, or extend `PluggableMviViewModel` to have them driven by plugins.

| Seam | Runs |
| --- | --- |
| `interceptIntent(intent: I): Boolean` | Before `handleIntent`. Return true to swallow the intent |
| `afterStateUpdate(oldState: T, newState: T)` | After every `updateState` |
| `afterEffect(effect: E)` | After every `emitEffect` |
| `onDispose()` | With `onCleared`, after both channels close |

## `PluggableMviViewModel<T, I, E>`

`MviViewModel` with the four seams forwarded to a list of plugins. That is its entire
contents.

```kotlin
abstract class PluggableMviViewModel<T : ViewState, I : Intent, E : Effect>(
    private val plugins: List<MVIPlugin>,
) : MviViewModel<T, I, E>()
```

Adds one member: `pluginOrNull(KClass)`, which backs the reified helpers below.

Every plugin sees every intent via `plugins.map { it.onIntent(intent) }.any { it }` —
mapped before reduced, so a plugin's visibility never depends on its position in the list.

Plugins are set up in **this class's** `init`, which is the reason the plugin wiring lives
here rather than in the base: `plugins` is not yet assigned while the base's own `init`
runs. They are therefore ready before any subclass `init`, so a subclass can use one from
its own constructor.

## `MVIPlugin`

```kotlin
interface MVIPlugin {
    fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope) {}
    fun onIntent(intent: Intent): Boolean = false   // true consumes the intent
    fun onStateChanged(oldState: ViewState, newState: ViewState) {}
    fun onEffectEmitted(effect: Effect) {}
    fun onCleared() {}
}
```

Every hook has a default. A plugin takes what it needs in its own constructor — there is
no dependency container here, and the ViewModel that installs a plugin supplies it.

Note a consuming plugin **cannot change screen state**: `updateState` belongs to the
ViewModel. Consumption suits guards, gating and interception.

## Lookup

| | |
| --- | --- |
| `requirePlugin<P>()` | The installed plugin, or throws naming the missing type. |
| `pluginOrNull<P>()` | Null when not installed, for genuinely optional plugins. |
| `pluginOrNull(KClass<P>)` | Non-reified member the two above delegate to. |

The pattern these exist for — three pieces, all in your module:

```kotlin
class LoadingPlugin : MVIPlugin { ... }                 // 1. the capability

interface HasLoadingPlugin                              // 2. the marker

val HasLoadingPlugin.loading: LoadingPlugin             // 3. the accessor
    get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()
```

Because the accessor extends the marker, `loading` only resolves inside a class that
declared it — a compile error rather than a null. Install by passing an instance:

```kotlin
class ProfileViewModel :
    PluggableMviViewModel<S, I, E>(listOf(LoadingPlugin())), HasLoadingPlugin
```

Worked example: [CustomPluginTest.kt](../app/src/test/java/com/example/mvi/CustomPluginTest.kt).
Four production-shaped ones: [app/…/plugins/](../app/src/main/java/com/example/mvi/plugins).

## Compose

```kotlin
CollectEffects(viewModel.effect) { effect -> /* suspend body */ }
```

Lifecycle-aware effect collection. Stops below `STARTED`, uses `rememberUpdatedState` so a
recreated callback isn't captured forever, and is keyed so a fresh lambda per recomposition
doesn't restart collection.

## Testing

```bash
./gradlew :mvi-core:test
```

Plugins are plain classes, so each tests on its own with no ViewModel and no Android.
Tests that construct a real `MviViewModel` need `MainDispatcherRule`, because
`viewModelScope` runs on `Dispatchers.Main`.
