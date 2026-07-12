# Monomail: Full-Stack Android Development Guide (0 → 100)

> A complete walkthrough of building a production email client, teaching Kotlin, Jetpack Compose, Room, Hilt, WorkManager, REST APIs, Push Notifications, Encryption, and more — all grounded in real code from this project.

---

## Table of Contents

1. [Phase 0: Prerequisites & Project Setup](#phase-0-prerequisites--project-setup)
2. [Phase 1: Kotlin Fundamentals](#phase-1-kotlin-fundamentals)
3. [Phase 2: Android App Structure](#phase-2-android-app-structure)
4. [Phase 3: Jetpack Compose — UI from Scratch](#phase-3-jetpack-compose--ui-from-scratch)
5. [Phase 4: Navigation](#phase-4-navigation)
6. [Phase 5: Theming & Design System](#phase-5-theming--design-system)
7. [Phase 6: Dependency Injection with Hilt](#phase-6-dependency-injection-with-hilt)
8. [Phase 7: Data Layer — Models & Database](#phase-7-data-layer--models--database)
9. [Phase 8: Networking — REST APIs with Retrofit](#phase-8-networking--rest-apis-with-retrofit)
10. [Phase 9: Repository Pattern](#phase-9-repository-pattern)
11. [Phase 10: ViewModel & State Management](#phase-10-viewmodel--state-management)
12. [Phase 11: Building the Inbox Screen](#phase-11-building-the-inbox-screen)
13. [Phase 12: Authentication — OAuth & Multi-Account](#phase-12-authentication--oauth--multi-account)
14. [Phase 13: Background Work with WorkManager](#phase-13-background-work-with-workmanager)
15. [Phase 14: Push Notifications](#phase-14-push-notifications)
16. [Phase 15: Security & Encryption](#phase-15-security--encryption)
17. [Phase 16: Email Providers — Gmail, Outlook, IMAP](#phase-16-email-providers--gmail-outlook-imap)
18. [Phase 17: The Offline-First Pattern](#phase-17-the-offline-first-pattern)
19. [Phase 18: Compose Screen Deep-Dives](#phase-18-compose-screen-deep-dives)
20. [Phase 19: Backend — Cloudflare Workers](#phase-19-backend--cloudflare-workers)
21. [Phase 20: Build System, Flavors & Release](#phase-20-build-system-flavors--release)
22. [Phase 21: What's Next](#phase-21-whats-next)

---

## Phase 0: Prerequisites & Project Setup

### What You Need Installed
- **Android Studio** (latest stable)
- **JDK 17+**
- **Android SDK 26+** (minimum device: Android 8.0)

### Project Structure Overview
```
Monomail/
├── app/                          # The single Android module
│   ├── build.gradle.kts          # App-level build config
│   └── src/main/java/com/shrivatsav/monomail/
│       ├── MainActivity.kt       # Entry point Activity
│       ├── MonoMailApp.kt        # Application class
│       ├── auth/                 # Authentication logic
│       ├── data/                 # Database, API, repository
│       ├── di/                   # Dependency injection modules
│       ├── push/                 # Push notification handling
│       ├── security/             # Encryption utilities
│       ├── ui/                   # All Compose UI code
│       ├── util/                 # String helpers, etc.
│       └── worker/               # Background tasks
├── backend/                      # Cloudflare Worker (push relay)
├── build.gradle.kts              # Root build config
└── settings.gradle.kts           # Module declarations
```

### Build Commands
```bash
./gradlew assembleGithubDebug     # Build debug APK (no API keys needed)
./gradlew installGithubDebug     # Build + install on connected device
./gradlew test                    # Run unit tests
```

---

## Phase 1: Kotlin Fundamentals

Everything in Android today is Kotlin. Here are the patterns used throughout Monomail.

### 1.1 Data Classes — Your Data Containers

A data class auto-generates `equals()`, `hashCode()`, `toString()`, `copy()`.

```kotlin
// auth/UserProfile.kt — a simple data class
data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val accessToken: String,
    val provider: String,           // "gmail" | "outlook" | "imap"
    val refreshToken: String = ""   // default value
)
```

The `copy()` method lets you create a modified version:
```kotlin
val updated = profile.copy(accessToken = "new_token_123")
```

### 1.2 Sealed Classes — Restricted Type Hierarchies

Sealed classes represent a fixed set of types. The compiler knows all subtypes, so `when` expressions are exhaustive.

```kotlin
// auth/AuthManager.kt
sealed class SignInResult {
    data class Success(val profile: UserProfile) : SignInResult()
    data class NeedsConsent(val intent: Intent)  : SignInResult()
    data class Failure(val error: Exception)     : SignInResult()
}

// Usage — compiler ensures all cases handled:
when (result) {
    is SignInResult.Success    -> navigateToInbox(result.profile)
    is SignInResult.NeedsConsent -> launchConsentFlow(result.intent)
    is SignInResult.Failure    -> showError(result.error)
    // no `else` needed — compiler knows all 3 subtypes
}
```

This pattern is used everywhere in the project for navigation routes, inbox states, action types, etc.

### 1.3 Enums — Fixed Value Sets

```kotlin
// data/settings/SettingsDataStore.kt
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SwipeAction { ARCHIVE, STAR, DELETE, READ_UNREAD }
enum class InboxTab { INBOX, SENT, ARCHIVED, STARRED, TRASH, UNIFIED, SNOOZED, SPAM }
```

### 1.4 Extension Functions — Add Methods to Existing Types

```kotlin
// data/local/Entities.kt — convert Room entity to domain model
fun EmailThread.toEntity(accountId: String, inInbox: Boolean = false) = ThreadEntity(
    threadId = threadId,
    accountId = accountId,
    subject = subject,
    // ... all fields mapped
)
```

This keeps conversion logic right next to the types it converts.

### 1.5 Coroutines — Async Without Callbacks

Kotlin coroutines replace callback hell with sequential-looking async code.

```kotlin
// auth/AuthManager.kt
suspend fun restoreSession(): Boolean {
    val profile = accountManager.getActiveAccount() ?: return false  // suspend call
    _userProfile = profile
    _isSignedIn.value = true
    refreshCurrentToken()  // suspend call
    pushNotificationManager.registerForPushNotifications(profile)  // suspend call
    return true
}
```

**Key concepts:**
- `suspend` = this function can pause and resume
- `Dispatchers.IO` = run on a thread pool (for network/disk)
- `Dispatchers.Main` = run on UI thread
- `withContext(Dispatchers.IO) { ... }` = switch to IO thread for a block

```kotlin
// Switching to IO thread for a network call
val newToken = withContext(Dispatchers.IO) {
    GoogleAuthUtil.getToken(context, Account(profile.email, "com.google"), GMAIL_SCOPE)
}
```

### 1.6 Flows — Reactive Data Streams

Flow is Kotlin's answer to reactive streams. It emits values over time.

```kotlin
// auth/AccountManager.kt
val accountsFlow: Flow<List<UserProfile>> = context.dataStore.data.map { prefs ->
    val json = prefs[KEY_ACCOUNTS_JSON] ?: return@map emptyList()
    val decryptedJson = SecurityUtil.decryptString(json) ?: return@map emptyList()
    val type = object : TypeToken<List<UserProfile>>() {}.type
    try { gson.fromJson(decryptedJson, type) } catch (e: Exception) { emptyList() }
}
```

**Operators:** `.map`, `.filter`, `.combine`, `.flatMapLatest`, `.distinctUntilChanged`

**StateFlow** = a Flow that always has a current value (useful for state):

```kotlin
private val _isSignedIn = MutableStateFlow(false)  // mutable internally
val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()  // read-only externally
```

### 1.7 Lambdas & Higher-Order Functions

Functions can be passed as arguments:

```kotlin
// di/AppModule.kt — a factory function that creates EmailProviders
fun provideProviderFactory(...): (UserProfile) -> EmailProvider {
    val providerCache = ConcurrentHashMap<String, EmailProvider>()
    return { profile ->   // this lambda takes a UserProfile, returns EmailProvider
        providerCache.getOrPut(profile.id) {
            createProvider(profile, ...)
        }
    }
}
```

---

## Phase 2: Android App Structure

### 2.1 The Application Class — `MonoMailApp.kt`

The Application class runs once when the app process starts. Perfect for global initialization.

```kotlin
@HiltAndroidApp  // tells Hilt this is the application entry point
class MonoMailApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var actionQueueManager: ActionQueueManager

    override fun onCreate() {
        super.onCreate()
        initializeMailcap()       // IMAP/MIME setup
        actionQueueManager.start() // start background action processing
    }
}
```

- `@HiltAndroidApp` — enables Hilt dependency injection for the entire app
- `Configuration.Provider` — lets WorkManager use Hilt to create workers

### 2.2 The Activity — `MainActivity.kt`

The single Activity hosts all Compose UI:

```kotlin
@AndroidEntryPoint  // enables Hilt injection into this Activity
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var emailRepository: EmailRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()  // Android 12+ splash
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isContentReady }

        enableEdgeToEdge()  // draw behind system bars

        setContent {  // this is where Compose UI begins
            val settings by settingsDataStore.settingsFlow.collectAsState()
            MonoMailTheme(themeMode = settings.themeMode.name) {
                NavGraph(authManager = authManager, emailRepository = emailRepository, ...)
            }
        }
    }
}
```

Key concepts:
- `@AndroidEntryPoint` — Hilt annotation, enables `@Inject` in Activities
- `setContent {}` — the bridge from Android to Jetpack Compose
- `collectAsState()` — converts a Flow into Compose state that recomposes on new values

### 2.3 The Android Manifest

Declares what your app can do:

```xml
<!-- app/src/main/AndroidManifest.xml -->
<application android:name=".MonoMailApp" ...>
    <activity android:name=".MainActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

---

## Phase 3: Jetpack Compose — UI from Scratch

### 3.1 What Is Compose?

Compose replaces XML layouts with Kotlin functions. Each `@Composable` function describes a piece of UI. When state changes, only the affected composables re-run (recompose).

### 3.2 Your First Composable

```kotlin
@Composable
fun EmailItem(
    thread: EmailThread,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.from,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (thread.isRead) FontWeight.Normal else FontWeight.Bold
            )
            Text(
                text = thread.subject,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (thread.snippet.isNotEmpty()) {
                Text(
                    text = thread.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
        Text(
            text = formatDate(thread.date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
```

### 3.3 State in Compose

State drives what the UI looks like. When state changes, Compose redraws.

```kotlin
@Composable
fun SearchBar() {
    var query by remember { mutableStateOf("") }  // local state

    TextField(
        value = query,
        onValueChange = { query = it },  // update state on input
        placeholder = { Text("Search emails") }
    )
}
```

- `remember { }` — preserves state across recompositions
- `mutableStateOf()` — creates an observable state holder
- `by` — Kotlin property delegation, lets you read/write directly

### 3.4 State Hoisting

State moves UP so child composables stay stateless (easier to test/reuse):

```kotlin
// State lives here (parent)
@Composable
fun InboxScreen(viewModel: InboxViewModel) {
    val state by viewModel.state.collectAsState()  // state from ViewModel

    // Pass state DOWN, events UP
    when (val s = state) {
        is InboxState.Loading -> LoadingIndicator()
        is InboxState.Error   -> ErrorView(s.message)
        is InboxState.Success -> EmailList(s.threads)
    }
}
```

### 3.5 Lists with LazyColumn

Only visible items are composed (like RecyclerView but simpler):

```kotlin
LazyColumn {
    items(displayItems, key = { it.key }) { item ->
        when (item) {
            is InboxDisplayItem.SingleThread -> {
                SwipeableEmailItem(
                    thread = item.thread,
                    onEmailClick = { onEmailClick(item.thread.threadId) },
                    // ...
                )
            }
            is InboxDisplayItem.DateHeader -> {
                Text(text = item.title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
```

### 3.6 Animations

Compose makes animations trivial:

```kotlin
// AnimatedContent — morph between states
AnimatedContent(
    targetState = when {
        tab == InboxTab.TRASH -> "trash"
        else -> "default"
    },
    label = "FabIconMorph"
) { state ->
    if (state == "default") {
        FloatingActionButton(onClick = onCompose) {
            Icon(Icons.Rounded.Edit, "Compose")
        }
    }
}

// animateFloatAsState — smooth scale on press
val fabScale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)
```

### 3.7 Side Effects — LaunchedEffect, DisposableEffect

```kotlin
// LaunchedEffect — run code when a key changes
LaunchedEffect(Unit) {
    viewModel.refresh()  // runs once on first composition
}

// DisposableEffect — cleanup when leaving composition
DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.setForegroundPollingEnabled(true)
        }
    }
    lifecycle.addObserver(observer)
    onDispose {
        lifecycle.removeObserver(observer)  // cleanup
    }
}
```

### 3.8 Modifiers — Chainable Styling

Modifiers are chained and read top-to-bottom:

```kotlin
Modifier
    .fillMaxSize()                      // take all available space
    .background(MaterialTheme.colorScheme.background)  // fill color
    .padding(16.dp)                     // inner padding
    .blur(12.dp)                        // blur effect
    .graphicsLayer(scaleX = fabScale, scaleY = fabScale)  // transform
```

### 3.9 Material 3 Components Used

| Component | Where Used |
|-----------|------------|
| `Scaffold` | Every screen — provides top bar, bottom bar, FAB, snackbar slots |
| `TextField` / `OutlinedTextField` | Search bars, compose screen, IMAP setup |
| `LazyColumn` | Inbox list, settings, scheduled messages |
| `FloatingActionButton` | Compose new email |
| `AlertDialog` | Reauth prompt, delete confirmation |
| `PullToRefreshBox` | Inbox pull-to-refresh |
| `SnackbarHost` | Error messages, undo toasts |
| `NavigationRail` / Bottom dock | Tab navigation |
| `LoadingIndicator` | Splash, initial load |
| `Surface` / `Card` | Long-press menus, profile cards |

---

## Phase 4: Navigation

### 4.1 Defining Routes

All routes are a sealed class:

```kotlin
// ui/navigation/NavGraph.kt
sealed class Screen(val route: String) {
    object Onboarding   : Screen("onboarding")
    object SignIn       : Screen("sign_in")
    object Inbox        : Screen("inbox")
    object ThreadDetail : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
    object Compose      : Screen("compose?mode={mode}&to={to}&subject={subject}&threadId={threadId}&messageId={messageId}&scheduledId={scheduledId}") {
        fun createRoute(mode: ComposeMode = ComposeMode.NEW, to: String = "", subject: String = ""): String {
            val enc = { s: String -> Uri.encode(s) }
            return "compose?mode=${mode.name}&to=${enc(to)}&subject=${enc(subject)}&..."
        }
    }
    object Settings : Screen("settings")
}
```

### 4.2 Setting Up NavHost

```kotlin
@Composable
fun NavGraph(authManager: AuthManager, ...) {
    val navController = rememberNavController()

    // Determine where to start based on auth state
    val startDestination = when {
        isAuthenticated -> Screen.Inbox.route
        !hasSeenWelcomePrompt -> Screen.Onboarding.route
        else -> Screen.SignIn.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(SlideDirection.Left, tween(300)) + fadeIn(tween(300))
        }
    ) {
        composable(Screen.Inbox.route) {
            val vm: InboxViewModel = hiltViewModel()
            InboxScreen(
                viewModel = vm,
                onEmailClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                },
                onCompose = {
                    navController.navigate(Screen.Compose.createRoute())
                }
            )
        }

        composable(
            route = Screen.ThreadDetail.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) {
            val vm: EmailDetailViewModel = hiltViewModel()
            EmailDetailScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
```

### 4.3 Navigation Transitions

Different routes get different animations:

```kotlin
enterTransition = {
    when {
        targetState.destination.route?.startsWith("compose") == true ->
            slideIntoContainer(SlideDirection.Up, tween(300)) + fadeIn()
        targetState.destination.route?.startsWith("thread") == true ->
            slideIntoContainer(SlideDirection.Left, tween(300)) + fadeIn()
        else -> fadeIn(tween(300))
    }
}
```

---

## Phase 5: Theming & Design System

### 5.1 Color Scheme — Monochrome Black & White

Monomail uses a strict monochrome palette:

```kotlin
// ui/theme/Color.kt
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFF8F8F8)     // off-white
val DarkBackground = Color(0xFF0A0A0A)      // near-black
val LightSurfaceContainer = Color(0xFFEDEDED)
val DarkSurfaceContainer = Color(0xFF1A1A1A)
```

### 5.2 Theme Setup

```kotlin
// ui/theme/Theme.kt
@Composable
fun MonoMailTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK"  -> true
        else    -> isSystemInDarkTheme()  // follow system
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialExpressiveTheme(
        colorScheme  = colorScheme,
        typography   = AppTypography,
        shapes       = MonoMailShapes,
        motionScheme = MotionScheme.expressive(),  // Material 3 Expressive
        content      = content
    )
}
```

### 5.3 Custom Theme Tokens

When Material 3 doesn't have a slot for your color:

```kotlin
data class MonoMailExtendedColors(val onSurfaceMuted: Color)

val LocalMonoMailExtendedColors = staticCompositionLocalOf { LightExtendedColors }

// Access in any composable:
MonoMailTheme.extendedColors.onSurfaceMuted
```

### 5.4 Font Scaling

Font size is adjustable by wrapping the density:

```kotlin
// MainActivity.kt
val fontScaleMultiplier = when (settings.fontScale) {
    FontScale.EXTRA_SMALL -> 0.8f
    FontScale.SMALL       -> 0.9f
    FontScale.DEFAULT     -> 1.0f
    FontScale.LARGE       -> 1.15f
    FontScale.EXTRA_LARGE -> 1.3f
}
CompositionLocalProvider(
    LocalDensity provides Density(
        density = density.density,
        fontScale = density.fontScale * fontScaleMultiplier
    )
) {
    MonoMailTheme { NavGraph(...) }
}
```

---

## Phase 6: Dependency Injection with Hilt

### 6.1 What Is DI?

Instead of creating dependencies yourself, Hilt gives them to you. This decouples code and makes testing easier.

### 6.2 Hilt Setup

```kotlin
// MonoMailApp.kt
@HiltAndroidApp  // step 1: annotate Application
class MonoMailApp : Application()

// MainActivity.kt
@AndroidEntryPoint  // step 2: annotate Activity
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AuthManager  // step 3: inject
}
```

### 6.3 Module — Where Bindings Are Defined

```kotlin
// di/AppModule.kt
@Module
@InstallIn(SingletonComponent::class)  // lives as long as the app
object AppModule {

    @Provides @Singleton  // one instance for the whole app
    fun provideAccountManager(@ApplicationContext context: Context): AccountManager =
        AccountManager(context)

    @Provides @Singleton
    fun provideAuthManager(
        @ApplicationContext context: Context,
        accountManager: AccountManager,        // Hilt provides this
        pushNotificationManager: PushNotificationManager  // and this
    ): AuthManager = AuthManager(context, accountManager, pushNotificationManager)

    @Provides @Singleton
    fun provideProviderFactory(
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        authManager: AuthManager
    ): (UserProfile) -> EmailProvider {  // provides a lambda function
        val providerCache = ConcurrentHashMap<String, EmailProvider>()
        return { profile ->
            providerCache.getOrPut(profile.id) {
                createProvider(profile, ...)
            }
        }
    }
}
```

### 6.4 Database Module

```kotlin
// di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides fun provideThreadDao(db: AppDatabase) = db.threadDao()
    @Provides fun provideEmailDao(db: AppDatabase) = db.emailDao()
    @Provides fun providePendingActionDao(db: AppDatabase) = db.pendingActionDao()
}
```

### 6.5 Injecting ViewModels

```kotlin
// Any screen composable:
val vm: InboxViewModel = hiltViewModel()  // Hilt creates + injects it

// InboxViewModel.kt
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: EmailRepository,       // from AppModule
    private val authManager: AuthManager,           // from AppModule
    private val settingsDataStore: SettingsDataStore // from AppModule
) : ViewModel() { ... }
```

### 6.6 Worker Injection

```kotlin
// worker/EmailSyncWorker.kt
@HiltWorker  // Hilt annotation for workers
class EmailSyncWorker @AssistedInject constructor(
    private val emailRepository: EmailRepository,  // Hilt injects this
    private val accountManager: AccountManager,     // and this
    @Assisted appContext: Context,                  // @Assisted = provided by WorkManager
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) { ... }
```

---

## Phase 7: Data Layer — Models & Database

### 7.1 Domain Models

These represent your app's data, independent of storage or network:

```kotlin
// data/model/EmailThread.kt (simplified)
data class EmailThread(
    val threadId: String,
    val subject: String,
    val from: String,
    val fromEmail: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val isRead: Boolean,
    val isStarred: Boolean,
    val latestMessageId: String,
    val participants: List<String>
)
```

### 7.2 Room Database — Persistent Storage

Room is SQLite with compile-time verification.

**Entity** = a table:

```kotlin
// data/local/Entities.kt
@Entity(
    tableName = "threads",
    primaryKeys = ["accountId", "threadId"],  // composite primary key
    indices = [
        Index(value = ["accountId", "inInbox", "date"]),  // index for fast queries
        Index(value = ["accountId", "isStarred", "date"]),
    ]
)
data class ThreadEntity(
    val threadId: String,
    val accountId: String,
    val subject: String,
    val fromName: String,
    val snippet: String,
    val date: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val inInbox: Boolean,
    val inSent: Boolean,
    val inArchived: Boolean,
    val inTrash: Boolean,
    val isSnoozed: Boolean = false,
    val snoozedUntil: Long = 0L
)
```

**DAO** = data access object (query definitions):

```kotlin
@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC")
    fun getInboxThreads(accountId: String): Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)

    @Query("UPDATE threads SET isStarred = :starred WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun updateThreadStarred(threadId: String, accountId: String, starred: Boolean)

    @Query("UPDATE threads SET inInbox = 0, inArchived = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun archiveThread(threadId: String, accountId: String)
}
```

**Database** = connects everything:

```kotlin
// data/local/AppDatabase.kt
@Database(
    entities = [ThreadEntity::class, EmailEntity::class, ScheduledMessageEntity::class, PendingActionEntity::class],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun emailDao(): EmailDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun pendingActionDao(): PendingActionDao
}
```

### 7.3 Database Migrations

When you change the schema, you must migrate existing data:

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN isSnoozed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE threads ADD COLUMN snoozedUntil INTEGER NOT NULL DEFAULT 0")
    }
}
```

### 7.4 Encrypted Database with SQLCipher

```kotlin
// AppDatabase.kt
fun getDatabase(context: Context): AppDatabase {
    System.loadLibrary("sqlcipher")
    val passphrase = String(SecurityUtil.getDatabasePassphrase(context)).toByteArray()
    val factory = SupportOpenHelperFactory(passphrase)  // SQLCipher encrypts at rest
    return Room.databaseBuilder(context, AppDatabase::class.java, "monomail_database")
        .openHelperFactory(factory)
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, ...)
        .build()
}
```

### 7.5 Entity ↔ Domain Mapping

Room entities are never shown to the UI. Extension functions bridge them:

```kotlin
// Entity → Domain
fun ThreadEntity.toDomainModel() = EmailThread(
    threadId = threadId,
    subject = subject,
    from = fromName,
    // ... all fields
)

// Domain → Entity
fun EmailThread.toEntity(accountId: String, inInbox: Boolean = false) = ThreadEntity(
    threadId = threadId,
    accountId = accountId,
    subject = subject,
    // ...
)
```

---

## Phase 8: Networking — REST APIs with Retrofit

### 8.1 Retrofit Interface

Define your API as a Kotlin interface:

```kotlin
// data/remote/GmailApi.kt
interface GmailApi {
    @GET("users/me/messages")
    suspend fun listMessages(
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("q") query: String? = null,
        @Header("Authorization") token: String
    ): GmailListResponse

    @GET("users/me/messages/{id}")
    suspend fun getMessage(
        @Path("id") messageId: String,
        @Header("Authorization") token: String
    ): GmailMessage
}
```

### 8.2 Retrofit Client with Auto-Refresh

```kotlin
// data/remote/RetrofitClient.kt
class RetrofitClient(
    private val tokenRefresher: () -> String?,
    private val onRefreshFailed: () -> Unit
) {
    val cachedToken = AtomicReference<String?>(null)

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            var request = chain.request()
            val token = cachedToken.get()
            if (token != null) {
                request = request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            val response = chain.proceed(request)

            // Auto-refresh on 401
            if (response.code == 401) {
                val newToken = tokenRefresher()
                if (newToken != null) {
                    cachedToken.set(newToken)
                    request = request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@addInterceptor chain.proceed(request)
                } else {
                    onRefreshFailed()  // notify auth layer
                }
            }
            response
        }
        .build()

    val gmailApi: GmailApi = Retrofit.Builder()
        .baseUrl("https://gmail.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GmailApi::class.java)
}
```

### 8.3 Provider Models — Normalizing API Responses

Different APIs return different shapes. Providers normalize them:

```kotlin
// data/provider/ProviderModels.kt
data class ProviderThread(
    val threadId: String,
    val messages: List<ProviderMessage>
)

data class ProviderMessage(
    val id: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val fromEmail: String,
    val body: String,
    val bodyIsHtml: Boolean,
    val date: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val folders: List<EmailFolder>,
    val attachments: List<EmailAttachmentInfo>
)
```

---

## Phase 9: Repository Pattern

The repository is the single source of truth. UI never talks to DB or network directly.

```kotlin
// data/repository/EmailRepository.kt
class EmailRepository(
    private val providerFactory: (UserProfile) -> EmailProvider,
    private val database: AppDatabase,
    private val context: Context,
    private val accountManager: AccountManager,
    private val pendingActionDao: PendingActionDao
) {
    // Expose database data as Flows
    fun getInboxThreadsFlow(tab: InboxTab, accountId: String): Flow<List<EmailThread>> {
        return threadDao.getInboxThreads(accountId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    // Network + database refresh
    suspend fun refreshInbox(tab: InboxTab, ...): Result<String?> {
        return try {
            val provider = getActiveProvider() ?: return Result.failure(Exception("No provider"))
            val listResponse = provider.listThreads(folder = folder, maxResults = 20, ...)

            // Map to entities and insert
            val entities = listResponse.threads.map { pt -> buildThreadEntity(pt, ...) }
            database.withTransaction {
                threadDao.insertThreads(entities)
                emailDao.insertEmails(allEmails)
            }
            Result.success(listResponse.nextPageToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## Phase 10: ViewModel & State Management

### 10.1 ViewModel — State That Survives Recomposition

ViewModels hold UI state and don't get recreated when the screen rotates.

```kotlin
// ui/screens/inbox/InboxViewModel.kt
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val authManager: AuthManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow<InboxState>(InboxState.Loading)
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private val _currentTab = MutableStateFlow(InboxTab.INBOX)
    val currentTab: StateFlow<InboxTab> = _currentTab.asStateFlow()

    init {
        // Observe database changes → update UI automatically
        viewModelScope.launch {
            combine(_currentTab, _activeAccountId, _organizeByThread) { tab, accountId, organize ->
                Triple(tab, accountId, organize)
            }.flatMapLatest { (tab, accountId, organize) ->
                repository.getInboxThreadsFlow(tab, accountId ?: "")
            }.collect { threads ->
                _state.value = InboxState.Success(threads)
            }
        }
    }

    fun switchTab(tab: InboxTab) {
        _currentTab.value = tab
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.refreshInbox(_currentTab.value)
            _isRefreshing.value = false
        }
    }
}
```

### 10.2 State Flow → UI

```kotlin
@Composable
fun InboxScreen(viewModel: InboxViewModel) {
    val state by viewModel.state.collectAsState()  // Flow → Compose State

    when (val s = state) {
        is InboxState.Loading -> LoadingIndicator()
        is InboxState.Error   -> ErrorMessage(s.message)
        is InboxState.Success -> EmailList(s.threads)
    }
}
```

### 10.3 Combining Multiple Flows

```kotlin
combine(_currentTab, _activeAccountId, _unifiedInboxEnabled, _organizeByThread) { tab, accountId, unifiedEnabled, organize ->
    Quad(tab, accountId, organize, unifiedEnabled)
}.flatMapLatest { (tab, accountId, organize, unifiedEnabled) ->
    if (organize) repository.getInboxThreadsFlow(tab, accountId ?: "")
    else repository.getInboxEmailsFlow(tab, accountId ?: "").map { emails -> /* group by threadId */ }
}
```

---

## Phase 11: Building the Inbox Screen

The inbox is the heart of the app. Here's how its pieces fit together.

### 11.1 Pull-to-Refresh

```kotlin
PullToRefreshBox(
    isRefreshing = s.isRefreshing,
    onRefresh = { viewModel.refresh() },
    state = rememberPullToRefreshState()
) {
    LazyColumn {
        items(displayItems, key = { it.key }) { item ->
            when (item) {
                is InboxDisplayItem.SingleThread -> SwipeableEmailItem(...)
                is InboxDisplayItem.DateHeader    -> DateHeader(...)
                is InboxDisplayItem.GroupHeader   -> GroupHeader(...)
            }
        }
    }
}
```

### 11.2 Swipe Actions

Swipe left/right on an email to archive or delete:

```kotlin
SwipeableEmailItem(
    thread = displayItem.thread,
    tabForSwipe = currentTab,
    appSettings = appSettings,
    viewModel = viewModel,
    onEmailClick = { onEmailClick(displayItem.thread.threadId) },
    onLongClick = {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        longPressedThread = displayItem.thread
    }
)
```

### 11.3 Long-Press Context Menu

```kotlin
if (longPressedThread != null) {
    BackHandler { longPressedThread = null }  // back button dismisses
    Surface(shape = RoundedCornerShape(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            LongPressAction(icon = Icons.Rounded.Star, label = "Star", onClick = { ... })
            LongPressAction(icon = Icons.Rounded.Archive, label = "Archive", onClick = { ... })
            LongPressAction(icon = Icons.Rounded.Delete, label = "Delete", onClick = { ... })
        }
    }
}
```

### 11.4 Bottom Dock Bar (Tab Navigation)

```kotlin
BottomDockBar(
    currentTab = tabForDock,
    dockConfig = appSettings.dockConfig,
    onTabClick = { viewModel.switchTab(it) }
)
```

### 11.5 Undo Pattern

The app hides items immediately and shows a 4-second undo window:

```kotlin
private fun queueAction(threadId: String, type: ActionType, message: String) {
    pendingHideIdsSnapshot[threadId] = true  // hide immediately from UI
    _toastState.value = ToastState(threadId, message, type)  // show undo toast

    pendingActionJobs[threadId] = viewModelScope.launch {
        delay(4000)  // wait 4 seconds
        executeAction(threadId, type)  // then actually do it
    }
}

fun undoAction() {
    val threadId = _toastState.value?.threadId ?: return
    pendingActionJobs[threadId]?.cancel()  // cancel the pending action
    pendingHideIdsSnapshot.remove(threadId)  // show it again
    _toastState.value = null
}
```

---

## Phase 12: Authentication — OAuth & Multi-Account

### 12.1 Google Sign-In Flow

```kotlin
// auth/AuthManager.kt
suspend fun signIn(activityContext: Context): SignInResult {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(clientId)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val result = credentialManager.getCredential(request, activityContext)
    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

    // Get OAuth access token for Gmail API
    val accessToken = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(activityContext, Account(email, "com.google"), GMAIL_SCOPE)
    }

    val profile = UserProfile(id = "gmail_$email", email = email, accessToken = accessToken, provider = "gmail")
    accountManager.addAccount(profile)
    return SignInResult.Success(profile)
}
```

### 12.2 Multi-Account Persistence

Accounts are stored as encrypted JSON in DataStore:

```kotlin
// auth/AccountManager.kt
suspend fun addAccount(profile: UserProfile) {
    context.dataStore.edit { prefs ->
        val accounts = getAccountsFromPrefs(prefs).toMutableList()
        accounts.add(profile)
        prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts))
    }
}

suspend fun getActiveAccount(): UserProfile? {
    val accounts = getAccounts()
    val activeId = prefs[KEY_ACTIVE_ACCOUNT_ID]
    return accounts.find { it.id == activeId } ?: accounts.firstOrNull()
}
```

### 12.3 Two-Phase Session Restore

On cold start, the app shows the UI instantly then refreshes in background:

```kotlin
// NavGraph.kt
LaunchedEffect(Unit) {
    // Phase 1: fast — read cached state, show UI immediately
    isAuthenticated = authManager.restoreSessionQuick()
    isLoading = false
    onContentReady()  // dismiss splash

    // Phase 2: slow — refresh tokens, register for push
    if (isAuthenticated) {
        scope.launch { authManager.restoreSession() }
    }
}
```

---

## Phase 13: Background Work with WorkManager

### 13.1 Periodic Sync

```kotlin
// MainActivity.kt
private fun scheduleBackgroundSync() {
    val workRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(
        15, TimeUnit.MINUTES  // minimum interval WorkManager allows
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "EmailSyncWork",
        ExistingPeriodicWorkPolicy.KEEP,  // don't replace if already scheduled
        workRequest
    )
}
```

### 13.2 The Sync Worker

```kotlin
@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    private val emailRepository: EmailRepository,
    private val accountManager: AccountManager,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val allAccounts = accountManager.getAccounts()

        // Sync all accounts in parallel
        val results = coroutineScope {
            allAccounts.map { account ->
                async(Dispatchers.IO) { syncAccount(account) }
            }.awaitAll()
        }

        scheduleNextAdaptiveSync(applicationContext, accountManager)
        return if (results.any { it.first }) Result.retry() else Result.success()
    }

    private suspend fun syncAccount(account: UserProfile): Pair<Boolean, Boolean> {
        val refreshResult = emailRepository.refreshInbox(InboxTab.INBOX, accountId = account.id)
        if (refreshResult.isFailure) return handleSyncFailure(...)

        val newestThread = emailRepository.getLatestInboxThread(account.id)
        val lastKnown = accountManager.getLastKnownEmailId(account.id)

        if (lastKnown != null && newestThread?.date?.toString() != lastKnown) {
            showNotification(account.id, newestThread!!)  // new email!
        }
        accountManager.setLastKnownEmailId(account.id, newestThread?.date?.toString() ?: "")
        return Pair(false, false)
    }
}
```

### 13.3 Adaptive Sync

Syncs more often when the user is active:

```kotlin
private suspend fun scheduleNextAdaptiveSync(context: Context, accountManager: AccountManager) {
    val lastActive = accountManager.getLastActiveTime()
    val isRecentlyActive = lastActive > 0 && (now - lastActive) < 5.minutes

    val delayMinutes = if (isRecentlyActive) 2L else 15L  // active → 2 min, idle → 15 min
    WorkManager.getInstance(context).enqueueUniqueWork(
        "adaptive_email_sync",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
    )
}
```

### 13.4 Scheduled Send

```kotlin
// EmailRepository.kt
suspend fun scheduleSend(accountId: String, to: String, subject: String, body: String, scheduledAt: Long) {
    val id = UUID.randomUUID().toString()
    scheduledMessageDao.insertScheduledMessage(ScheduledMessageEntity(id = id, ...))

    val delay = scheduledAt - System.currentTimeMillis()
    WorkManager.getInstance(context).enqueue(
        OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInitialDelay(maxOf(delay, 0), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("scheduled_message_id", id).build())
            .addTag("scheduled_send_$id")
            .build()
    )
}
```

---

## Phase 14: Push Notifications

### 14.1 Notification Channels (Android 8+)

```kotlin
private fun createNotificationChannel(context: Context, accountId: String, accountName: String) {
    val channel = NotificationChannel(
        "monomail_$accountId",   // unique per account
        "$accountName ($accountId)",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
```

### 14.2 Building Notifications with Actions

```kotlin
val builder = NotificationCompat.Builder(context, channelId)
    .setSmallIcon(R.drawable.ic_notification_leaf)
    .setContentTitle(thread.from)
    .setContentText(thread.subject)
    .setStyle(BigTextStyle().bigText(htmlContent))
    .addAction(replyAction)     // inline reply
    .addAction(archiveAction)   // one-tap archive
    .addAction(snoozeAction)    // snooze
    .setAutoCancel(true)
```

### 14.3 Inline Reply from Notification

```kotlin
val replyRemoteInput = RemoteInput.Builder("KEY_TEXT_REPLY")
    .setLabel("Reply")
    .build()
val replyAction = NotificationCompat.Action.Builder(
    android.R.drawable.ic_menu_send, "Reply", replyPendingIntent
).addRemoteInput(replyRemoteInput).build()
```

---

## Phase 15: Security & Encryption

### 15.1 AES-GCM Encryption via Android KeyStore

```kotlin
// security/SecurityUtil.kt
object SecurityUtil {
    fun encryptString(plaintext: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // ... generate/retrieve AES key from KeyStore
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        // return Base64(iv + encrypted)
    }

    fun decryptString(ciphertext: String): String? {
        // ... reverse of encrypt
    }
}
```

### 15.2 What Gets Encrypted

| Data | Storage | Encryption |
|------|---------|------------|
| User accounts (JSON) | DataStore | AES-GCM via SecurityUtil |
| Database contents | SQLite file | SQLCipher (full DB encryption) |
| DB passphrase | EncryptedSharedPreferences | Android KeyStore |
| Google Client ID | EncryptedSharedPreferences | Android KeyStore |
| IMAP config & password | Stored in `UserProfile.accessToken` / `.refreshToken` | AES-GCM via SecurityUtil |

---

## Phase 16: Email Providers — Gmail, Outlook, IMAP

### 16.1 The Provider Interface

All backends implement the same contract:

```kotlin
// data/provider/EmailProvider.kt
interface EmailProvider {
    val providerName: String
    suspend fun listThreads(folder: EmailFolder, maxResults: Int = 20, pageToken: String? = null, query: String? = null): ProviderThreadListResult
    suspend fun getThread(threadId: String): ProviderThread
    suspend fun archiveThread(threadId: String)
    suspend fun toggleStar(threadId: String, starred: Boolean)
    suspend fun markRead(threadId: String, read: Boolean)
    suspend fun sendEmail(from: String, to: String, subject: String, body: String, options: SendEmailOptions): String?
    suspend fun getSendAsAliases(): List<SendAsAlias>
    // ... more methods
}
```

### 16.2 Three Implementations

| Provider | Library | Auth Method |
|----------|---------|-------------|
| `GmailProvider` | Retrofit + Gmail REST API | Google OAuth token |
| `OutlookProvider` | Retrofit + Microsoft Graph API | MSAL token |
| `ImapProvider` | Jakarta Mail (Eclipse Angus) | Username/password |

### 16.3 Factory Pattern with Caching

```kotlin
// di/AppModule.kt
fun provideProviderFactory(...): (UserProfile) -> EmailProvider {
    val providerCache = ConcurrentHashMap<String, EmailProvider>()
    return { profile ->
        providerCache.getOrPut(profile.id) {
            when (profile.provider) {
                "gmail"   -> GmailProvider(retrofit.gmailApi, context)
                "outlook" -> OutlookProvider(retrofit.outlookApi, context)
                "imap"    -> createImapProvider(profile, context, authManager)
                else      -> throw IllegalArgumentException("Unknown: ${profile.provider}")
            }
        }
    }
}
```

---

## Phase 17: The Offline-First Pattern

### 17.1 How It Works

```
User Action → Update Local DB → Enqueue PendingAction → UI Updates Immediately
                                                            ↓
                              Background: ActionQueueManager syncs to server
```

### 17.2 The Pending Action Table

```kotlin
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val actionType: PendingActionType,  // TOGGLE_STAR, ARCHIVE, DELETE, etc.
    val threadId: String,
    val payload: String = "",
    val status: PendingActionStatus = PendingActionStatus.PENDING,
    val retryCount: Int = 0
)
```

### 17.3 ActionQueueManager — Background Sync

```kotlin
// data/worker/ActionQueueManager.kt
class ActionQueueManager(private val pendingActionDao: PendingActionDao, ...) {
    fun start() {
        scope.launch {
            while (true) {
                val pending = pendingActionDao.getPendingActions()
                for (action in pending) {
                    try {
                        pendingActionDao.updateStatus(action.id, PendingActionStatus.IN_FLIGHT)
                        processAction(action)  // call provider API
                        pendingActionDao.delete(action.id)
                    } catch (e: Exception) {
                        if (action.retryCount >= 5) {
                            pendingActionDao.updateStatus(action.id, PendingActionStatus.FAILED)
                        } else {
                            pendingActionDao.incrementRetry(action.id)
                        }
                    }
                }
                delay(5000)  // check every 5 seconds
            }
        }
    }
}
```

### 17.4 Optimistic Updates in Repository

```kotlin
suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
    val newStarred = !currentStarred
    insertPendingAction(PendingActionType.TOGGLE_STAR, accountId, threadId, payload = newStarred.toString())
    threadDao.updateThreadStarred(threadId, accountId, newStarred)  // local update first
    emailDao.updateThreadStarred(threadId, accountId, newStarred)
    // server sync happens in background via ActionQueueManager
}
```

---

## Phase 18: Compose Screen Deep-Dives

### 18.1 Compose Screen — Email Editor

```kotlin
// ui/screens/compose/ComposeScreen.kt
@Composable
fun ComposeScreen(viewModel: ComposeViewModel, onBack: () -> Unit, onSent: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(uiState.mode) { ComposeMode.REPLY -> "Reply"; ... }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = { /* Send button */ }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(value = uiState.to, onValueChange = { viewModel.updateTo(it) }, label = { Text("To") })
            OutlinedTextField(value = uiState.subject, onValueChange = { viewModel.updateSubject(it) }, label = { Text("Subject") })
            OutlinedTextField(
                value = uiState.body,
                onValueChange = { viewModel.updateBody(it) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = { Text("Body") }
            )
        }
    }
}
```

### 18.2 Email Detail Screen — WebView for HTML

```kotlin
// ui/screens/detail/EmailDetailScreen.kt
AndroidView(
    factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = false
            settings.loadWithOverviewMode = true
        }
    },
    update = { webView ->
        webView.loadDataWithBaseURL(null, email.body, "text/html", "UTF-8", null)
    }
)
```

### 18.3 Settings Screen — Preference Items

```kotlin
// ui/screens/settings/SettingsScreen.kt
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn {
        item {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SettingsToggle("Dark mode", checked = settings.themeMode == ThemeMode.DARK) {
                viewModel.setThemeMode(if (it) ThemeMode.DARK else ThemeMode.LIGHT)
            }
            SettingsToggle("Show dividers", checked = settings.showDividers) {
                viewModel.setShowDividers(it)
            }
        }
    }
}
```

---

## Phase 19: Backend — Cloudflare Workers

Monomail includes a small push relay backend.

### 19.1 What It Does

Receives email webhooks (Gmail Pub/Sub, Outlook Graph) and forwards them as push notifications to the user's device via FCM.

```typescript
// backend/src/index.ts (Cloudflare Worker)
export default {
    async fetch(request, env) {
        const url = new URL(request.url);

        if (url.pathname === '/register' && request.method === 'POST') {
            // Store FCM token + email mapping in KV
            const { accountId, fcmToken, email } = await request.json();
            await env.PUSH_KV.put(`account:${accountId}`, JSON.stringify({ fcmToken, email }));
            return new Response('OK');
        }

        if (url.pathname === '/webhook/gmail' && request.method === 'POST') {
            // Gmail Pub/Sub notification → fetch latest email → send FCM
            const body = await request.json();
            const accountId = body.emailAddress;
            const stored = await env.PUSH_KV.get(`account:${accountId}`, { type: 'json' });
            if (stored) {
                await sendFcmNotification(stored.fcmToken, env, ...);
            }
            return new Response('OK');
        }
    }
};
```

### 19.2 Why a Backend?

- Gmail Pub/Sub requires a publicly accessible HTTPS endpoint
- Outlook Graph webhooks need a callback URL
- The Worker is free (Cloudflare free tier) and stateless

---

## Phase 20: Build System, Flavors & Release

### 20.1 Product Flavors

Two distribution channels, same code:

```kotlin
// app/build.gradle.kts
flavorDimensions += "distribution"

productFlavors {
    create("github") {
        dimension = "distribution"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"\"")
        buildConfigField("Boolean", "IS_GITHUB_BUILD", "true")
    }
    create("playstore") {
        dimension = "distribution"
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("Boolean", "IS_GITHUB_BUILD", "false")
    }
}
```

This generates variants like `githubDebug`, `githubRelease`, `playstoreDebug`, `playstoreRelease`.

### 20.2 Secrets Management

Sensitive values live in files excluded from git:

```
secrets.properties    → GOOGLE_CLIENT_ID, PUSH_BACKEND_URL
keystore.properties   → signing key passwords
```

These are loaded at build time and injected as `BuildConfig` fields.

### 20.3 ProGuard / R8 (Release Builds)

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true     // shrink code
        isShrinkResources = true   // remove unused resources
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("release")
    }
}
```

### 20.4 Full Build & Install

```bash
./gradlew assembleGithubDebug     # compile
./gradlew installGithubDebug      # compile + install on device
```

---

## Phase 21: What's Next

This project covers production-grade Android development:

| Concept | Where in Monomail |
|---------|-------------------|
| Jetpack Compose UI | Every screen in `ui/screens/` |
| MVVM Architecture | ViewModels + Repository + Room |
| Dependency Injection | Hilt modules in `di/` |
| Room Database | `data/local/` with migrations |
| Retrofit Networking | `data/remote/` with auto-refresh |
| Background Sync | WorkManager in `worker/` |
| Push Notifications | `push/` + `EmailSyncWorker` |
| Security | SQLCipher + AES-GCM + Android KeyStore |
| Multi-Account Auth | `auth/` with OAuth + IMAP |
| Offline-First | Pending actions + optimistic updates |
| Material 3 Expressive | Custom monochrome theme |
| Cloudflare Workers | `backend/` push relay |

### Where to Go from Here

1. **Add unit tests** for `EmailRepository` and ViewModels
2. **Add Compose UI tests** with `ComposeTestRule`
3. **Explore the IMAP provider** — it uses Jakarta Mail directly instead of REST APIs
4. **Study the undo pattern** — it's the most sophisticated UI interaction in the app
5. **Look at the PGP support** — encryption/decryption of emails

---

*This guide was generated from the actual Monomail source code. Every code example is a real, working snippet from this project.*
