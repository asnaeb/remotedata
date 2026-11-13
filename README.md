# RemoteData

A data fetching, caching, and state management library for Kotlin Multiplatform, inspired by React Query and TanStack Query. It simplifies fetching, caching, persistent storage, retries, and synchronizing asynchronous data and actions in KMP applications.

## Features

- 🚀 **Declarative Data Fetching**: Easily manage loading, success, error, initializing, and reloading states via `DataResult`.
- 🔄 **Stale-While-Revalidate & Auto-Observer**: Auto-refetch stale data on UI observation via `RemoteDataObserver`.
- 🔁 **Automatic Background Reloading**: Periodically re-fetch data on a configurable `reloadInterval`.
- 💾 **Persistence Support**: Seamless persistent data caching with `RemoteDataPersister` and `kotlinx.serialization`.
- ⚡ **Retry Mechanism**: Built-in automatic retry logic with configurable delay and retry counts.
- 🎯 **Mutations & Actions (`RemoteAction`)**: Execute async side-effects with concurrent strategies (`JOIN`, `CANCEL`, `QUEUE`) and callback-based runners (`RemoteActionRunner`).
- 🌐 **Kotlin Multiplatform**: Supports Android, iOS, JVM, JS, Wasm, and Linux targets.
- ⚛️ **Reactive StateFlow**: Fully asynchronous and reactive using Kotlin Coroutines and `StateFlow`.

---

## Installation

Add the following to your `commonMain` dependencies in your `build.gradle.kts`:

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation("io.github.asnaeb:remotedata:1.0.0-alpha01")
    }
}
```

*Note: If you plan to use data persistence features, ensure the `kotlinx.serialization` plugin is also applied in your build configuration.*

---

## Getting Started

### 1. Initialize the `RemoteDataRegistry`

The `RemoteDataRegistry` manages caching, execution scopes, and default configurations for all `RemoteData` and `RemoteAction` instances.

```kotlin
val registry = RemoteDataRegistry(
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    defaultLoadOnInit = false,
    defaultStaleTime = 5.minutes,
    defaultReloadInterval = null,
    defaultMaxRetries = 3,
    defaultRetryDelay = { attempt -> 1.seconds * (1 shl attempt) },
    defaultPersister = myPersister,
    defaultActionStrategy = ConcurrentActionStrategy.JOIN
)
```

---

### 2. Fetching Data

Data declarations are created using factory functions registered on the `RemoteDataRegistry`.

#### Dynamic / Parameterized Data (`dataFactory`)

Use `dataFactory` for data that depends on runtime parameters. It returns a factory function `(Params) -> RemoteData<Key, Data, Params>`.

```kotlin
// Define the data factory
val fetchUser = registry.dataFactory<String, User, String>(
    key = "user_by_id",
    loader = { userId -> api.getUser(userId) }
)

// Retrieve the cached RemoteData instance for a specific parameter
val userData: RemoteData<String, User, String> = fetchUser("user_123")
```

#### Static Data (`data`)

Use `data` for static or parameterless data.

```kotlin
val appConfig: RemoteData<String, AppConfig, Unit> = registry.data(
    key = "app_config",
    reloadInterval = 10.minutes,
    loader = { api.getAppConfig() }
)
```

---

### 3. Observing Data State (`RemoteDataObserver`)

Use `observe(scope, ...)` to create a `RemoteDataObserver` bound to your UI or lifecycle scope. Subscribing to its `current` flow automatically triggers a background load if the data is missing or stale.

```kotlin
// In a ViewModel or UI Component scope:
val observer = userData.observe(
    scope = viewModelScope,
    staleTime = 5.minutes,
    loadOnInit = true
)

// Collect state reactively
val state by observer.current.collectAsState()

when (state) {
    is DataResult.Empty -> Text("No data loaded yet")
    is DataResult.Loading -> CircularProgressIndicator()
    is DataResult.Success -> Text("Hello, ${state.data.name}")
    is DataResult.Error -> Text("Error: ${state.error.message}")
}
```

#### Inspecting `DataResult<T>`

`DataResult` provides convenient helper methods to inspect the current state:

```kotlin
val data: User? = state.data()               // Current data, or stale data if reloading/error
val error: Throwable? = state.error()         // Error object if in Error state
val isLoading: Boolean = state.loading()      // true if currently fetching
val isInitializing: Boolean = state.initializing() // true if loading for the first time
val isReloading: Boolean = state.reloading()   // true if refreshing existing stale data
val lastLoaded: Instant? = state.lastLoaded() // Timestamp of last successful load
```

---

### 4. Triggering Loads & Cache Control

Any `RemoteDataHandle` (`RemoteData` or `RemoteDataObserver`) provides methods for controlling data execution and cache lifecycle:

```kotlin
// Background fire-and-forget fetch
userData.load()

// Suspending fetch
val result: DataResult<User> = userData.loadSuspending()

// Ensure data is present (returns immediately if cached/successful, fetches if missing, throws on error)
val user: User? = userData.ensure()

// Cancel active background fetch
userData.cancel()

// Invalidate cached timestamp (marks data as stale so next check re-fetches)
userData.invalidate()

// Reset state back to DataResult.Empty()
userData.clear()
```

---

### 5. Updating Cached Data (Optimistic Updates)

Use `setData` to manually update cached values — ideal for optimistic UI updates following mutations.

```kotlin
// Set a new value directly
userData.setData(updatedUser)

// Update using a transform function on the current data
userData.setData { currentUser -> currentUser?.copy(name = "New Name") }
```

---

### 6. Persistence & Serialization

`RemoteData` supports persistence across app restarts via `RemoteDataPersister` and `kotlinx.serialization`.

```kotlin
class KeyValuePersister(private val storage: KeyValueStorage) : RemoteDataPersister {
    override suspend fun <Key, Data, Params> write(
        key: RemoteDataKey<Key, Params>,
        data: PersistentData<Data>,
        serializer: RemoteDataSerializer<Key, Data, Params>
    ) {
        val keyJson = serializer.serializeKeyToJson(key)
        val dataJson = serializer.serializeDataToJson(data)
        storage.put(keyJson, dataJson)
    }

    override suspend fun <Key, Data, Params> read(
        key: RemoteDataKey<Key, Params>,
        serializer: RemoteDataSerializer<Key, Data, Params>
    ): PersistentData<Data>? {
        val keyJson = serializer.serializeKeyToJson(key)
        val dataJson = storage.get(keyJson) ?: return null
        return serializer.deserializeDataFromJson(dataJson)
    }
}
```

Pass your persister instance to `RemoteDataRegistry` or directly to individual `dataFactory`/`data` calls.

---

### 7. Actions & Mutations (`RemoteAction`)

Use `RemoteAction` for mutations and side-effects such as `POST`, `PUT`, or `DELETE` requests.

#### Registering an Action

```kotlin
val updateUserAction: RemoteAction<UserUpdateParams, User> = registry.action(
    key = "update_user",
    strategy = ConcurrentActionStrategy.JOIN,
    action = { params -> api.updateUser(params) }
)
```

#### Concurrent Action Strategies

- **`JOIN`** *(Default)*: Reuses and joins an ongoing execution if the action is already running for the same parameters.
- **`CANCEL`**: Cancels any active execution for the same parameters and starts a new one.
- **`QUEUE`**: Queues the new execution to run sequentially after the active one finishes.

#### Executing Actions via `RemoteActionRunner`

Create a scope-bound `RemoteActionRunner` to manage execution state and handle callbacks (`onSuccess`, `onError`, `onCancel`, `onSettled`).

```kotlin
val runner = updateUserAction.runner(viewModelScope)

// Run in background with callbacks
runner.run(
    params = UserUpdateParams(id = "123", name = "New Name"),
    onSuccess = { updatedUser ->
        userData.setData(updatedUser) // Update local cache optimistically
    },
    onError = { error ->
        showSnackbar("Update failed: ${error.message}")
    },
    onCancel = {
        println("Action was cancelled")
    },
    onSettled = {
        println("Action execution finished")
    }
)

// Or run as a suspending function
val updatedUser: User? = runner.runSuspending(params)

// Reactively observe the runner's last result
val actionResult by runner.currentResult.collectAsState(initial = null)
```

#### Accessing Action Results Map

`RemoteAction.results` exposes a `StateFlow<Map<Params, ActionResult<Params, Data>>>` containing status for all parameters:

```kotlin
val resultsMap by updateUserAction.results.collectAsState()
val statusForUser123 = resultsMap[UserUpdateParams(id = "123")]
```

---

### 8. Registry Management

```kotlin
// Remove specific key's cached data and actions
registry.remove("user_by_id")

// Clear all cached RemoteData and RemoteAction instances
registry.clear()
```

---

## API Reference Overview

### Core Types & Interfaces

| Type / Interface | Description |
|---|---|
| `DataResult<T>` | Sealed interface (`Empty`, `Loading`, `Success`, `Error`) representing data state. |
| `ActionResult<Params, Data>` | Sealed interface (`Loading`, `Success`, `Error`) representing parameter-scoped action state. |
| `RemoteDataHandle<Data>` | Common contract interface implemented by `RemoteData` and `RemoteDataObserver`. |
| `RemoteData<Key, Data, Params>` | Core reactive cached data container. |
| `RemoteDataObserver<Key, Data, Params>` | Lifecycle-aware observer wrapping `RemoteData` with stale-time revalidation on subscription. |
| `RemoteAction<Params, Data>` | Mutation handler with concurrent strategy support (`JOIN`, `CANCEL`, `QUEUE`). |
| `RemoteActionRunner<Params, Data>` | Scope-bound runner for `RemoteAction` supporting async callbacks (`onSuccess`, `onError`, `onCancel`, `onSettled`). |
| `RemoteDataPersister` | Interface for persistent storage using `RemoteDataSerializer`. |
| `RemoteDataRegistry` | Central cache registry and factory for all `RemoteData` and `RemoteAction` instances. |

---

## License

RemoteData is licensed under the [Apache License 2.0](LICENSE).
