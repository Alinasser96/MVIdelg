# `:mvi-core`

The library. Five files, no plugins, no DI framework, no networking, no app code.

```kotlin
dependencies {
    implementation(project(":mvi-core"))
}
```

Only `compose/` touches Compose; the rest is plain Kotlin + Coroutines and works from a
JVM module with no UI toolkit.

```
MviMarkers.kt          ViewState · Intent · Effect · NoEffect
MviViewModel.kt        the loop
plugins/MVIPlugin.kt   the extension interface — no implementations
plugins/PluginLookup.kt requirePlugin() · pluginOrNull()
compose/MviCompose.kt  CollectEffects
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

```kotlin
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect>(
    private val plugins: List<MVIPlugin> = emptyList(),
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
| `pluginOrNull(KClass)` | Find an installed plugin. Backs the reified helpers below. |

Intents arrive on a `Channel(UNLIMITED)`. Every plugin sees every intent via
`plugins.map { it.onIntent(intent) }.any { it }` — mapped before reduced, so a plugin's
visibility never depends on its position in the list — and any plugin returning `true`
consumes the intent so `handleIntent` never sees it.

Plugins are set up in `init`, before any subclass `init` runs, so they are usable from a
subclass constructor. Only `initialState()` is deferred, because calling an abstract member
from the base constructor would run before the subclass finished initializing.

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
    get() = (this as MviViewModel<*, *, *>).requirePlugin()
```

Because the accessor extends the marker, `loading` only resolves inside a class that
declared it — a compile error rather than a null. Install by passing an instance:

```kotlin
class ProfileViewModel(plugins: List<MVIPlugin>) :
    MviViewModel<S, I, E>(plugins), HasLoadingPlugin
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
