# RemoteData

A data fetching, caching, state management, and side-effect (action) library for **Kotlin Multiplatform**, inspired by React Query and TanStack Query. It simplifies fetching, caching, persistent storage, retries, reactive parameter flows, and synchronizing asynchronous data and mutations across KMP applications.

---

## Features

- 🚀 **Declarative Data State**: Represents asynchronous states using `DataResult` (`Empty`, `Loading`, `Success`, `Error`) with full support for preserving stale data during background refreshes.
- 🔄 **Stale-While-Revalidate (`DataObserver`)**: Auto-refetch stale data whenever UI components observe data, adhering to configurable `staleTime`.
- 🌊 **Reactive Parameter Flows (`DataFlow`)**: Automatically switch and observe cached data instances dynamically based on a parameter `Flow<Params>`.
- 🔁 **Automatic Background Reloading**: Periodic background refetching on a configurable `reloadInterval` when active subscribers are present.
- 💾 **Persistence Support**: Automatic cross-session caching backed by `DataPersister` and `kotlinx.serialization`.
- ⚡ **Retry Mechanism**: Exponential backoff or custom retry delay strategies for failed data loaders.
- 🎯 **Mutations & Side-Effects (`Action` & `ActionRunner`)**: Execute async side-effects with concurrent execution strategies (`JOIN`, `CANCEL`, `QUEUE`) and scope-bound runners supporting lifecycle callbacks (`onSuccess`, `onError`, `onCancel`, `onSettled`).
- 🌐 **Kotlin Multiplatform**: Supports Android, iOS (Arm64 & Simulator), JVM, JS, Wasm (`wasmJs`), and Linux (`linuxX64`) targets.
- ⚛️ **Reactive & Thread-Safe**: Fully asynchronous, powered by Kotlin Coroutines and `StateFlow`.

---

## Installation

Add the `remotedata` dependency to your `commonMain` source set in `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.asnaeb:remotedata:1.0.0-alpha03")
        }
    }
}
```

*Note: If you plan to use automatic data persistence, ensure the `kotlinx.serialization` plugin is applied in your project:*

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.1.10" // or your Kotlin version
}
```

---

## Detailed Usage Guide & Examples

### 1. Initialize the `Registry`

The `Registry` is the central owner of memory caches, coroutine scopes, and default configurations for all `Data` and `Action` instances.

```kotlin
import io.github.asnaeb.remotedata.Registry
import io.github.asnaeb.remotedata.ConcurrentActionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val registry = Registry(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    defaultDataLoadOnInit = false,
    defaultDataStaleTime = 5.minutes,
    defaultDataReloadInterval = null,
    defaultDataMaxRetries = 0,
    defaultDataRetryDelay = { attempt -> 1.seconds * (1 shl attempt) }, // Exponential backoff
    defaultDataPersister = null,
    defaultActionStrategy = ConcurrentActionStrategy.JOIN
)
```

---

### 2. Fetching & Declaring Data

Data loading is defined using `Registry` extensions. RemoteData supports both static (parameterless) data and dynamic (parameterized) data.

#### Static Data (`registry.data`)

Use `registry.data` for parameterless data definitions (e.g., app config, current user profile).

```kotlin
import io.github.asnaeb.remotedata.Data
import io.github.asnaeb.remotedata.data

val appConfig: Data<String, AppConfig, Unit> = registry.data(
    key = "app_config",
    reloadInterval = 10.minutes,
    maxRetries = 3,
    loader = { api.fetchAppConfig() }
)
```

#### Dynamic / Parameterized Data (`registry.factory`)

Use `registry.factory` for data that depends on runtime parameters (e.g., user profiles by ID, search queries). It returns a `Factory<Key, Data, Params>` object.

```kotlin
import io.github.asnaeb.remotedata.Factory
import io.github.asnaeb.remotedata.Data
import io.github.asnaeb.remotedata.factory

// Declare the factory once
val userFactory: Factory<String, UserProfile, String> = registry.factory(
    key = "user_by_id",
    maxRetries = 2,
    loader = { userId: String -> api.fetchUser(userId) }
)

// Obtain or create a cached Data instance for a specific parameter
val userData: Data<String, UserProfile, String> = userFactory.data("user_123")
```

#### Reactive Parameter Flows (`DataFlow`)

When UI screens change selected IDs dynamically (e.g., user changing selection, search input flow), use `factory.dataFlow(...)` to bind cached data directly to a `Flow<Params>`. `DataFlow` automatically switches the active underlying data cache whenever the parameter flow emits a new value.

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import io.github.asnaeb.remotedata.DataFlow

val selectedUserId = MutableStateFlow("user_123")

// DataFlow reactively switches data source when selectedUserId changes
val userFlow: DataFlow<String, UserProfile, String> = userFactory.dataFlow(selectedUserId)

// Now observe userFlow directly in UI or ViewModel
```

---

### 3. Observing Data State (`DataObserver` & `DataFlowObserver`)

Call `.observe(scope, ...)` on a `Data` or `DataFlow` instance to attach a lifecycle-bound observer. Observing `observer.current` automatically checks data staleness and triggers a background fetch if the data is missing or stale.

```kotlin
import io.github.asnaeb.remotedata.DataObserver
import io.github.asnaeb.remotedata.DataResult

// Create observer in ViewModel scope
val userObserver: DataObserver<String, UserProfile, String> = userData.observe(
    scope = viewModelScope,
    staleTime = 2.minutes,
    loadOnInit = true
)

// Observe current state reactively (Compose example)
val state by userObserver.current.collectAsState()

when (state) {
    is DataResult.Empty -> Text("No data loaded yet.")
    is DataResult.Loading -> {
        // Display loading UI, option to show stale content if reloading
        val cachedUser = state.data()
        if (cachedUser != null) {
            Text("Refreshing profile for ${cachedUser.name}...")
        } else {
            CircularProgressIndicator()
        }
    }
    is DataResult.Success -> Text("Welcome back, ${state.data.name}!")
    is DataResult.Error -> Text("Failed to load: ${state.error.message}")
}
```

#### Understanding `DataResult<T>` State Helpers

`DataResult` provides convenience extension and member functions to easily extract status and cached values regardless of the active state variant:

```kotlin
val data: UserProfile? = state.data()               // Returns data if Success, or stale data if Loading/Error
val error: Throwable? = state.error()              // Returns exception if Error state, else null
val isLoading: Boolean = state.loading()           // true if currently in Loading state
val isInitializing: Boolean = state.initializing() // true if Loading for the first time (no prior cached data)
val isReloading: Boolean = state.reloading()       // true if Loading while preserving previous stale data
val isEmpty: Boolean = state.isEmpty()             // true if in Empty state
val lastLoaded: Instant? = state.lastLoaded()      // Timestamp when data was successfully loaded
```

---

### 4. Cache Operations & Data Handles (`DataHandle`)

Both `Data` and `DataObserver` implement `DataHandle<Data>`, providing unified control over background fetching, manual invalidation, and optimistic updates:

```kotlin
// Fire-and-forget background fetch
userData.load()

// Suspending fetch returning latest DataResult
val result: DataResult<UserProfile> = userData.loadSuspending()

// Ensure data is available (returns cached if valid, fetches if missing, throws on error)
val profile: UserProfile? = userData.ensure()

// Optimistic updates
userData.setData(updatedProfile) // Set value directly
userData.setData { current -> current?.copy(name = "Updated Name") } // Transform value

// Invalidate cache (marks timestamp as stale; next observer/load check re-fetches)
userData.invalidate()

// Cancel ongoing background fetch
userData.cancel()

// Reset state back to DataResult.Empty()
userData.clear()
```

---

### 5. Mutations & Side-Effects (`Action` & `ActionRunner`)

Use `Action` to manage async side-effects such as `POST`, `PUT`, or `DELETE` requests, with concurrency control and scope-bound execution.

#### Registering an `Action`

```kotlin
import io.github.asnaeb.remotedata.Action
import io.github.asnaeb.remotedata.ConcurrentActionStrategy

val updateUserAction: Action<UpdateUserParams, UserProfile> = registry.action(
    key = "update_user_profile",
    strategy = ConcurrentActionStrategy.JOIN,
    action = { params -> api.updateUserProfile(params) }
)
```

#### Concurrent Action Strategies

- **`JOIN`** *(Default)*: Joins and reuses an ongoing execution if the action is already running for the exact same parameters.
- **`CANCEL`**: Cancels any active in-flight execution for the same parameters and starts a new one immediately.
- **`QUEUE`**: Queues the new execution to run sequentially after the active one finishes.

#### Executing Actions with `ActionRunner`

Create a scope-bound `ActionRunner` for convenient execution, callback handling, and UI state binding.

```kotlin
import io.github.asnaeb.remotedata.ActionRunner
import io.github.asnaeb.remotedata.ActionResult

// Create runner in ViewModel or UI scope
val runner: ActionRunner<UpdateUserParams, UserProfile> = updateUserAction.runner(viewModelScope)

// 1. Asynchronous execution with callbacks
runner.run(
    params = UpdateUserParams(id = "user_123", name = "Jane Doe"),
    onSuccess = { updatedProfile ->
        // Optimistically update local data cache on success
        userData.setData(updatedProfile)
    },
    onError = { error ->
        showSnackbar("Update failed: ${error.message}")
    },
    onCancel = {
        println("Action was cancelled.")
    },
    onSettled = {
        println("Action execution completed.")
    }
)

// 2. Suspending execution
val updated: UserProfile? = runner.runSuspending(params)

// 3. Reactively observe the last executed action result in UI
val actionState: ActionResult<UpdateUserParams, UserProfile>? by runner.currentResult.collectAsState(initial = null)

when (actionState) {
    is ActionResult.Loading -> CircularProgressIndicator()
    is ActionResult.Success -> Text("Profile updated successfully!")
    is ActionResult.Error -> Button(onClick = { (actionState as ActionResult.Error).retry() }) {
        Text("Retry Update")
    }
    null -> Unit
}
```

#### Parameter-Keyed Results Map

`Action.results` exposes a `StateFlow<Map<Params, ActionResult<Params, Data>>>` mapping every parameter set to its latest `ActionResult`:

```kotlin
val resultsMap by updateUserAction.results.collectAsState()
val user123Status = resultsMap[UpdateUserParams(id = "user_123", name = "Jane Doe")]
```

---

### 6. Persistence & Serialization

`Data` can automatically persist cached items across application restarts using `DataPersister` and `kotlinx.serialization`.

#### Custom `DataPersister` Implementation

Implement `DataPersister` to connect RemoteData with your preferred storage solution (e.g. Key-Value Storage, MMKV, Files, Preferences DataStore):

```kotlin
import io.github.asnaeb.remotedata.DataPersister
import io.github.asnaeb.remotedata.DataKey
import io.github.asnaeb.remotedata.DataSerializer
import io.github.asnaeb.remotedata.PersistentData

class StorageDataPersister(private val storage: KeyValueStorage) : DataPersister {
    override suspend fun <Key : Any, Data, Params> write(
        key: DataKey<Key, Params>,
        data: PersistentData<Data>,
        serializer: DataSerializer<Key, Data, Params>
    ) {
        val stringKey = serializer.serializeKeyToJson(key)
        val jsonPayload = serializer.serializeDataToJson(data)
        storage.putString(stringKey, jsonPayload)
    }

    override suspend fun <Key : Any, Data, Params> read(
        key: DataKey<Key, Params>,
        serializer: DataSerializer<Key, Data, Params>
    ): PersistentData<Data>? {
        val stringKey = serializer.serializeKeyToJson(key)
        val jsonPayload = storage.getString(stringKey) ?: return null
        return serializer.deserializeDataFromJson(jsonPayload)
    }

    override fun onWriteError(error: Throwable) {
        println("Failed to persist data: ${error.message}")
    }

    override fun onReadError(error: Throwable) {
        println("Failed to read persisted data: ${error.message}")
    }
}
```

Pass your `DataPersister` instance to `Registry` to make it the default for all data calls, or pass it directly to individual `factory` or `data` definitions.

---

### 7. Cache Management & Lifecycle

`Registry` provides methods for removing or inspecting cached items:

```kotlin
// Inspect or update matching keys in the registry
registry.forEachMatchingKey("user_by_id", "app_config") { data ->
    data.invalidate()
}

// Remove cached data and actions for a specific key
registry.remove("user_by_id")

// Clear all cached RemoteData and RemoteAction instances in the registry
registry.clear()
```

---

## API Reference Overview

### Core Types & Interfaces

| Type / Interface | Description |
|---|---|
| `DataResult<T>` | Sealed interface (`Empty`, `Loading`, `Success`, `Error`) representing data fetching state. |
| `ActionResult<Params, Data>` | Sealed interface (`Loading`, `Success`, `Error`) representing parameter-scoped action state. |
| `DataHandle<Data>` | Common interface implemented by `Data` and `DataObserver`. |
| `DataFlowHandle<Data>` | Common interface implemented by `DataFlow` and `DataFlowObserver`. |
| `ActionHandle<Params, Data>` | Interface implemented by `Action` and `ActionRunner`. |
| `Data<Key, Data, Params>` | Core reactive cached data container. |
| `DataObserver<Key, Data, Params>` | Scope-bound observer auto-refetching stale data on subscription. |
| `DataFlow<Key, Data, Params>` | Dynamic reactive data flow driven by a parameter `Flow<Params>`. |
| `DataFlowObserver<Key, Data, Params>` | Scope-bound observer wrapping a `DataFlow`. |
| `Factory<Key, Data, Params>` | Factory producing `Data` and `DataFlow` instances for specific parameters. |
| `Action<Params, Data>` | Mutation handler supporting concurrency strategies (`JOIN`, `CANCEL`, `QUEUE`). |
| `ActionRunner<Params, Data>` | Scope-bound executor for `Action` supporting async callbacks (`onSuccess`, `onError`, `onCancel`, `onSettled`). |
| `DataPersister` | Custom persistence storage interface backed by `DataSerializer`. |
| `Registry` | Central cache registry and factory manager for `Data` and `Action` instances. |

---

## License

RemoteData is licensed under the [Apache License 2.0](LICENSE).
