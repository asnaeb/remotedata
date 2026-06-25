# RemoteData

A data fetching and state management library for Kotlin Multiplatform, inspired by React Query. It simplifies fetching, caching, and synchronizing asynchronous data in your KMP applications.

## Features

- **Declarative Data Fetching**: Easily manage loading, success, and error states.
- **Caching & Stale Time**: Built-in support for caching and configurable stale-while-revalidate logic.
- **Automatic Reloading**: Periodically re-fetch data in the background with `reloadInterval`.
- **Kotlin Multiplatform**: Designed for KMP, supporting Android, iOS, JVM, JS, Wasm, and Linux.
- **Coroutine & Flow Powered**: Fully asynchronous and reactive using Kotlin Coroutines and StateFlow.
- **Action Support**: Dedicated `RemoteAction` for handling mutations and side effects.

## Installation

Add the following to your `commonMain` dependencies in your `build.gradle.kts`:

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation("io.github.asnaeb:remotedata:0.0.6")
    }
}
```

## Getting Started

### 1. Initialize the Registry

The `RemoteDataRegistry` manages the lifecycle and caching of your data.

```kotlin
val registry = RemoteDataRegistry(
    scope = CoroutineScope(Dispatchers.Default),
    defaultLoadOnInit = false,
    defaultStaleTime = 5.minutes,
    defaultReloadInterval = null
)
```

**Registry Parameters:**
- **`scope`**: The `CoroutineScope` where background fetches will be executed. Defaults to `CoroutineScope(Dispatchers.Default)`.
- **`defaultLoadOnInit`**: If `true`, `RemoteData` instances will automatically trigger a load when initialized. Defaults to `false`.
- **`defaultStaleTime`**: The duration after which data is considered stale. Defaults to `5.minutes`.
- **`defaultReloadInterval`**: If set, data will be automatically re-fetched on this interval. Defaults to `null` (disabled).

### 2. Fetching Data

> **Note on Keys**: `RemoteDataRegistry` caches instances based on the provided `id` (and parameters for `useRemoteData`). If you call these methods again with the same `id`, the existing instance is returned. Providing a different `loader` for the same `id` will result in the new loader being ignored.

#### Parameterized Data (factory form)

Use `useRemoteData` for data that depends on input parameters. It returns a factory function that creates or retrieves a cached `RemoteData` for each unique set of parameters.

```kotlin
val fetchUser = registry.useRemoteData<User, String>(
    id = "user_by_id",
    loader = { userId -> api.getUser(userId) }
)

// Access data for a specific user ID — returns a cached RemoteData<User>
val userData = fetchUser("123")

// In Compose or other reactive UI:
val data by userData.data.collectAsState()
val isLoading by userData.loading.collectAsState()
```

#### Parameterized Data (direct form)

Pass `params` directly to get a `RemoteData` instance immediately.

```kotlin
val userData: RemoteData<User> = registry.useRemoteData(
    id = "user_by_id",
    params = "123",
    loader = { userId -> api.getUser(userId) }
)
```

#### Static Data

Use `useStaticRemoteData` for data that doesn't require parameters.

```kotlin
val config: RemoteData<AppConfig> = registry.useStaticRemoteData(
    id = "app_config",
    loadOnInit = true,
    loader = { api.getConfig() }
)
```

#### Automatic Reloading

Pass a `reloadInterval` to keep data fresh without manual intervention.

```kotlin
val stockPrice: RemoteData<Price> = registry.useStaticRemoteData(
    id = "stock_price",
    loadOnInit = true,
    reloadInterval = 10.seconds,
    loader = { api.getLatestPrice() }
)
```

### 3. Loading Data

```kotlin
// Load in the background (fire-and-forget)
userData.load()

// Load and suspend until complete
userData.loadSuspend()

// Only load if data is stale
userData.loadIfStale()
userData.loadSuspendIfStale()

// Ensure data is present: loads if missing, returns immediately if already loaded
val user: User? = userData.ensure()
```

### 4. Updating Cached Data

Use `setData` to manually update the cache — useful for optimistic updates after a mutation.

```kotlin
// Set a value directly
userData.setData(updatedUser)

// Update using the current value
userData.setData { currentUser -> currentUser?.copy(name = "New Name") }
```

### 5. Mutations (RemoteAction)

Use `RemoteAction` for operations that change data (e.g., POST/PUT requests).

```kotlin
val updateUser = registry.useRemoteAction<User, UserUpdateParams>(
    id = "update_user",
    action = { params -> api.updateUser(params) }
)

// Fire-and-forget with callbacks
updateUser.run(
    params = UserUpdateParams(name = "New Name"),
    onSuccess = { updatedUser ->
        // Optimistically update the cached user
        userData.setData(updatedUser)
    },
    onError = { error ->
        println("Error: ${error.message}")
    },
    onCancel = { println("Cancelled") },
    onSettled = { println("Done") }
)

// Or suspend and get the result directly
val result: User? = updateUser.runAsync(UserUpdateParams(name = "New Name"))
```

#### Observing action state

```kotlin
val isLoading by updateUser.loading.collectAsState()
val lastResult by updateUser.data.collectAsState()
val lastError by updateUser.error.collectAsState()
val lastParams by updateUser.params.collectAsState()

// Check loading state for a specific set of params
val isUpdatingUser123 by updateUser.loading(UserUpdateParams(id = "123")).collectAsState(false)
```

### 6. Copying RemoteData with Different Options

Use `copy()` to get a new `RemoteData` instance that shares the same cache but uses different settings for the current scope.

```kotlin
val localUserData = userData.copy(
    scope = viewModelScope,
    loadOnInit = true,
    staleTime = 1.minutes,
    reloadInterval = 30.seconds
)
```

---

## API Reference

### `RemoteData<Data>`

A `RemoteData` instance represents a piece of asynchronous data that is cached and can be refreshed.

**Configuration Options (via `useRemoteData` / `useStaticRemoteData`):**
- **`scope`**: The `CoroutineScope` for background operations.
- **`loadOnInit`**: Whether to fetch data immediately upon creation if it's missing or stale.
- **`staleTime`**: The duration before the data is considered stale and needs re-fetching.
- **`reloadInterval`**: If set, data will be automatically re-fetched on this interval.

**Properties:**
| Property | Type | Description |
|---|---|---|
| `data` | `StateFlow<Data?>` | The current cached value. |
| `loading` | `StateFlow<Boolean>` | `true` while any fetch is in progress. |
| `error` | `StateFlow<Throwable?>` | The last error, or `null` if the last fetch succeeded. |
| `initializing` | `Flow<Boolean>` | `true` while fetching data for the first time. |
| `reloading` | `Flow<Boolean>` | `true` while refreshing data that is already present. |

**Methods:**
| Method | Description |
|---|---|
| `load()` | Triggers a fetch in the background. |
| `loadSuspend()` | Suspending version of `load()`. |
| `loadIfStale()` | Triggers a fetch only if data is stale. |
| `loadSuspendIfStale()` | Suspending version of `loadIfStale()`. |
| `ensure(): Data?` | Loads data if missing and returns it. Throws on error. |
| `setData(data: Data?)` | Manually updates the cached data. |
| `setData(function: (Data?) -> Data)` | Manually updates the cached data via a transform. |
| `copy(...)` | Returns a new `RemoteData` sharing the same cache with different options. |

---

### `RemoteAction<Data, Params>`

A `RemoteAction` handles mutations and side effects (e.g., POST/PUT/DELETE requests).

**Configuration Options (via `useRemoteAction`):**
- **`scope`**: The `CoroutineScope` where the action will be executed by default.

**Properties:**
| Property | Type | Description |
|---|---|---|
| `loading` | `StateFlow<Boolean>` | `true` while the action is executing. |
| `data` | `StateFlow<Data?>` | The result of the last successful execution. |
| `error` | `StateFlow<Throwable?>` | The last error encountered. |
| `params` | `StateFlow<Params?>` | The parameters used in the last execution. |

**Methods:**
| Method | Description |
|---|---|
| `run(params, onSuccess?, onError?, onCancel?, onSettled?, scope?)` | Executes the action in the background with optional callbacks. |
| `runAsync(params): Data?` | Suspending version of `run()`. Returns the result directly. |
| `loading(params): Flow<Boolean>` | A flow that is `true` only while the action is running with the given params. |

---

### `RemoteDataRegistry`

The central factory and cache for all `RemoteData` and `RemoteAction` instances.

**Constructor:**
```kotlin
RemoteDataRegistry(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    defaultLoadOnInit: Boolean = false,
    defaultStaleTime: Duration = 5.minutes,
    defaultReloadInterval: Duration? = null
)
```

**Methods:**
| Method | Description |
|---|---|
| `useRemoteData<Data, Params>(id, ...)` | Returns a `(Params) -> RemoteData<Data>` factory function. |
| `useRemoteData<Data, Params>(id, params, ...)` | Returns a `RemoteData<Data>` directly for the given params. |
| `useStaticRemoteData<Data>(id, ...)` | Returns a `RemoteData<Data>` for parameterless data. |
| `useRemoteAction<Data, Params>(id, ...)` | Returns a `RemoteAction<Data, Params>`. |

## License

RemoteData is licensed under the [Apache License 2.0](LICENSE).
