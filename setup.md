# Jar — Development Setup

Instructions for getting this project building and testing on a fresh machine.

## 1. Prerequisites

- **JDK 17** (Adoptium Temurin recommended)
- **Android SDK** — either the full Android Studio install, or just the `cmdline-tools` package if you only need to run unit tests
- **Git Bash** (Windows) or any POSIX shell (macOS/Linux)

### Required SDK packages

```
platforms;android-34
platform-tools
build-tools;34.0.0
```

Install with the SDK manager once `cmdline-tools` is on your `PATH`:

```
sdkmanager "platforms;android-34" "platform-tools" "build-tools;34.0.0"
sdkmanager --licenses   # accept all
```

## 2. Environment variables

Point to wherever your toolchain lives. Example (adjust the paths to match your install):

```bash
export JAVA_HOME="/path/to/jdk-17"
export ANDROID_HOME="/path/to/Android/Sdk"
export GRADLE_USER_HOME="$HOME/.gradle"        # or a custom cache location
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

**Convenience:** create a `.envrc` file in the project root with your paths and `source .envrc` at the start of each shell session. `.envrc` is gitignored because paths are machine-specific.

### Windows-specific notes

- Git Bash: use forward-slash paths with a drive prefix, e.g. `export JAVA_HOME="/d/Java/jdk-17"`.
- If you want the env vars to persist across new shells, set them in the User registry via PowerShell:
  ```powershell
  [Environment]::SetEnvironmentVariable("JAVA_HOME", "D:\Java\jdk-17", "User")
  ```
  Then open a **fresh** shell. Already-running processes (including the one you set the variable from) won't see the change.

## 3. Verify

From a fresh shell in the project root:

```bash
java -version                   # expect OpenJDK 17.x
echo "$ANDROID_HOME"            # expect the SDK path you set
ls "$ANDROID_HOME/platforms"    # expect at least android-34
./gradlew --version             # expect Gradle 8.5, JVM 17
```

## 4. Run the test suite

Phase 1 tests are pure JVM — no emulator needed:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 26 tests passing.

## 5. Adding HDFC SMS samples

The parser grows with real samples. Drop them into `app/src/test/resources/hdfc_samples/` as paired files:

- `<name>_<nnn>.txt` — the raw SMS text on a single line (or multi-line, the harness trims)
- `<name>_<nnn>.expected.json` — the expected parser output

Example (`zomato_delivery_007.txt` + `zomato_delivery_007.expected.json`):

```json
{
  "matchedPattern": "upi_sent",
  "amount": 42000,
  "merchant": "Zomato Limited",
  "balance": null,
  "accountLast4": "1234",
  "minConfidence": 0.7
}
```

Optional fields in `.expected.json`:
- `matchedPattern: null` → the harness expects `parser.parse(...)` to return `Failure` for this input.
- Omit any field to skip that assertion (useful when only certain fields matter for a given sample).
- `minConfidence` / `maxConfidence` — floor/ceiling, for exact confidence pinning use both set to the same value.

The parameterized suite (`HdfcFixtureSuiteTest`) discovers fixture pairs automatically — no code changes needed when adding samples. Run the suite with:

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcFixtureSuiteTest
```

When the corpus crosses 50 real samples, bump `ParseRateReportTest.minRate` from `0.0f` to `0.95f`. That enforces the spec's release gate (§11).

## 6. Gotchas

- **Kotlin compile daemons persist across shells.** If you change `JAVA_HOME` or `GRADLE_USER_HOME` mid-session, kill stale daemons before the next build:
  ```bash
  ./gradlew --stop
  powershell.exe -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force"
  ```
- **Gradle cache size.** `$GRADLE_USER_HOME` will grow to ~900 MB with AGP + Kotlin + test deps. Point it at a drive with headroom if your home drive is tight.

## 7. What's next

Phase 1 (parser foundation) is done — see tag `phase-1-parser-foundation`. Later phases build the Room data layer, NotificationListenerService, Compose UI, and release polish. Each phase has its own plan under `docs/superpowers/plans/`.
