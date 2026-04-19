# Jar — Phase 1: Parser Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the HDFC SMS parser with a fixture-based test harness that can grow with real samples. At the end of Phase 1, `./gradlew testDebugUnitTest` runs a parameterized suite that parses every seed sample correctly and reports a parse-rate number.

**Architecture:** Single `:app` Gradle module; a pure-Kotlin `parser` package with zero Android dependencies (so its unit tests run on plain JVM); sealed `ParseResult` type; `HdfcParser` with a chain of named regex patterns tried in order; parameterized tests driven by `.txt` + `.expected.json` fixture pairs; a separate `ParseRateReport` test that computes corpus success rate.

**Tech Stack:** Kotlin 1.9+, Gradle 8.5+, Android Gradle Plugin 8.2+, JUnit 4 (Android default), kotlinx-serialization (for fixture JSON), minSdk 26 / targetSdk 34.

**Reference spec:** `docs/superpowers/specs/2026-04-19-jar-app-design.md` §5 (parser design).

---

## Prerequisites (one-time machine setup)

These run once before Task 0. They install the toolchain for compiling/testing Kotlin for Android. Parser unit tests run on plain JVM — no emulator needed until Phase 3.

**Storage constraint:** C drive is nearly full on this machine. **Every install below must go on the D drive.** Paths below use `D:\...` — if your D drive uses a different letter, substitute accordingly.

- [ ] **P1: Install JDK 17 on D drive** (Adoptium Temurin recommended)

Windows (PowerShell, run as admin):
```powershell
winget install EclipseAdoptium.Temurin.17.JDK `
  --location "D:\Java\jdk-17" `
  --accept-package-agreements --accept-source-agreements
```

If `winget` refuses the `--location` flag on your version, download the MSI installer from https://adoptium.net and pick `D:\Java\jdk-17` as the install directory during setup.

Verify in Git Bash:
```bash
java -version
# Expected: openjdk version "17.x.x"
which java
# Expected: /d/Java/jdk-17/bin/java (or similar — must be on D)
```

If `java` still resolves to a C-drive install, update PATH so D-drive JDK comes first. In `~/.bashrc`:
```bash
export JAVA_HOME="/d/Java/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"
```

- [ ] **P2: Install Android Studio on D drive**

Download: https://developer.android.com/studio

Run the installer. **When prompted for install location, choose `D:\Android\Android Studio`** (not the default `C:\Program Files\Android\...`).

Then launch Android Studio. On first run, the setup wizard asks where to install the Android SDK. **Choose `D:\Android\Sdk`** (not the default `%LOCALAPPDATA%\Android\Sdk` which lives on C). Let the wizard accept SDK licenses and download the baseline platform.

- [ ] **P3: Set ANDROID_HOME to the D-drive SDK**

In Git Bash `~/.bashrc`:
```bash
export ANDROID_HOME="/d/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin"
```

Reload:
```bash
source ~/.bashrc
```

Verify:
```bash
echo "$ANDROID_HOME"
# Expected: /d/Android/Sdk
ls "$ANDROID_HOME/platforms"
# Expected: at least one android-XX directory
```

- [ ] **P4: Redirect Gradle cache to D drive (avoid C-drive growth during builds)**

Gradle downloads dependencies and distributions into `GRADLE_USER_HOME` — by default `~/.gradle` which is on C. Redirect to D:

In `~/.bashrc`:
```bash
export GRADLE_USER_HOME="/d/.gradle"
```

Verify:
```bash
source ~/.bashrc
echo "$GRADLE_USER_HOME"
# Expected: /d/.gradle
mkdir -p "$GRADLE_USER_HOME"
```

---

## Task 0: Initialize git repo + .gitignore

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Init repo + set repo-local git identity**

Run:
```bash
cd "C:/Users/satya/OneDrive/Desktop/DARSHAN/projects_demo"
git init
git branch -M main
# Repo-local identity (does NOT touch global config)
git config user.name "mdarshan1810"
git config user.email "mdarshan179@gmail.com"
# Verify
git config --get user.name && git config --get user.email
# Expected: mdarshan1810 / mdarshan179@gmail.com
```

- [ ] **Step 2: Write `.gitignore`**

Create `.gitignore` with:
```gitignore
# Gradle
.gradle/
build/
!gradle-wrapper.jar

# IDE
.idea/
*.iml
*.iws
*.ipr

# Android
local.properties
captures/
.externalNativeBuild/
.cxx/

# OS
.DS_Store
Thumbs.db

# Superpowers brainstorm session data
.superpowers/
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore docs/
git commit -m "chore: init repo with gitignore and design spec"
```

---

## Task 1: Gradle wrapper + root build files

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`

We bootstrap the Gradle wrapper using a host Gradle install from Android Studio (it ships one at `<AS-install>/gradle/gradle-<ver>/bin/gradle`). If that's inconvenient, the wrapper can also be downloaded directly — step 2 shows a fallback.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jar"
include(":app")
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
junit = "4.13.2"
kotlinx-serialization = "1.6.2"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

- [ ] **Step 4: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 5: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 6: Install the wrapper binary + scripts**

Preferred (uses the Gradle bundled with Android Studio):
```bash
# Find the Android Studio Gradle — exact path depends on AS version
"/c/Program Files/Android/Android Studio/gradle/gradle-8.5/bin/gradle" wrapper --gradle-version 8.5
```

Fallback (download a one-off Gradle to generate wrapper):
```bash
curl -L -o /tmp/gradle-8.5-bin.zip https://services.gradle.org/distributions/gradle-8.5-bin.zip
unzip -q /tmp/gradle-8.5-bin.zip -d /tmp
/tmp/gradle-8.5/bin/gradle wrapper --gradle-version 8.5
```

Verify:
```bash
./gradlew --version
# Expected: Gradle 8.5, JVM 17, Kotlin 1.9.x
```

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat
git commit -m "chore: add gradle wrapper and root build config"
```

---

## Task 2: app module build config + manifest stub

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/proguard-rules.pro`

- [ ] **Step 1: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jar"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        debug { /* defaults */ }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
```

- [ ] **Step 2: Create `app/src/main/AndroidManifest.xml`**

Minimal manifest — no Application class yet (added in Phase 2), just a valid shell.
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:label="Jar" />

</manifest>
```

- [ ] **Step 3: Create `app/proguard-rules.pro`**

```proguard
# Project-specific ProGuard rules. Empty for Phase 1.
```

- [ ] **Step 4: Verify it configures**

```bash
./gradlew :app:tasks
# Expected: no errors; lists tasks like assembleDebug, testDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/proguard-rules.pro
git commit -m "chore: add app module build config and manifest shell"
```

---

## Task 3: Package scaffolding + smoke compile

**Files:**
- Create: `app/src/main/kotlin/com/jar/parser/.gitkeep`
- Create: `app/src/main/kotlin/com/jar/data/.gitkeep`
- Create: `app/src/main/kotlin/com/jar/notifications/.gitkeep`
- Create: `app/src/main/kotlin/com/jar/settings/.gitkeep`
- Create: `app/src/main/kotlin/com/jar/ui/.gitkeep`
- Create: `app/src/test/kotlin/com/jar/parser/SmokeTest.kt`

- [ ] **Step 1: Create empty package directories**

Each directory gets a `.gitkeep` so git tracks it.

```bash
mkdir -p app/src/main/kotlin/com/jar/{parser,data,notifications,settings,ui}
touch app/src/main/kotlin/com/jar/parser/.gitkeep
touch app/src/main/kotlin/com/jar/data/.gitkeep
touch app/src/main/kotlin/com/jar/notifications/.gitkeep
touch app/src/main/kotlin/com/jar/settings/.gitkeep
touch app/src/main/kotlin/com/jar/ui/.gitkeep
```

- [ ] **Step 2: Write one smoke test to prove the unit-test wiring works**

Create `app/src/test/kotlin/com/jar/parser/SmokeTest.kt`:
```kotlin
package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals

class SmokeTest {
    @Test fun onePlusOneIsTwo() {
        assertEquals(2, 1 + 1)
    }
}
```

- [ ] **Step 3: Run it**

```bash
./gradlew :app:testDebugUnitTest
# Expected: BUILD SUCCESSFUL, SmokeTest.onePlusOneIsTwo PASSED
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/jar app/src/test/kotlin/com/jar
git commit -m "chore: scaffold package layout and smoke-test unit test wiring"
```

---

## Task 4: `ParseResult` sealed class

**Files:**
- Create: `app/src/main/kotlin/com/jar/parser/ParseResult.kt`
- Test: `app/src/test/kotlin/com/jar/parser/ParseResultTest.kt`

- [ ] **Step 1: Write failing test**

`app/src/test/kotlin/com/jar/parser/ParseResultTest.kt`:
```kotlin
package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class ParseResultTest {

    @Test fun successHoldsAllFields() {
        val r = ParseResult.Success(
            amount = 50000L,
            merchant = "Zomato",
            balance = 1234500L,
            accountLast4 = "1234",
            confidence = 1.0f,
            matchedPattern = "upi_sent"
        )
        assertEquals(50000L, r.amount)
        assertEquals("Zomato", r.merchant)
        assertEquals(1234500L, r.balance)
        assertEquals("1234", r.accountLast4)
        assertEquals(1.0f, r.confidence, 0.0001f)
        assertEquals("upi_sent", r.matchedPattern)
    }

    @Test fun failureHoldsReason() {
        val r = ParseResult.Failure("no pattern matched")
        assertEquals("no pattern matched", r.reason)
    }

    @Test fun resultsAreSealedHierarchy() {
        val r: ParseResult = ParseResult.Failure("x")
        val matched = when (r) {
            is ParseResult.Success -> "success"
            is ParseResult.Failure -> "failure"
        }
        assertTrue(matched == "failure")
    }
}
```

- [ ] **Step 2: Run — should fail to compile**

```bash
./gradlew :app:testDebugUnitTest
# Expected: compilation error "Unresolved reference: ParseResult"
```

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/jar/parser/ParseResult.kt`:
```kotlin
package com.jar.parser

sealed class ParseResult {
    data class Success(
        val amount: Long,
        val merchant: String?,
        val balance: Long?,
        val accountLast4: String?,
        val confidence: Float,
        val matchedPattern: String
    ) : ParseResult()

    data class Failure(val reason: String) : ParseResult()
}
```

- [ ] **Step 4: Run — should pass**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASSED
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jar/parser/ParseResult.kt app/src/test/kotlin/com/jar/parser/ParseResultTest.kt
git commit -m "feat(parser): add ParseResult sealed class"
```

---

## Task 5: `BankParser` interface

**Files:**
- Create: `app/src/main/kotlin/com/jar/parser/BankParser.kt`
- Test: `app/src/test/kotlin/com/jar/parser/BankParserTest.kt`

- [ ] **Step 1: Write failing test**

`app/src/test/kotlin/com/jar/parser/BankParserTest.kt`:
```kotlin
package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals

class BankParserTest {

    @Test fun parserExposesBankIdAndParses() {
        val p: BankParser = object : BankParser {
            override val bankId = "TEST"
            override fun parse(text: String) =
                ParseResult.Success(
                    amount = 100L,
                    merchant = null,
                    balance = null,
                    accountLast4 = null,
                    confidence = 0.4f,
                    matchedPattern = "stub"
                )
        }
        assertEquals("TEST", p.bankId)
        val r = p.parse("anything")
        assertEquals(ParseResult.Success::class, r::class)
    }
}
```

- [ ] **Step 2: Run — fails to compile**

```bash
./gradlew :app:testDebugUnitTest
# Expected: "Unresolved reference: BankParser"
```

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/jar/parser/BankParser.kt`:
```kotlin
package com.jar.parser

interface BankParser {
    val bankId: String
    fun parse(text: String): ParseResult
}
```

- [ ] **Step 4: Run — should pass**

```bash
./gradlew :app:testDebugUnitTest
# Expected: PASS
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jar/parser/BankParser.kt app/src/test/kotlin/com/jar/parser/BankParserTest.kt
git commit -m "feat(parser): add BankParser interface"
```

---

## Task 6: `parseAmountToPaise` — amount normalization (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/jar/parser/Amount.kt`
- Test: `app/src/test/kotlin/com/jar/parser/AmountTest.kt`

- [ ] **Step 1: Write comprehensive failing tests**

`app/src/test/kotlin/com/jar/parser/AmountTest.kt`:
```kotlin
package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class AmountTest {

    @Test fun plainRupeesNoDecimals() {
        assertEquals(50000L, parseAmountToPaise("Rs.500"))
        assertEquals(50000L, parseAmountToPaise("Rs 500"))
        assertEquals(50000L, parseAmountToPaise("₹500"))
        assertEquals(50000L, parseAmountToPaise("INR 500"))
    }

    @Test fun rupeesWithDecimals() {
        assertEquals(50025L, parseAmountToPaise("Rs.500.25"))
        assertEquals(50000L, parseAmountToPaise("INR 500.00"))
        assertEquals(12345L, parseAmountToPaise("₹123.45"))
    }

    @Test fun westernCommaGrouping() {
        assertEquals(123456L, parseAmountToPaise("Rs.1,234.56"))
        assertEquals(100000000L, parseAmountToPaise("INR 1,000,000"))
    }

    @Test fun indianCommaGrouping() {
        assertEquals(12345678L, parseAmountToPaise("Rs.1,23,456.78"))
        assertEquals(1000000000L, parseAmountToPaise("₹1,00,00,000"))
    }

    @Test fun bareNumberNoSymbol() {
        assertEquals(50000L, parseAmountToPaise("500"))
        assertEquals(50025L, parseAmountToPaise("500.25"))
    }

    @Test fun returnsNullForNonsense() {
        assertNull(parseAmountToPaise(""))
        assertNull(parseAmountToPaise("abc"))
        assertNull(parseAmountToPaise("Rs."))
        assertNull(parseAmountToPaise("1.2.3"))
    }

    @Test fun handlesWhitespace() {
        assertEquals(50000L, parseAmountToPaise("  Rs. 500  "))
        assertEquals(50000L, parseAmountToPaise("INR\t500"))
    }

    @Test fun rejectsNegativeNumbers() {
        // We treat SMS amounts as magnitudes; sign is implied by the template ("debited", "sent").
        assertNull(parseAmountToPaise("-500"))
        assertNull(parseAmountToPaise("Rs.-500"))
    }

    @Test fun paiseSingleDigitPadding() {
        // "500.5" should be 500 rupees + 50 paise = 50050, not 50005.
        assertEquals(50050L, parseAmountToPaise("Rs.500.5"))
    }
}
```

- [ ] **Step 2: Run — fails to compile**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.AmountTest
# Expected: "Unresolved reference: parseAmountToPaise"
```

- [ ] **Step 3: Implement**

Create `app/src/main/kotlin/com/jar/parser/Amount.kt`:
```kotlin
package com.jar.parser

/**
 * Parses an amount string like "Rs.1,23,456.78" into paise (Long).
 * Returns null for malformed or negative inputs.
 * Supports: Rs., Rs , INR, ₹, bare numbers, western ("1,234,567") and Indian ("1,23,45,678") grouping.
 */
fun parseAmountToPaise(raw: String): Long? {
    if (raw.isBlank()) return null
    val stripped = raw.trim()
        .replace("Rs.", "", ignoreCase = true)
        .replace("Rs ", "", ignoreCase = true)
        .replace("INR", "", ignoreCase = true)
        .replace("₹", "")
        .replace(",", "")
        .trim()

    if (stripped.isEmpty() || stripped.startsWith("-")) return null

    val parts = stripped.split(".")
    return when (parts.size) {
        1 -> parts[0].toLongOrNull()?.let { it * 100 }
        2 -> {
            val rupees = parts[0].toLongOrNull() ?: return null
            val paiseStr = parts[1]
            if (paiseStr.isEmpty() || paiseStr.length > 2 || !paiseStr.all { it.isDigit() }) return null
            // pad "5" to "50" so .5 means 50 paise, not 5 paise
            val paise = paiseStr.padEnd(2, '0').toLongOrNull() ?: return null
            rupees * 100 + paise
        }
        else -> null
    }
}
```

- [ ] **Step 4: Run — should pass**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.AmountTest
# Expected: all 9 tests PASS
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/jar/parser/Amount.kt app/src/test/kotlin/com/jar/parser/AmountTest.kt
git commit -m "feat(parser): add parseAmountToPaise with Indian/western comma handling"
```

---

## Task 7: `HdfcParser` skeleton + confidence scoring helper

**Files:**
- Create: `app/src/main/kotlin/com/jar/parser/Confidence.kt`
- Create: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Test: `app/src/test/kotlin/com/jar/parser/ConfidenceTest.kt`

The skeleton has no patterns yet — always returns `Failure("no pattern matched")`. Tasks 8–12 add patterns one at a time.

- [ ] **Step 1: Write failing confidence test**

`app/src/test/kotlin/com/jar/parser/ConfidenceTest.kt`:
```kotlin
package com.jar.parser

import org.junit.Test
import org.junit.Assert.assertEquals

class ConfidenceTest {

    @Test fun fullFieldsScoresOne() {
        assertEquals(1.0f, scoreConfidence(amount = 100L, merchant = "X", balance = 200L), 0.0001f)
    }

    @Test fun amountAndMerchantScoresSevenTenths() {
        assertEquals(0.7f, scoreConfidence(amount = 100L, merchant = "X", balance = null), 0.0001f)
    }

    @Test fun amountOnlyScoresFourTenths() {
        assertEquals(0.4f, scoreConfidence(amount = 100L, merchant = null, balance = null), 0.0001f)
    }

    @Test fun amountAndBalanceButNoMerchantStillSevenTenths() {
        // Choice: presence of either merchant OR balance bumps from 0.4 to 0.7.
        assertEquals(0.7f, scoreConfidence(amount = 100L, merchant = null, balance = 200L), 0.0001f)
    }
}
```

- [ ] **Step 2: Run — fails to compile**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.ConfidenceTest
# Expected: "Unresolved reference: scoreConfidence"
```

- [ ] **Step 3: Implement confidence scoring**

Create `app/src/main/kotlin/com/jar/parser/Confidence.kt`:
```kotlin
package com.jar.parser

/**
 * Spec §5.3. Scoring:
 *  - 1.0 = amount + merchant + balance all present
 *  - 0.7 = amount + at least one of merchant/balance
 *  - 0.4 = amount only
 */
fun scoreConfidence(amount: Long, merchant: String?, balance: Long?): Float {
    val hasMerchant = !merchant.isNullOrBlank()
    val hasBalance = balance != null
    return when {
        hasMerchant && hasBalance -> 1.0f
        hasMerchant || hasBalance -> 0.7f
        else -> 0.4f
    }
}
```

- [ ] **Step 4: Create `HdfcParser` skeleton**

`app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`:
```kotlin
package com.jar.parser.hdfc

import com.jar.parser.BankParser
import com.jar.parser.ParseResult
import com.jar.parser.scoreConfidence

class HdfcParser : BankParser {
    override val bankId: String = "HDFC"

    /** A named regex pattern. Patterns are tried in the order they appear in `patterns`. */
    internal data class Pattern(val name: String, val regex: Regex)

    /** Extracted fields. Amount is required; everything else is optional. */
    internal data class Extracted(
        val amount: Long,
        val merchant: String?,
        val balance: Long?,
        val accountLast4: String?
    )

    /** Filled in by Tasks 8–12. */
    internal val patterns: List<Pattern> = emptyList()

    /** Filled in by Tasks 8–12 — one `extractFor...` per named pattern. */
    internal fun extract(pattern: Pattern, match: MatchResult): Extracted? = null

    override fun parse(text: String): ParseResult {
        for (p in patterns) {
            val m = p.regex.find(text) ?: continue
            val ex = extract(p, m) ?: continue
            return ParseResult.Success(
                amount = ex.amount,
                merchant = ex.merchant,
                balance = ex.balance,
                accountLast4 = ex.accountLast4,
                confidence = scoreConfidence(ex.amount, ex.merchant, ex.balance),
                matchedPattern = p.name
            )
        }
        return ParseResult.Failure("no pattern matched")
    }
}
```

- [ ] **Step 5: Run — should pass**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASS (parser returns Failure for everything for now, which is fine — no parser test asserts a match yet)
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/jar/parser/Confidence.kt app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt app/src/test/kotlin/com/jar/parser/ConfidenceTest.kt
git commit -m "feat(parser): add HdfcParser skeleton and confidence scoring"
```

---

## Task 8: HDFC pattern — `upi_sent` (TDD, first fixture)

Introduces the fixture-based sample layout. We'll write the fixture files directly and test against them. This pattern covers: *"Sent Rs.X from HDFC Bank A/C XXXX to `<merchant>` on `<date>`"*.

**Files:**
- Create: `app/src/test/resources/hdfc_samples/upi_sent_001.txt`
- Create: `app/src/test/resources/hdfc_samples/upi_sent_001.expected.json`
- Modify: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcUpiSentTest.kt`

> **NOTE ON REAL SAMPLES.** These fixture strings are synthetic but match the shape HDFC uses. When the user provides real SMS samples, drop them into `hdfc_samples/` as additional `.txt` + `.expected.json` pairs — no code changes needed. If real samples diverge from the shape here, update the regex to cover both and add both as fixtures.

- [ ] **Step 1: Create sample fixture**

`app/src/test/resources/hdfc_samples/upi_sent_001.txt`:
```
Sent Rs.420 from HDFC Bank A/C x1234 to Zomato Limited on 19-04-26. UPI Ref 123456789012. Not you? Call 18002586161
```

`app/src/test/resources/hdfc_samples/upi_sent_001.expected.json`:
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

- [ ] **Step 2: Write failing test**

`app/src/test/kotlin/com/jar/parser/HdfcUpiSentTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class HdfcUpiSentTest {

    private val parser = HdfcParser()

    @Test fun parsesSentRsToMerchantWithAccountLast4() {
        val text = "Sent Rs.420 from HDFC Bank A/C x1234 to Zomato Limited on 19-04-26. " +
            "UPI Ref 123456789012. Not you? Call 18002586161"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("upi_sent", r.matchedPattern)
        assertEquals(42000L, r.amount)
        assertEquals("Zomato Limited", r.merchant)
        assertEquals("1234", r.accountLast4)
        assertEquals(null, r.balance)
        assertTrue("confidence >= 0.7 expected, got ${r.confidence}", r.confidence >= 0.7f)
    }
}
```

- [ ] **Step 3: Run — fails because HdfcParser has no patterns yet**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcUpiSentTest
# Expected: ClassCastException (parser returned Failure, test cast to Success)
```

- [ ] **Step 4: Implement the pattern**

Edit `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`. Replace the `patterns` and `extract` stubs:
```kotlin
internal val patterns: List<Pattern> = listOf(
    Pattern(
        name = "upi_sent",
        regex = Regex(
            """Sent\s+Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+from\s+HDFC\s+Bank\s+A/?C\s+x?(\d{4,6})\s+to\s+(.+?)(?:\s+on\s+|\s*\.)""",
            RegexOption.IGNORE_CASE
        )
    )
)

internal fun extract(pattern: Pattern, match: MatchResult): Extracted? {
    return when (pattern.name) {
        "upi_sent" -> {
            val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
            val last4 = match.groupValues[2].takeLast(4)
            val merchant = match.groupValues[3].trim().trimEnd('.')
            Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = last4)
        }
        else -> null
    }
}
```

- [ ] **Step 5: Run — should pass**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcUpiSentTest
# Expected: PASS
```

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/hdfc_samples/upi_sent_001.* \
        app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt \
        app/src/test/kotlin/com/jar/parser/HdfcUpiSentTest.kt
git commit -m "feat(parser): add HDFC upi_sent pattern with first fixture"
```

---

## Task 9: HDFC pattern — `card_spent`

Covers *"Rs.X spent on HDFC Bank Card XXXX at `<merchant>` on `<date>`"*.

**Files:**
- Create: `app/src/test/resources/hdfc_samples/card_spent_001.txt`
- Create: `app/src/test/resources/hdfc_samples/card_spent_001.expected.json`
- Modify: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcCardSpentTest.kt`

- [ ] **Step 1: Fixture**

`app/src/test/resources/hdfc_samples/card_spent_001.txt`:
```
Rs.1,250.00 spent on HDFC Bank Card x9876 at Amazon Pay on 18-04-26. Avl Lmt Rs.87,500.00. Not you? SMS BLOCK to 567676
```

`app/src/test/resources/hdfc_samples/card_spent_001.expected.json`:
```json
{
  "matchedPattern": "card_spent",
  "amount": 125000,
  "merchant": "Amazon Pay",
  "balance": null,
  "accountLast4": "9876",
  "minConfidence": 0.7
}
```

- [ ] **Step 2: Failing test**

`app/src/test/kotlin/com/jar/parser/HdfcCardSpentTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcCardSpentTest {
    private val parser = HdfcParser()

    @Test fun parsesCardSpentAtMerchant() {
        val text = "Rs.1,250.00 spent on HDFC Bank Card x9876 at Amazon Pay on 18-04-26. " +
            "Avl Lmt Rs.87,500.00. Not you? SMS BLOCK to 567676"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("card_spent", r.matchedPattern)
        assertEquals(125000L, r.amount)
        assertEquals("Amazon Pay", r.merchant)
        assertEquals("9876", r.accountLast4)
    }
}
```

- [ ] **Step 3: Run — fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcCardSpentTest
# Expected: ClassCastException (no matching pattern yet)
```

- [ ] **Step 4: Add pattern**

Edit `HdfcParser.kt`. Append to `patterns`:
```kotlin
Pattern(
    name = "card_spent",
    regex = Regex(
        """Rs\.?\s*([\d,]+(?:\.\d{1,2})?)\s+spent\s+on\s+HDFC\s+Bank\s+Card\s+x?(\d{4,6})\s+at\s+(.+?)(?:\s+on\s+|\s*\.)""",
        RegexOption.IGNORE_CASE
    )
)
```

Append to `extract` `when`:
```kotlin
"card_spent" -> {
    val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
    val last4 = match.groupValues[2].takeLast(4)
    val merchant = match.groupValues[3].trim().trimEnd('.')
    Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = last4)
}
```

- [ ] **Step 5: Run — passes**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASS
```

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/hdfc_samples/card_spent_001.* \
        app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt \
        app/src/test/kotlin/com/jar/parser/HdfcCardSpentTest.kt
git commit -m "feat(parser): add HDFC card_spent pattern"
```

---

## Task 10: HDFC pattern — `debit_std` (carries balance → confidence 1.0)

Covers *"INR X.XX debited from A/c XXXXXX. Avl Bal: INR X,XXX.XX"*. This pattern extracts balance, so confidence should score 1.0 when merchant is also present, or 0.7 without merchant. This fixture has no merchant — we'll expect 0.7.

**Files:**
- Create: `app/src/test/resources/hdfc_samples/debit_std_001.txt`
- Create: `app/src/test/resources/hdfc_samples/debit_std_001.expected.json`
- Modify: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcDebitStdTest.kt`

- [ ] **Step 1: Fixture**

`app/src/test/resources/hdfc_samples/debit_std_001.txt`:
```
INR 750.00 debited from A/c XX5678 on 17-04-26. Avl Bal: INR 12,345.67. UPI Ref 987654321012.
```

`app/src/test/resources/hdfc_samples/debit_std_001.expected.json`:
```json
{
  "matchedPattern": "debit_std",
  "amount": 75000,
  "merchant": null,
  "balance": 1234567,
  "accountLast4": "5678",
  "minConfidence": 0.7
}
```

- [ ] **Step 2: Failing test**

`app/src/test/kotlin/com/jar/parser/HdfcDebitStdTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcDebitStdTest {
    private val parser = HdfcParser()

    @Test fun parsesDebitWithAvailableBalance() {
        val text = "INR 750.00 debited from A/c XX5678 on 17-04-26. " +
            "Avl Bal: INR 12,345.67. UPI Ref 987654321012."
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("debit_std", r.matchedPattern)
        assertEquals(75000L, r.amount)
        assertEquals(1234567L, r.balance)
        assertEquals("5678", r.accountLast4)
        assertEquals(null, r.merchant)
        assertEquals(0.7f, r.confidence, 0.0001f)  // balance but no merchant
    }
}
```

- [ ] **Step 3: Run — fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcDebitStdTest
# Expected: ClassCastException or no matching pattern
```

- [ ] **Step 4: Add pattern + extractor**

Append to `patterns` in `HdfcParser.kt`:
```kotlin
Pattern(
    name = "debit_std",
    regex = Regex(
        """INR\s+([\d,]+(?:\.\d{1,2})?)\s+debited\s+from\s+A/c\s+X*(\d{4,6}).*?Avl\s+Bal:\s+INR\s+([\d,]+(?:\.\d{1,2})?)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
)
```

Append to `extract` `when`:
```kotlin
"debit_std" -> {
    val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
    val last4 = match.groupValues[2].takeLast(4)
    val balance = com.jar.parser.parseAmountToPaise(match.groupValues[3])
    Extracted(amount = amount, merchant = null, balance = balance, accountLast4 = last4)
}
```

- [ ] **Step 5: Run — passes**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASS
```

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/hdfc_samples/debit_std_001.* \
        app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt \
        app/src/test/kotlin/com/jar/parser/HdfcDebitStdTest.kt
git commit -m "feat(parser): add HDFC debit_std pattern with balance extraction"
```

---

## Task 11: HDFC pattern — `upi_slash`

Covers *"UPI/XXXXXXXXX/`<merchant>`/`<upi-id>`"*-style SMS bodies that embed UPI-native formatting, often within a longer message.

**Files:**
- Create: `app/src/test/resources/hdfc_samples/upi_slash_001.txt`
- Create: `app/src/test/resources/hdfc_samples/upi_slash_001.expected.json`
- Modify: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcUpiSlashTest.kt`

- [ ] **Step 1: Fixture**

`app/src/test/resources/hdfc_samples/upi_slash_001.txt`:
```
UPI txn of Rs.299.00 debited. UPI/427054887123/SWIGGY/swiggy@ybl on 16-04-26. -HDFC Bank
```

`app/src/test/resources/hdfc_samples/upi_slash_001.expected.json`:
```json
{
  "matchedPattern": "upi_slash",
  "amount": 29900,
  "merchant": "SWIGGY",
  "balance": null,
  "accountLast4": null,
  "minConfidence": 0.7
}
```

- [ ] **Step 2: Failing test**

`app/src/test/kotlin/com/jar/parser/HdfcUpiSlashTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcUpiSlashTest {
    private val parser = HdfcParser()

    @Test fun parsesUpiSlashFormat() {
        val text = "UPI txn of Rs.299.00 debited. UPI/427054887123/SWIGGY/swiggy@ybl on 16-04-26. -HDFC Bank"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("upi_slash", r.matchedPattern)
        assertEquals(29900L, r.amount)
        assertEquals("SWIGGY", r.merchant)
        assertEquals(null, r.accountLast4)
        assertEquals(0.7f, r.confidence, 0.0001f)
    }
}
```

- [ ] **Step 3: Run — fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcUpiSlashTest
# Expected: ClassCastException (pattern missing)
```

- [ ] **Step 4: Add pattern + extractor**

Append to `patterns`:
```kotlin
Pattern(
    name = "upi_slash",
    regex = Regex(
        """Rs\.?\s*([\d,]+(?:\.\d{1,2})?).*?UPI/\d+/([^/]+)/""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
)
```

Append to `extract` `when`:
```kotlin
"upi_slash" -> {
    val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
    val merchant = match.groupValues[2].trim().ifBlank { null }
    Extracted(amount = amount, merchant = merchant, balance = null, accountLast4 = null)
}
```

- [ ] **Step 5: Run — passes**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASS
```

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/hdfc_samples/upi_slash_001.* \
        app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt \
        app/src/test/kotlin/com/jar/parser/HdfcUpiSlashTest.kt
git commit -m "feat(parser): add HDFC upi_slash pattern"
```

---

## Task 12: HDFC fallback pattern — `amount_only`

Last-resort pattern. Catches any "Rs./INR/₹" amount in a message that didn't match any earlier pattern. Confidence caps at 0.4 (amount only). Pipeline in Phase 3 will route these to `unparsed_notifications`.

**Files:**
- Create: `app/src/test/resources/hdfc_samples/amount_only_001.txt`
- Create: `app/src/test/resources/hdfc_samples/amount_only_001.expected.json`
- Modify: `app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcAmountOnlyTest.kt`

- [ ] **Step 1: Fixture**

`app/src/test/resources/hdfc_samples/amount_only_001.txt`:
```
Dear Customer, your mandate of Rs.5000 for insurance premium is scheduled for 20-04-26. -HDFC Bank
```

`app/src/test/resources/hdfc_samples/amount_only_001.expected.json`:
```json
{
  "matchedPattern": "amount_only",
  "amount": 500000,
  "merchant": null,
  "balance": null,
  "accountLast4": null,
  "minConfidence": 0.4,
  "maxConfidence": 0.4
}
```

- [ ] **Step 2: Failing test**

`app/src/test/kotlin/com/jar/parser/HdfcAmountOnlyTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertEquals

class HdfcAmountOnlyTest {
    private val parser = HdfcParser()

    @Test fun fallbackExtractsAmountOnlyWithLowConfidence() {
        val text = "Dear Customer, your mandate of Rs.5000 for insurance premium is scheduled for 20-04-26. -HDFC Bank"
        val r = parser.parse(text) as ParseResult.Success
        assertEquals("amount_only", r.matchedPattern)
        assertEquals(500000L, r.amount)
        assertEquals(null, r.merchant)
        assertEquals(null, r.balance)
        assertEquals(0.4f, r.confidence, 0.0001f)
    }

    @Test fun returnsFailureWhenNoAmountAnywhere() {
        val text = "Dear Customer, please update your KYC at your earliest convenience. -HDFC Bank"
        val r = parser.parse(text)
        assert(r is ParseResult.Failure) { "expected Failure, got $r" }
    }
}
```

- [ ] **Step 3: Run — fails**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcAmountOnlyTest
# Expected: ClassCastException on the first test (no fallback pattern yet)
```

- [ ] **Step 4: Add pattern + extractor**

Append to `patterns` (LAST — must stay last so earlier patterns take precedence):
```kotlin
Pattern(
    name = "amount_only",
    regex = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
)
```

Append to `extract` `when`:
```kotlin
"amount_only" -> {
    val amount = com.jar.parser.parseAmountToPaise(match.groupValues[1]) ?: return null
    Extracted(amount = amount, merchant = null, balance = null, accountLast4 = null)
}
```

- [ ] **Step 5: Run — passes**

```bash
./gradlew :app:testDebugUnitTest
# Expected: all tests PASS
```

- [ ] **Step 6: Commit**

```bash
git add app/src/test/resources/hdfc_samples/amount_only_001.* \
        app/src/main/kotlin/com/jar/parser/hdfc/HdfcParser.kt \
        app/src/test/kotlin/com/jar/parser/HdfcAmountOnlyTest.kt
git commit -m "feat(parser): add HDFC amount_only fallback pattern"
```

---

## Task 13: Fixture-based parameterized test harness

Walks every `.txt` / `.expected.json` pair in `hdfc_samples/` and asserts the parser's output matches. This is what real samples plug into without code changes.

**Files:**
- Create: `app/src/test/kotlin/com/jar/parser/ExpectedSample.kt`
- Create: `app/src/test/kotlin/com/jar/parser/HdfcFixtureSuiteTest.kt`

- [ ] **Step 1: Define the fixture schema**

`app/src/test/kotlin/com/jar/parser/ExpectedSample.kt`:
```kotlin
package com.jar.parser

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedSample(
    val matchedPattern: String? = null,    // null means "parser should fail on this input"
    val amount: Long? = null,
    val merchant: String? = null,
    val balance: Long? = null,
    val accountLast4: String? = null,
    val minConfidence: Float? = null,
    val maxConfidence: Float? = null
)
```

- [ ] **Step 2: Write the parameterized harness**

`app/src/test/kotlin/com/jar/parser/HdfcFixtureSuiteTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import java.io.File

class HdfcFixtureSuiteTest {

    private val parser = HdfcParser()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Resolves the samples directory from the classpath. Works from any working dir.
     */
    private fun samplesDir(): File {
        val url = javaClass.classLoader.getResource("hdfc_samples")
            ?: error("hdfc_samples/ not on classpath — check src/test/resources")
        return File(url.toURI())
    }

    @Test fun everyFixtureMatchesItsExpected() {
        val dir = samplesDir()
        val pairs = dir.listFiles { f -> f.extension == "txt" }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertNotNull("no fixtures found", pairs)
        assert(pairs.isNotEmpty()) { "no fixtures found in $dir" }

        val failures = mutableListOf<String>()
        for (txt in pairs) {
            val expFile = File(txt.parentFile, txt.nameWithoutExtension + ".expected.json")
            if (!expFile.exists()) {
                failures += "${txt.name}: missing ${expFile.name}"
                continue
            }
            val expected = json.decodeFromString(ExpectedSample.serializer(), expFile.readText())
            val input = txt.readText().trim()
            val actual = parser.parse(input)

            val diff = compare(txt.name, expected, actual)
            if (diff != null) failures += diff
        }
        if (failures.isNotEmpty()) {
            fail("fixture mismatches:\n" + failures.joinToString("\n"))
        }
    }

    private fun compare(name: String, expected: ExpectedSample, actual: ParseResult): String? {
        // expected.matchedPattern == null => expect Failure
        if (expected.matchedPattern == null) {
            return if (actual is ParseResult.Failure) null
                   else "$name: expected Failure, got $actual"
        }
        if (actual !is ParseResult.Success) return "$name: expected Success, got $actual"

        val errs = buildList {
            if (actual.matchedPattern != expected.matchedPattern)
                add("matchedPattern ${actual.matchedPattern} != ${expected.matchedPattern}")
            if (expected.amount != null && actual.amount != expected.amount)
                add("amount ${actual.amount} != ${expected.amount}")
            if (expected.merchant != null && actual.merchant != expected.merchant)
                add("merchant ${actual.merchant} != ${expected.merchant}")
            if (expected.balance != null && actual.balance != expected.balance)
                add("balance ${actual.balance} != ${expected.balance}")
            if (expected.accountLast4 != null && actual.accountLast4 != expected.accountLast4)
                add("accountLast4 ${actual.accountLast4} != ${expected.accountLast4}")
            if (expected.minConfidence != null && actual.confidence < expected.minConfidence)
                add("confidence ${actual.confidence} < min ${expected.minConfidence}")
            if (expected.maxConfidence != null && actual.confidence > expected.maxConfidence)
                add("confidence ${actual.confidence} > max ${expected.maxConfidence}")
        }
        return if (errs.isEmpty()) null else "$name: ${errs.joinToString("; ")}"
    }
}
```

- [ ] **Step 3: Run — should pass against all five seed fixtures**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.HdfcFixtureSuiteTest
# Expected: PASS (all 5 fixtures from Tasks 8-12 match)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/test/kotlin/com/jar/parser/ExpectedSample.kt \
        app/src/test/kotlin/com/jar/parser/HdfcFixtureSuiteTest.kt
git commit -m "test(parser): fixture-based parameterized suite"
```

---

## Task 14: `ParseRateReport` — corpus success-rate metric

Walks every `.txt` in `hdfc_samples/`, calls `HdfcParser.parse`, counts `Success` vs `Failure`. Logs the rate and fails the build only if below a configurable threshold (0% during seed phase — so the test passes unconditionally today but begins enforcing once real samples push the corpus past 50).

**Files:**
- Create: `app/src/test/kotlin/com/jar/parser/ParseRateReportTest.kt`

- [ ] **Step 1: Write the report test**

`app/src/test/kotlin/com/jar/parser/ParseRateReportTest.kt`:
```kotlin
package com.jar.parser

import com.jar.parser.hdfc.HdfcParser
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File

class ParseRateReportTest {

    private val parser = HdfcParser()

    /**
     * Release gate threshold. Ramp this up as the corpus grows:
     *  - Seed phase (< 50 samples): 0.0 — report only, no enforcement
     *  - Once corpus ≥ 50: raise to 0.95 per spec §11 release gate
     */
    private val minRate: Float = 0.0f

    @Test fun corpusParseRateMeetsThreshold() {
        val url = javaClass.classLoader.getResource("hdfc_samples") ?: error("samples dir missing")
        val dir = File(url.toURI())
        val samples = dir.listFiles { f -> f.extension == "txt" }?.toList() ?: emptyList()
        assertTrue("no samples on classpath", samples.isNotEmpty())

        var success = 0
        val failures = mutableListOf<String>()
        for (s in samples) {
            val r = parser.parse(s.readText().trim())
            if (r is ParseResult.Success) success++ else failures += s.name
        }
        val rate = success.toFloat() / samples.size
        println("=== HDFC ParseRateReport ===")
        println("total=${samples.size} success=$success failed=${failures.size} rate=${"%.2f".format(rate * 100)}%")
        if (failures.isNotEmpty()) println("failed: $failures")
        assertTrue(
            "parse rate $rate below threshold $minRate",
            rate >= minRate
        )
    }
}
```

- [ ] **Step 2: Run**

```bash
./gradlew :app:testDebugUnitTest --tests com.jar.parser.ParseRateReportTest --info
# Expected: PASS, stdout shows the report line:
#   === HDFC ParseRateReport ===
#   total=5 success=5 failed=0 rate=100.00%
```

- [ ] **Step 3: Commit**

```bash
git add app/src/test/kotlin/com/jar/parser/ParseRateReportTest.kt
git commit -m "test(parser): add corpus ParseRateReport (seed threshold = 0.0)"
```

---

## Task 15: Sanity run + tag Phase 1 done

- [ ] **Step 1: Full test run**

```bash
./gradlew :app:testDebugUnitTest
# Expected: BUILD SUCCESSFUL, all tests PASS
```

- [ ] **Step 2: Check sample directory is clean**

```bash
ls app/src/test/resources/hdfc_samples/
# Expected: upi_sent_001.{txt,expected.json}, card_spent_001.{txt,expected.json},
#           debit_std_001.{txt,expected.json}, upi_slash_001.{txt,expected.json},
#           amount_only_001.{txt,expected.json}
# That's 10 files total.
```

- [ ] **Step 3: Tag**

```bash
git tag phase-1-parser-foundation
# or: git tag -a phase-1-parser-foundation -m "Phase 1: parser foundation complete"
```

---

## What's next (not in this plan)

- **Phase 2 plan** (Room + settings + period math + repository) — written separately.
- **Phase 3 plan** (NotificationListenerService + pipeline + dedupe glue) — written separately.
- **Phase 4 plan** (Compose UI: jar canvas, two pages, onboarding) — written separately.
- **Phase 5 plan** (polish + release prep) — written separately.

**Ongoing during Phase 2+ work:** as you receive real HDFC SMS samples, drop them into `app/src/test/resources/hdfc_samples/` as new `.txt` + `.expected.json` pairs. The fixture suite picks them up automatically. When the corpus crosses 50 real samples, bump `ParseRateReportTest.minRate` to `0.95f` — this becomes the spec's release gate.
