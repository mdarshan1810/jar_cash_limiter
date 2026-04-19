# Jar — Mindful Spending App (v1 Design Spec)

Date: 2026-04-19
Platform: Android (minSdk 26, targetSdk latest stable)
Stack: Kotlin + Jetpack Compose + Room + NotificationListenerService

## 1. Product concept

Jar creates psychological friction around spending by visualizing a user's monthly
money as liquid in a glass jar that drains in real-time as bank transaction SMS
land. No manual logging. The thesis: physical cash makes us spend less because we
see it diminish; Jar recreates that feeling digitally.

## 2. Scope (v1, strictly enforced)

**In:** Android only; HDFC bank only (architected for easy expansion); single
tracked account; debits only (credits/refunds/transfers ignored); local storage
only; no categories/merchant enrichment/analytics.

**Out (explicit):** iOS, web, cloud sync, multi-bank, multi-account,
categories, merchant enrichment, push notifications from Jar, widgets, Wear OS,
multi-currency, CSV export UI (Phase 5 polish note only).

## 3. Key design decisions (resolved during brainstorming)

| # | Question | Decision | Reason |
|---|----------|----------|--------|
| 1 | SMS sample acquisition | Start with 5–10 seed samples; grow over time | User will supply real HDFC samples incrementally |
| 2 | starting_amount vs monthly_limit | Two separate fields | Jar = what's available, limit = stricter target |
| 3 | Jar overflow state (spent > starting) | Jar empty + red + negative balance "-₹X" | Preserves honest metaphor under overdraw |
| 4 | Account picker UX | NotificationListener-only + wait for first tx; optional last-4 escape hatch | No READ_SMS permission; Play Store-friendly |
| 5 | rollover_mode default | RESET | Matches physical-cash thesis; fresh jar each period |
| 6 | Build environment | Nothing installed — spec includes setup steps | User is on Windows, greenfield |
| 7 | Module structure | Single `:app` module, package-level separation | YAGNI; keeps parser Android-free for pure JVM tests |
| 8 | DI | Manual via `AppContainer` in `Application` | Hilt is overkill for v1's size |
| 9 | Period computation | Compute on-the-fly from today + settings | Zero background state; survives kills/reinstalls |
| 10 | 95% parse rule | Release gate, not Phase 4 gate | Seed won't satisfy it; unblock UI work in parallel |

## 4. Architecture

Single Gradle module `:app`, five packages with one responsibility each:

```
app/
├── parser/          Pure Kotlin, zero Android deps
│                    BankParser interface, HdfcParser, ParseResult, amount normalization
├── data/            Room DB, DAOs, entities, JarRepository, period math
├── notifications/   JarNotificationListener, NotificationPipeline, dedupe, sender whitelist
├── settings/        DataStore-backed Settings + SettingsStore, onboarding state
└── ui/              Compose: onboarding, main (jar + limit pages), settings, debug
    └── theme/       Colors, typography, state-driven accent
JarApp.kt            Application class — manual DI container
MainActivity.kt      Single activity, NavHost with 4 graphs
```

Data flow for one new transaction:

```
Bank app / SMS notification
  → NotificationListenerService.onNotificationPosted
  → NotificationPipeline: sender/package whitelist filter
  → BankParser.parse(text) → ParseResult
  → dedupe on (amount, timestamp/min, accountLast4) within ±2 min
  → Room insert (unique index on source_sms_hash as last-line defense)
  → Room Flow → JarRepository combines with settings → JarState
  → JarViewModel.StateFlow<JarState>
  → Compose re-renders jar level + emits ripple event
```

Period math is a pure function: `currentPeriod(now, settings, zone) → Period`.
No stored period row, no cron, no WorkManager reset job. The "Reset month"
button writes `settings.lastManualResetAt = now`, which period math honors as
`effectiveStart = max(computedStart, lastManualResetAt)`.

## 5. Parser (the heart of the product)

### 5.1 Interface

```kotlin
interface BankParser {
    val bankId: String
    fun parse(text: String): ParseResult
}

sealed class ParseResult {
    data class Success(
        val amount: Long,             // paise — never floats for money
        val merchant: String?,
        val balance: Long?,           // paise, nullable
        val accountLast4: String?,
        val confidence: Float,        // 0.0–1.0
        val matchedPattern: String
    ) : ParseResult()
    data class Failure(val reason: String) : ParseResult()
}
```

All monetary values are `Long` paise throughout the app. Floats forbidden.

### 5.2 HdfcParser patterns (seed set)

Pattern chain, tried in order, first match wins:

| Name | Shape | Confidence ceiling |
|------|-------|--------------------|
| `upi_sent` | "Sent Rs.X from HDFC Bank A/C XXXX to `<merchant>` on `<date>`" | 1.0 |
| `card_spent` | "Rs.X spent on HDFC Bank Card XXXX at `<merchant>` on `<date>`" | 1.0 |
| `debit_std` | "INR X.XX debited from A/c XXXXXX. Avl Bal: INR X,XXX.XX" | 1.0 |
| `upi_slash` | "UPI/XXXXXXXXX/`<merchant>`/`<upi-id>`" | 0.7 |
| `amount_only` | Last-resort: any "Rs./INR/₹ X" token | 0.4 |

### 5.3 Confidence scoring (from spec)

- 1.0 — amount + merchant + balance all present
- 0.7 — amount + merchant, no balance
- 0.4 — amount only
- < 0.4 — not auto-applied; routed to `unparsed_notifications`

### 5.4 Amount normalization

One function `parseAmountToPaise(raw: String): Long?` handles all formats:
`Rs.500`, `Rs 500`, `INR 500.00`, `₹500`, `500.00`, `1,234.56`, `1,23,456.78`
(Indian comma grouping). Strips symbols, validates, multiplies rupees × 100,
adds paise.

### 5.5 Test harness

`app/src/test/resources/hdfc_samples/<name>.txt` paired with
`<name>.expected.json`. A single parameterized JUnit4 test walks every pair.
Adding a sample = drop two files. Separate `ParseRateReport` test computes
the corpus parse rate and fails CI below threshold (0% seed, 95% release).

## 6. Data layer

### 6.1 Tables

```kotlin
@Entity(tableName = "transactions",
        indices = [Index(value = ["source_sms_hash"], unique = true)])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,                          // paise
    @ColumnInfo("merchant_raw")   val merchantRaw: String?,
    val timestamp: Long,                       // epoch millis
    @ColumnInfo("source_sms_hash") val sourceSmsHash: String,    // SHA-256
    @ColumnInfo("parse_confidence") val parseConfidence: Float,
    @ColumnInfo("account_last4")  val accountLast4: String?
)

@Entity(tableName = "unparsed_notifications")
data class UnparsedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo("raw_text")     val rawText: String,
    val sender: String?,
    val timestamp: Long,
    @ColumnInfo("package_name") val packageName: String
)
```

### 6.2 Settings — DataStore, not Room

Single-row config in Jetpack DataStore. Reasoning: single-row tables in Room
require an artificial ID and extra boilerplate; DataStore is built for this and
exposes a typed `Flow<Settings>`.

```kotlin
data class Settings(
    val startingAmount: Long,           // paise
    val periodStartDay: Int,            // 1..28 (avoid Feb edge case)
    val monthlyLimit: Long,             // paise
    val rolloverMode: RolloverMode,     // RESET (default) | ROLLOVER
    val trackedBank: String,            // "HDFC"
    val trackedAccountLast4: String?,   // null until confirmed
    val lastManualResetAt: Long?
)
```

### 6.3 Period math (pure function)

```kotlin
data class Period(val startMillis: Long, val endMillis: Long)

fun currentPeriod(now: LocalDateTime, settings: Settings, zone: ZoneId): Period {
    val dayOfMonth = min(settings.periodStartDay, now.lengthOfMonth())
    val candidate = now.withDayOfMonth(dayOfMonth).truncatedTo(ChronoUnit.DAYS)
    val start = if (candidate.isAfter(now)) candidate.minusMonths(1) else candidate
    val end = start.plusMonths(1)
    val startMs = start.atZone(zone).toInstant().toEpochMilli()
    val endMs   = end.atZone(zone).toInstant().toEpochMilli()
    val effective = maxOf(startMs, settings.lastManualResetAt ?: Long.MIN_VALUE)
    return Period(effective, endMs)
}
```

Pure function of `(now, settings, zone)` — testable with `Clock.fixed(...)`,
no mocks, no DB.

### 6.4 Repository

Thin wrapper. Key method:

```kotlin
fun observeJarState(): Flow<JarState>   // combines settings + spend-in-period
```

`JarState` carries `startingAmount`, `spent`, `monthlyLimit`,
`fractionRemaining`, `isOverdrawn`, `isOverLimit`.

### 6.5 Dedupe

Two-layer:
1. **In-code before insert:** `findDupeCandidates(amount, last4, now-2min..now+2min)`
   — if any row matches, skip. SQL nullability note: compare `last4` with
   `IS` (or `COALESCE(...,'∅') = COALESCE(...,'∅')`) so null-last4 candidates
   match one another; a raw `=` in SQLite will not match two NULLs.
2. **DB unique index on `source_sms_hash`:** last-line defense against replays
   (e.g., service restart replaying a notification).

### 6.6 Migrations

Room version 1 at launch, no migrations. When schema changes later, add a
proper `Migration` — do NOT use `fallbackToDestructiveMigration()`.

## 7. Notification capture

### 7.1 Service

```kotlin
class JarNotificationListener : NotificationListenerService() {
    @Inject lateinit var pipeline: NotificationPipeline

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val text = listOfNotNull(
            extras.getCharSequence("android.title")?.toString(),
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.bigText")?.toString()
        ).joinToString(" · ").ifBlank { return }
        pipeline.handle(RawNotification(text, sender, packageName, sbn.postTime))
    }
}
```

### 7.2 Pipeline composition

1. Look up active `BankParser` by `settings.trackedBank`.
2. Apply `BankWhitelist.matches(raw)` — accept only whitelisted senders or
   bank-app packages. Everything else silently dropped (never written to
   `unparsed_notifications` — no noise from WhatsApp etc.).
3. Hash raw text (SHA-256) for `source_sms_hash`.
4. Parser → `ParseResult`.
5. If `Success` and `confidence ≥ 0.4` and account matches (see below) and
   not a dupe → insert transaction.
6. If `Failure` or low confidence → insert into `unparsed_notifications`.

**Account-match rule** (resolves parse-result `accountLast4` nullability):
- `trackedAccountLast4 == null` (not yet set) → accept any. The first
  accepted transaction proposes itself as the tracked account; onboarding's
  "Waiting for first transaction" screen confirms and writes the setting.
- `trackedAccountLast4 != null`:
  - parse `accountLast4 == tracked` → accept
  - parse `accountLast4 == null` (parser couldn't extract it) → route to
    `unparsed_notifications`. Safer to lose a tx than to attribute one to
    the wrong account.
  - parse `accountLast4 != tracked` → drop silently (different account).

### 7.3 HDFC whitelist (v1)

Senders: `HDFCBK`, `HDFC`, `VM-HDFCBK`, `VK-HDFCBK`, `AD-HDFCBK`, `JD-HDFCBK`
(prefix variants common on Indian telecom routes).

Packages: HDFC Bank app (`com.snapwork.hdfc`), Messages by Google
(`com.google.android.apps.messaging`), Samsung Messages
(`com.samsung.android.messaging`).

### 7.4 Permission flow

NotificationListenerService is granted via system settings, not a runtime
dialog. Onboarding:

1. One-screen plain-language explainer.
2. CTA → `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)`.
3. On resume, verify with `NotificationManagerCompat.getEnabledListenerPackages()`.
4. If denied, non-accusatory retry screen. No fallback — the product cannot
   work without this.

### 7.5 Resilience

- `AndroidManifest` declares the service with `BIND_NOTIFICATION_LISTENER_SERVICE`
  permission and `exported="true"`.
- Android auto-rebinds after kills; OEM battery savers (Xiaomi, Oppo, etc.)
  sometimes don't. We surface "last notification received: X ago" in the
  diagnostic screen and a re-check button. We do not request battery
  whitelist — that's a hostile first-run experience.
- Reboot: listener rebinds on `BOOT_COMPLETED`. No action needed.
- Replays are idempotent thanks to unique `source_sms_hash` index.

### 7.6 Explicit non-goals in v1

- No `READ_SMS` permission.
- No foreground service (ugly persistent notification, not needed).
- No battery-optimization whitelist request.

## 8. UI

### 8.1 Navigation

Single `MainActivity` with one `NavHost`:

- `onboarding/` — 5 screens, linear; shown only while `trackedAccountLast4 == null`.
- `main/` — `VerticalPager` with `JarScreen` (page 0) and `LimitScreen` (page 1).
- `settings/` — reachable from a top-right icon on both main pages.
- `debug/` — hidden; reachable only via 7-tap on version label in settings.

No bottom navigation bar.

### 8.2 Page 1 — Jar (hero)

Composition (top to bottom inside a `Column`):

- ~10% top spacer
- `JarCanvas` at ~60% screen height — jar outline, liquid (sin-wave top edge
  clipped to jar interior), centered balance `₹X,XXX` (32sp bold) and
  `N% left` (14sp muted), ripple layer
- ~5% spacer
- `"spent this month: ₹X,XXX"` text
- Last transaction line: `"Zomato · ₹420 · 12 min ago"`
- 4dp `LinearProgressIndicator`, muted — deliberately not prominent
- ~10% bottom spacer

### 8.3 Jar rendering

Canvas (no SVG). Animation:

```kotlin
val level by animateFloatAsState(
    targetValue = state.fractionRemaining.coerceIn(0f, 1f),
    animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f)
)
val phase by rememberInfiniteTransition().animateFloat(
    initialValue = 0f, targetValue = 2 * PI.toFloat(),
    animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing))
)
```

Jar shape: mason-jar silhouette (rounded rect body, slight neck taper,
flat lid line) via `Path.arcTo` + `cubicTo`. Exact geometry iterated in
Phase 4 with the visual companion.

Liquid top edge: two stacked sin-waves at slightly different phases, amplitude
~3% of jar width, wavelength ~25%. Sampled every 2px.

Color mapping:
- Overdrawn (spent > starting) → Red
- < 20% remaining → WarningRed
- < 40% remaining → Amber
- Otherwise → CalmGreen

Ripple on new transaction: `JarViewModel.SharedFlow<RippleEvent>` emits per
insert; screen draws an expanding circle, radius 0 → jar width, alpha 0.6 → 0,
over 1000ms.

### 8.4 Page 2 — Limit + history

`Column(padding = 24.dp)`:

- "This month" small label
- `OutlinedTextField` for monthly limit (numeric keyboard, ₹ prefix)
- `LinearProgressIndicator(spent / limit)` colored by state
- Status line: "₹X remaining this month" or "₹X over" (red)
- "Recent" label
- `LazyColumn` of 10 transactions (merchant, time-ago, amount)
- `TextButton("Reset month")` → confirmation dialog → writes `lastManualResetAt`

### 8.5 Onboarding

Five linear screens:

1. **Welcome** — one-sentence pitch, "Get started".
2. **Permission** — why we need notification access, grant CTA, verify on resume.
3. **Starting amount + period day** — two inputs, one screen.
4. **Monthly limit** — one input, pre-filled at 80% of starting_amount.
5. **Waiting for first transaction** — friendly copy; subtle "I know my
   account number" link reveals a last-4 input (escape hatch). On first
   matching tx (or manual entry), replace-nav to `main/`.

### 8.6 Theme

- Light surface #FAFAFA / dark surface #111111; auto with system.
- onSurface #111 / #F5F5F5; muted = 60% alpha.
- Accent derived from `JarState` (green/amber/red). No gradients. Shadows ≤ 2dp.
- Typography: `displayLarge` 32sp bold (balance), `headlineSmall` 20sp semi,
  `bodyLarge` 16sp, `labelSmall` 12sp uppercase muted.
- 24dp default screen margin; generous whitespace.

### 8.7 ViewModels

- `JarViewModel` — `StateFlow<JarState>` + `SharedFlow<RippleEvent>`, both
  derived from `JarRepository`.
- `LimitViewModel` — `StateFlow<LimitScreenState>`; limit edit + reset action.
- `OnboardingViewModel` — step state, validation, writes settings, listens
  for first tracked transaction.
- `SettingsViewModel` — CRUD on settings, debug-screen access.

## 9. Testing

Pyramid:

- **Parser (pure JVM)** — parameterized sample fixtures, amount normalization,
  confidence scoring, `ParseRateReport` (fails build below threshold).
- **Data layer (pure JVM)** — `PeriodTest` (month boundaries, Feb with
  start_day = 31, DST transition, manual reset override), `JarRepositoryTest`
  with in-memory Room, `DedupeTest` (triple-fires, ±2min window).
- **ViewModel (pure JVM)** — fake repo + `TestDispatcher`, assert state and
  ripple emission.
- **Compose UI (instrumented)** — minimal: onboarding happy path, page swipe,
  reset-month dialog. No snapshot tests for the jar (shape iterates).
- **Manual smoke checklist in `docs/TESTING.md`** — real-device scenarios:
  grant flow, live HDFC notification, SMS+app double-fire, reboot, overdraw
  state, period crossover.

## 10. Privacy

- **No `INTERNET` permission** in the manifest. Transitive pulls removed with
  `tools:node="remove"`. App is physically incapable of network I/O.
- No Firebase, Crashlytics, or analytics of any kind.
- Raw SMS text stored only in `unparsed_notifications`. Debug screen has
  "clear all unparsed" button.
- `docs/PRIVACY.md` written from day one.
- Onboarding permission screen: *"Jar reads bank notifications on your
  device to track your spending. No data ever leaves your phone."*

## 11. Phase sequencing

### Phase 1 — Parser foundation
- Gradle project skeleton, single `:app` module, package layout.
- `BankParser` interface, `ParseResult`, `parseAmountToPaise`.
- `HdfcParser` with 5 patterns.
- Sample fixtures (`.txt` + `.expected.json`) with 5–10 seed samples.
- Parameterized test harness, `ParseRateReport`.
- **Exit criterion:** all seed-sample tests green. `ParseRateReport` reports
  whatever rate the seed produces (no gate yet).

### Phase 2 — Data layer (starts as soon as Phase 1 is green)
- Room DB v1 (`transactions`, `unparsed_notifications`).
- DataStore-backed `SettingsStore`.
- `JarRepository`, `currentPeriod` pure function.
- Unit tests: `PeriodTest`, `JarRepositoryTest`, `DedupeTest`.
- **Exit criterion:** all data-layer tests green.

### Phase 3 — Notification capture
- `JarNotificationListener` service + manifest declaration.
- `NotificationPipeline` composing whitelist → parser → dedupe → insert.
- `HdfcWhitelist`.
- Pipeline unit tests (fake parser, in-memory Room).
- **Exit criterion:** pipeline tests green; manual smoke test captures one
  real HDFC transaction end-to-end on a physical device.

### Phase 4 — UI (can start in parallel with Phase 2/3 once seed parser is green)
- Single `MainActivity`, NavHost.
- Theme + typography.
- `JarScreen` (Canvas jar, balance text, ripple).
- `LimitScreen` (limit, progress, history, reset).
- `SettingsScreen`, `OnboardingScreen` (5 steps).
- `JarViewModel`, `LimitViewModel`, `OnboardingViewModel`, `SettingsViewModel`.
- **Exit criterion:** onboarding happy path reachable end-to-end; jar renders
  and updates from a faked transaction stream; all pure-JVM VM tests green.

### Phase 5 — Polish & release prep
- Ripple tuning, liquid color transitions, empty/error states.
- CSV export.
- Debug screen (unparsed viewer + diagnostic strip).
- Manual-entry escape hatch on "waiting for first transaction" screen.
- Haptic feedback on new transaction (optional).
- `docs/PRIVACY.md`, `docs/TESTING.md`.

### Release gate (v1 done)
1. Parser corpus ≥ 50 real HDFC samples; `ParseRateReport` ≥ 95%.
2. All automated tests green.
3. Clean install on a real HDFC device: 10 consecutive transactions captured
   correctly through onboarding.
4. 48-hour soak: no crashes, listener still alive.
5. APK size under 10 MB.

## 12. Setup prerequisites (user is on a fresh Windows machine)

Before Phase 1 can run:

1. Install JDK 17 (`winget install EclipseAdoptium.Temurin.17.JDK`).
2. Install Android Studio (includes SDK + platform-tools). Accept all SDK
   licenses on first run.
3. Set `ANDROID_HOME` to the SDK path (default
   `%LOCALAPPDATA%\Android\Sdk`).
4. First `./gradlew testDebugUnitTest` run will download Gradle wrapper +
   dependencies (~300 MB). After that, Phase 1 parser tests run in seconds
   on plain JVM — no emulator needed until Phase 3 smoke test.

## 13. Open iterations (to revisit during implementation)

- **Jar silhouette geometry** — Canvas path tuned with the visual companion
  during Phase 4.
- **Parser corpus growth** — as real samples accumulate, review
  `unparsed_notifications` in the debug screen and add patterns.
- **HDFC sender variations** — real SMS routes vary by region. Whitelist may
  need additions when samples arrive.
