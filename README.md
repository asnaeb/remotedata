# RemoteData

A data fetching and state management library for Kotlin Multiplatform, inspired by React Query. It simplifies fetching, caching, and synchronizing asynchronous data in your KMP applications.

## Features

- **Declarative Data Fetching**: Easily manage loading, success, and error states.
- **Caching & Stale Time**: Built-in support for caching and configurable stale-while-revalidate logic.
- **Kotlin Multiplatform**: Designed for KMP, supporting Android, iOS, JVM, JS, Wasm, and Linux.
- **Coroutine & Flow Powered**: Fully asynchronous and reactive using Kotlin Coroutines and StateFlow.
- **Action Support**: Dedicated `RemoteAction` for handling mutations and side effects.

## Installation

Add the following to your `commonMain` dependencies in your `build.gradle.kts`:

```kotlin
sourceSets {
    commonMain.dependencies {
        implementation("io.github.asnaeb:remotedata:0.0.2")
    }
}
```

## Getting Started

### 1. Initialize the Registry

The `RemoteDataRegistry` manages the lifecycle and caching of your data.

```kotlin
val registry = RemoteDataRegistry(
    scope = CoroutineScope(Dispatchers.Default),
    defaultLoadOnInit = true,
    defaultStaleTime = 5.minutes
)
```

**Registry Parameters:**
- **`scope`**: The `CoroutineScope` where background fetches will be executed. Defaults to `CoroutineScope(Dispatchers.Default)`.
- **`defaultLoadOnInit`**: If `true`, `RemoteData` instances will automatically trigger a load when initialized. Defaults to `true`.
- **`defaultStaleTime`**: The duration after which data is considered stale. Defaults to `5.minutes`.

### 2. Fetching Data

> **Note on Keys**: `RemoteDataRegistry` caches instances based on the provided `id` (and parameters for `useRemoteData`). If you call these methods again with the same `id`, the existing instance is returned. Providing a different `loader` for the same `id` will result in the new loader being ignored.

#### Parameterized Data
Use `useRemoteData` for data that depends on input parameters.

```kotlin
val fetchUser = registry.useRemoteData<User, String>(
    id = "user_by_id",
    loader = { userId -> api.getUser(userId) }
)

// Access data for a specific user ID
val userData = fetchUser("123")

// In Compose or other reactive UI:
val data by userData.data.collectAsState()
val isLoading by userData.loading.collectAsState()
```

#### Static Data
Use `useStaticRemoteData` for data that doesn't require parameters.

```kotlin
val config = registry.useStaticRemoteData(
    id = "app_config",
    loader = { api.getConfig() }
)
```

### 3. Mutations (RemoteAction)

Use `RemoteAction` for operations that change data (e.g., POST/PUT requests).

```kotlin
val updateUser = registry.useRemoteAction<User, UserUpdateParams>(
    id = "update_user",
    action = { params -> api.updateUser(params) }
)

updateUser.run(
    params = UserUpdateParams(name = "New Name"),
    onSuccess = { updatedUser ->
        println("User updated successfully!")
    },
    onError = { error ->
        println("Error: ${error.message}")
    }
)
```

## API Reference

### `RemoteData<Data>`

A `RemoteData` instance represents a piece of asynchronous data that is cached and can be refreshed.

**Configuration Options:**
- **`scope`**: The `CoroutineScope` for background operations.
- **`loadOnInit`**: Whether to fetch data immediately upon creation if it's missing or stale.
- **`staleTime`**: The duration before the data is considered stale and needs re-fetching.

**Properties & Methods:**
- **`data: StateFlow<Data?>`**: The current value of the data. Accessing `value` or collecting the flow will automatically trigger `loadIfStale()`.
- **`loading: StateFlow<Boolean>`**: `true` if a fetch operation is currently in progress.
- **`error: StateFlow<Throwable?>`**: Contains the last error encountered, or `null` if the last fetch was successful.
- **`initializing: Flow<Boolean>`**: `true` if the data is being fetched for the first time.
- **`reloading: Flow<Boolean>`**: `true` if a refresh is in progress and data already exists.
- **`load()`**: Triggers a fetch operation in the background.
- **`loadSuspend()`**: Suspending version of `load()`.
- **`loadIfStale()`**: Triggers a fetch only if the data is stale (based on `staleTime`).
- **`loadSuspendIfStale()`**: Suspending version of `loadIfStale()`.
- **`ensure(): Data?`**: Ensures data is present. Loads it if missing, otherwise returns existing data immediately. Throws on error.
- **`setData(data: Data?)`**: Manually updates the cached data.
- **`setData(function: (Data?) -> Data)`**: Manually updates the cached data using a transformation.
- **`withOptions(...)`**: Returns a new `RemoteData` instance sharing the same cache but with different configurations (`scope`, `loadOnInit`, `staleTime`).

### `RemoteAction<Data, Params>`

A `RemoteAction` handles mutations and side effects (e.g., POST/PUT requests).

**Configuration Options:**
- **`scope`**: The `CoroutineScope` where the action and its callbacks will be executed.

**Properties & Methods:**
- **`run(params, onSuccess?, onError?, onCancel?, onSettled?)`**: Executes the action in the background.
  - `params`: The input parameters for the action.
  - `onSuccess`: Called when the action completes successfully.
  - `onError`: Called when the action fails.
  - `onCancel`: Called if the action is cancelled.
  - `onSettled`: Called when the action finishes, regardless of the outcome.
- **`loading: StateFlow<Boolean>`**: `true` if the action is currently executing.
- **`data: StateFlow<Data?>`**: The result of the last successful execution.
- **`error: StateFlow<Throwable?>`**: The last error encountered.
- **`params: StateFlow<Params?>`**: The parameters used in the last execution.

## License

RemoteData is licensed under the [Apache License 2.0](LICENSE).
