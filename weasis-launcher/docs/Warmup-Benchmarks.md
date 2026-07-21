# Weasis Warm-up Benchmarks — Launch → First Main Window

Purpose: track how each optimization moves the **time between process launch and the main
window being shown**, so we can decide which options to keep. The reference metric is
`*PERF* Global starting` (JVM start → `WeasisWin` visible), emitted by the `weasis-base-ui`
activator, plus `*PERF* JVM start` for the JVM-init slice.

- **Environment:** packaged Linux app-image, Temurin 26.0.1, dev workstation (results are
  relative — absolute numbers vary by machine and disk).
- **Build:** 4.7-pf4j, common libs on the real classpath (`common-lib/`), plugins `.jar.xz`
  in `bundle/`, base CDS archive present unless noted.
- **Warm** = OS file cache primed and, for AOT, the cache already trained. **Cold** = first
  launch after install/update (also trains the AOT cache).
- ⏱ **measured** on this machine · 📐 **projected** (not yet implemented) — projections are
  estimates from the phase breakdown below, to be replaced with measurements as each lands.

---

## 1. Baseline configurations (measured)

End-to-end = what the user actually waits for, including the stage-1 relaunch when AOT is on.

| # | Configuration | JVM start | Global starting (end-to-end) | Δ vs baseline | Source |
|---|---------------|-----------|------------------------------|---------------|--------|
| 0 | **Baseline** — AOT off, no CDS | ~122–158 ms | **~1.93 s** | — | ⏱ |
| 1 | AOT off, base CDS archive | ~120 ms | ~2.07 s | +0.14 s (worse) | ⏱ |
| 2 | AOT **use** (warm), in-process only | 71 ms | ~1.63 s | −0.30 s | ⏱ |
| 3 | AOT **use** (warm), end-to-end (incl. ~0.40 s relaunch) | 71 ms | ~2.02 s | +0.09 s (worse) | ⏱ |
| 4 | AOT **train** (cold, first launch) | 71 ms | ~2.9 s (one-time) | +0.97 s once | ⏱ |

**Reading of the baselines**

- The AOT cache is genuinely faster **in-process** (row 2: −300 ms, JVM start 71 ms vs
  ~140 ms), but the ~400 ms stage-1 native-launcher **relaunch cancels it** end-to-end
  (row 3). Net today: neutral-to-slightly-worse.
- The base CDS archive alone (row 1) does not help here and is slightly slower — the win it
  targets (class-loading) is not the dominant cost (see §2). Kept only because dynamic AOT
  assembly requires a base archive.
- So the lever is **not** "add more class-sharing" — it is the wall-clock structure in §2.

---

## 2. Where the ~1.9 s actually goes (warm, AOT off)

Phase timeline from the launcher/boot logs. Class loading is spread across every phase; CPU
profiling shows ~90 samples total → the startup is **sequential I/O + single-threaded phase
work**, not CPU-bound.

| Phase | Time | What runs |
|-------|------|-----------|
| JVM init + launcher static init | ~271 ms | JVM bring-up, Logback boot appender, `AppLauncher` statics *(this is the slice AOT cuts to ~71 ms)* |
| Trust store + config | ~110 ms | SSL context merge (~98 ms) + `ConfigData` (~12 ms) |
| `loadProperties` | ~476 ms | weasis.properties, locale, **FlatLaf L&F init, splash window** |
| `AutoProcessor` + `loadPlugins` | ~689 ms | cache checks, pf4j jar scan + classloaders *(inflated by SNAPSHOT re-decompression; release skips that)* |
| `startPlugins` → window shown | ~677 ms | **15 of 17 plugins start before `base-ui`** (dicom-codec/dcm4che, OpenCV native load, …), then `WeasisWin` build |
| **Total** | **~2.2 s** | (profiled run; leaner than baseline row 0 varies by run) |

Hot methods (JFR): LZMA/xz decompression, Marlin renderer, JSVG SVG, FlatLaf JSON parser,
Xerces XML, TrueType font init. Confirms the cost is decompression + UI/font/theme init +
plugin start ordering — **not** class loading.

---

## 3. The Weasis plugin cache (per-user download cache)

Separate from the AOT cache. Populated by `AutoProcessor` from the `weasis.plugins.N`
locations, under `${weasis.cache.dir}/cache/`:

| Folder | Constant | Holds | Loaded by |
|--------|----------|-------|-----------|
| `plugins/` | `F_PLUGIN_FOLDER` | pf4j plugins (jars with a `Plugin-Id` / `plugin.id`) | pf4j child classloaders |
| `common-lib/` | `F_LIB_FOLDER` | non-plugin jars (weasis-core, jackson, …) | agent → system classloader |
| `i18n/` | `F_TRANSLATION_FOLDER` | `*-i18n` translation bundles | pf4j |
| `tmp/` | — | download staging | — |

The plugin-vs-common-lib split is decided **at download time** by `isValidPF4JPlugin`
(manifest descriptor present → `plugins/`, otherwise → `common-lib/`).

**The three decisions that govern cost** — for each `weasis.plugins.N` entry, in order:

1. **`onClasspath` skip** (`processAutoProperties`): `!SNAPSHOT && java.class.path contains
   name.jar` → **download skipped entirely**. In the packaged app this is exactly the
   `common-lib/` set — those jars ship on the app classpath and are never copied into the
   per-user cache.
2. **"already exists" skip** (`downloadPlugin`, non-SNAPSHOT only): file already in
   `plugins/` / `common-lib/` / `i18n/` → returned as-is, **no download, no decompress**.
3. **SNAPSHOT** → **neither skip applies** → downloaded, `.xz`-decompressed, validated and
   moved **every launch**.

**Clean policy** (`weasis.plugin.cache.clean`) — the big packaged-vs-dev difference:

- **Packaged (dist `base.json`): `none`** → cache persists across launches.
- **Dev (`weasis-launcher/conf/base.json`): `onFirstInit`** → the **entire cache is wiped
  every launch** and rebuilt from `file:${maven.localRepository}/…`. Intentional: a dev
  rebuild keeps the same `4.7.0` version, so without the wipe the stale jar would be reused
  (see `[[pf4j-native-lib-loading]]`).
- Crash recovery also forces `onFirstInit` once, so a crashed install rebuilds clean.

### Scenarios: best → worst

| Scenario | What happens | Cost |
|----------|--------------|------|
| **Stable release, 2nd+ launch** (best) | All plugin jars already uncompressed in `plugins/`, non-SNAPSHOT → every entry hits skip #1 or #2. Nothing downloaded or decompressed — the cache is just read. | Minimal (still pays pf4j load + `start()`) |
| **Stable release, first run** (cold) | `plugins/` empty → each `bundle/*.jar.xz` from the app image is copied to `tmp/`, **XZ-decompressed**, validated, moved to `plugins/`. common-libs skipped (on classpath). | High one-time: xz-decompress ~13–17 jars — inflates the `loadPlugins` phase in §2 |
| **Remote codebase (`cdb=…`), first run** | Same as cold, but jars come over **HTTP** from the server. Non-SNAPSHOT → cached and reused next launch. | Highest first run (network) |
| **SNAPSHOT jars** (worst steady state) | Skips #1 and #2 both disabled → **re-downloaded + re-decompressed + re-validated every launch, forever**. No caching benefit for those jars. | High, every launch |
| **Dev / IDE** | `onFirstInit` wipes the whole cache each launch → re-copies all jars from the local Maven repo (plain jars, no `.xz`). Always fresh. | Moderate, every launch (by design) |
| **Dev / IDE, warm-up mode** | `-Dweasis.plugin.cache.warmup=true` overrides the wipe and treats the `-SNAPSHOT` dev jars as immutable → first run cold, every later run **warm**, like a stable release. | First run cold, rest minimal |

> **Benchmarking warm starts in dev.** Dev builds are all `-SNAPSHOT` (`4.7.2-SNAPSHOT`), so
> by default they can never start warm (SNAPSHOT jars re-copy every launch). Set
> `-Dweasis.plugin.cache.warmup=true` (`AutoProcessor.PLUGIN_CACHE_WARMUP`, default `false`)
> to keep the cache and reuse the cached jars. **Only for benchmarking** — a code change to a
> module will *not* be picked up while it is on, so turn it off (or clear the cache) after a
> rebuild.

**Two consequences for optimization:**

1. Even the best case still pays the plugin `start()` cost — the cache only saves
   download+decompress; pf4j still loads `plugins/*.jar` and runs all ~17 activators every
   launch (never AOT-cacheable). This is why **Opt A** helps independently of any cache.
2. SNAPSHOT is a trap for nightly/snapshot **server** deployments: a snapshot-versioned
   bundle re-downloads and re-decompresses on every launch, so first-window time never
   improves across launches. Release builds (fixed versions) are unaffected.

---

## 4. The AOT cache (per-user JDK class cache)

`AotCache` — independent of the plugin cache. One file per install, named by identity:

```
${weasis.cache.dir}/aot/weasis-<jpackage.app-version>-<hex(java.home mtime)>.aot
```

`pruneOtherCaches()` deletes every other `*.aot`, so the folder holds at most one. The key
depends on **only two things**: the app version and the runtime-image timestamp (changes on
reinstall/JDK update). **The downloaded plugin/lib set is NOT part of the key** — nothing in
`bundle/`, no SNAPSHOT, no remote-codebase lib ever invalidates or refreshes it.

**What is cached:** only system-classloader classes — the launcher jar + everything jpackage
put on the classpath from `common-lib/` (~8.6k classes warm). **Never cached:** pf4j plugin
classes (child classloaders) and anything agent-appended (`appendToSystemClassLoaderSearch`)
— a JVM constraint, not a policy choice.

**Mode** (`buildToolOptions`): cache file exists → `-XX:AOTCache=` (**use**, fast); missing →
`-XX:AOTCacheOutput= -XX:-AOTClassLinking` (**train**, one-time, assembly spawns `bin/java`
at exit).

### Scenarios: best → worst

| Scenario | Cache key changes? | Mode | Cost |
|----------|--------------------|------|------|
| **Stable release, 2nd+ launch** (best) | no | **use** | JVM start ~71 ms, ~8.6k classes shared. Steady state. |
| **First run after install** | new key (no file) | **train** | One-time ~2.9 s + assembly, then every later launch is "use". |
| **App update** (4.7.0 → 4.7.1) | yes (version) | **train once** | Old `.aot` pruned, retrains once. Expected. |
| **Runtime / JDK update** (reinstall) | yes (java.home mtime) | **train once** | Same as app update — a new JVM never reads a mismatched cache. |
| **Plugin download** to `bundle/` | **no** | unchanged | Plugin classes aren't cacheable → no retrain, no effect. |
| **Remote SNAPSHOT common-lib download** | **no** | unchanged | Downloaded, agent-appended, **not** AOT-cached. Cache keeps mapping the packaged classpath copy. |
| **Bad / incompatible cache** | — | use → fail → recover | Child dies <10 s → `AotCache` deletes the cache, relaunches once **without** AOT; next launch retrains. |

**The SNAPSHOT / remote-update caveat:** the plugin cache and the AOT cache don't talk to
each other. A remote-codebase deployment that ships an updated common library either (a)
uses a new filename → downloaded + agent-appended → runs but is **not** AOT-cached (no
warm-up benefit), or (b) duplicates a packaged `common-lib/` class → the **classpath (cached)
copy wins** (agent appends to the end of the search path), silently shadowing the update.
This is a remote-update correctness caveat independent of AOT, but AOT makes it more
surprising because those classes come from the shared archive.

- **Best case:** installed stable release, second launch onward — pure "use" mode, ~71 ms
  JVM start, nothing on the critical path.
- **Worst case:** first launch after an install *or* update — one "train" run (~2.9 s +
  assembly); plus a SNAPSHOT/remote-updated common-lib gets no AOT benefit.

---

## 5. Optimization options (progression)

Applied roughly in impact order. "Cumulative" assumes the rows above it are already applied.
Replace 📐 rows with ⏱ as each is implemented and measured.

| Opt | Change | Mechanism | Est. gain | Cumulative target | Status |
|-----|--------|-----------|-----------|-------------------|--------|
| **A** | **Show window before non-essential plugins** | Progressive start: eager DICOM path + `base-ui` → open launch images → then start the deferred secondary plugins; they register toolbars/views dynamically | 📐 **−0.4 to −0.6 s** | ~1.4 s | **implemented (flag, default off)** |
| **B** | **Overlap independent boot phases** | Run SSL trust-store merge (~98 ms) + `AutoProcessor` cache check on background threads while the EDT builds L&F/splash | 📐 **−0.2 to −0.3 s** | ~1.1 s | proposed |
| **C** | **`Bootstrap` split → make AOT net-positive** | Dependency-free stage-1 main class (skips Logback/static init) cuts relaunch from ~400 ms to ~100 ms, so AOT's −300 ms in-process win becomes a real net gain | 📐 **−0.3 to −0.4 s** | ~0.8 s | proposed |
| **D** | Re-enable `AOTClassLinking` + JEP 515 profiles | Blocked by JDK 26 `InternalError` at cache use; revisit on newer JDK. Also lifts post-window warmup (first render), not measured here | 📐 further | — | blocked (JDK) |
| **E** | Ship common-lib jars uncompressed (done) | `.jar.xz` → plain jars in `common-lib/` on classpath; no per-launch xz decompress for common libs | ✅ folded into baseline | — | done |
| **F** | **Un-shade plugin libraries into `common-lib/`** | Stop shading dcm4che + parsson + jakarta.json + hk2 + weasis-dicom-tools into `dicom-codec`; ship them as separate no-`Plugin-Id` bundle jars → auto-routed to `common-lib/` → AOT-cached | 📐 modest alone, **grows AOT set by ~800 classes**; compounds with C/D | — | proposed (do after C) |

**Realistic combined target:** from **~2.0 s → ~1.0–1.2 s** to the main window on this
machine, with **A + C** delivering most of it.

### Opt A detail — progressive startup (DICOM path first)

The overwhelmingly common launch opens DICOM images, so the eager set keeps the **DICOM viewing
path** — `dicom-codec → dicom-explorer → dicom-viewer2d`, the OpenCV native decoder, and
`base-ui` (started **last**, so the window is built after those register their factories). Then
`executeCommands(...)` opens the launch images, and only afterwards do the **secondary** plugins
start behind the visible window.

- **Mechanism:** `WeasisPluginManager.startEagerPlugins(mainUi, deferred)` +
  `startRemainingPlugins()`, with `executeCommands` moved between them in `WeasisLauncher`. It's a
  **deny-list** (defer secondary plugins), not an allow-list — so codec/explorer/viewer2d/OpenCV
  stay eager automatically and no arch-specific native id has to be enumerated. Every plugin
  `start()` is timed: `*PERF* Plugin start, id:<id> time:<ms>` + per-phase totals.
- **Enable / measure (default is unchanged all-at-once start):**
  - `-Dweasis.plugins.progressive=true` → uses the built-in deferred set (`dicom-sr, rt, au, wave,
    send, qr, isowriter, base-viewer2d, viewer3d, jogamp, acquire-*`).
  - `-Dweasis.plugins.deferred="id1 id2 …"` → explicit override of the deferred set.
  - Best measured in dev with `-Dweasis.plugin.cache.warmup=true` (warm cache). Compare
    `*PERF* Global starting` and the per-plugin lines with/without the flag.
- **Watch for (why it's default-off until validated):** `base-ui` now starts *before* the
  deferred plugins (today it starts near-last), so `WeasisWin` is built before their toolbars/
  tools/`SeriesViewerFactory` register — those should attach dynamically a moment later, but a
  hard dependency at window-build time would surface here. Also: opening an initial **SR/RT/ECG**
  object renders in the default viewer until its (deferred) plugin starts. Turn the flag off (or
  add the id to the eager side) if either misbehaves.

### Opt F detail — un-shade `dicom-codec` (and check other shaded plugins)

`weasis-dicom-codec` is a shaded fat jar: of **913 classes only 114** (`org.weasis.dicom.codec`)
are the plugin; **~800 (~87%) are embedded libraries** that load through the pf4j plugin
classloader and are therefore **never AOT-cacheable** — dcm4che (~470), Parsson + jakarta.json
(~103), weasis-dicom-tools (`hp/web/ref/param/mf/macro/op/geom/util/tool`, ~211), hk2 (12).
`dicom-codec` starts at **level 30, before the base-ui window (level 60)**, so all of it is on
the critical path, and **9 downstream DICOM plugins** reach dcm4che through the codec
classloader today.

- **Mechanism (already exists):** the packaging split step routes any bundle jar **without** a
  `Plugin-Id` manifest to `common-lib/` on the classpath. Stop shading → ship the libraries as
  separate bundle jars → they become AOT-cacheable like docking-frames/weasis-core, and the 9
  downstream plugins get dcm4che via parent-first (shared, not duplicated per classloader).
- **Secondary wins:** plugin jar 2.1 MB → ~0.3 MB; SPI (`ImageIO`, `jakarta.json.spi.JsonProvider`)
  resolves more reliably from the system classloader — the `osgi-resource-locator` bridge may
  become droppable.
- **Caveats:** AOT caches classes, not the dcm4che dictionary XML **parse** at codec init (that
  is unchanged). Payoff is coupled to C/D — sequence **after C**. Validation required: dcm4che
  ImageIO decode + dcm4che-json still resolve their SPIs; `joml` is currently both a common-lib
  **and** shaded here (a duplication this removes).

**Survey of every shaded module** (only *plugins* matter — a shaded *common-lib* is already
cached). "On path?" = loads before a useful first window.

| Shaded module | Classes | Lib (non-Weasis) | Size | Level | On path? | Embedded libs | Verdict |
|---------------|---------|------------------|------|-------|----------|---------------|---------|
| `weasis-core` | — | — | — | 12 | already common-lib → **already cached** | scribejava, xchart, lablib-checkboxtree, VectorGraphics2D | nothing to do |
| `weasis-dicom-codec` | 913 | ~800 (87%) | 2.1 M | 30 | ✅ yes (decode first DICOM) | dcm4che, Parsson, jakarta.json, weasis-dicom-tools, hk2 | **Opt F — do first** |
| `jogamp` | 2151 | 2149 (99.9%) | 4.6 M | 120 | ❌ no (3D, post-window) | JOGL / gluegen-rt / jocl | max-coverage only; **high risk** (native loading tied to its classloader) |
| `weasis-dicom-isowriter` | 159 | 153 (96%) | 280 K | 110 | ❌ no (ISO burn, rare) | iso9660-writer, sabre | minor, low priority |
| `weasis-acquire-explorer` | 99 | 28 (28%) | 236 K | acquire | ❌ no (Dicomizer opts out of AOT) | usbdrivedetector, powershell-lib-java | **skip** |

Priority: **dicom-codec ≫ jogamp (max coverage, risky) > isowriter (minor) ≫ acquire-explorer (skip)**.
Only dicom-codec moves first-window time; the rest affect total cached-class count and
post-window feature responsiveness.

---

## 6. Keep / drop decisions

| Item | Verdict | Rationale |
|------|---------|-----------|
| Common libs on real classpath (`common-lib/`) | **Keep** | Prerequisite for any AOT/CDS caching; also removes per-launch xz decompression of the biggest jars |
| Base CDS archive (`--generate-cds-archive`) | **Keep (conditional)** | No standalone win, but required for dynamic AOT assembly; cheap to retain |
| AOT cache (relaunch) **as-is** | **Hold** | Neutral end-to-end until Opt C removes the relaunch tax; keep behind `-Dweasis.aot.cache` but don't claim a win yet |
| AOT cache **+ Bootstrap split (C)** | **Target** | This is what turns AOT from neutral into a net −0.3 s |
| Opt A (early window) | **Do first** | Largest single lever, no JDK dependency, no AOT dependency |

---

## 7. How to reproduce a measurement

```bash
# Fresh build (stale target/ jars are OSGi-era and will lie)
mvn -B clean install
mvn -B -P compressXZ -f weasis-distributions clean package
weasis-distributions/script/package-weasis.sh --jdk ~/.jdks/temurin-26.0.1 --no-installer

# Cold (train): wipe the per-user AOT cache first, then launch and read boot.log
#   grep '*PERF*' <state>/log/boot.log   →  JVM start / AOT mode / relaunch overhead / Global starting
# Warm (use):  launch again without wiping the cache
# AOT off:     -Dweasis.aot.cache=false     No CDS: add -Xshare:off
```

**Warm start in dev (IDE), without repackaging** — dev jars are `-SNAPSHOT`, so a plain dev
launch is always cold. To benchmark a warm plugin cache:

```bash
# 1. One cold launch to populate the cache, then every launch after is warm:
-Dweasis.plugin.cache.warmup=true
#    boot.log shows: *PERF* Plugin cache warm-up mode enabled ...
# 2. Turn it OFF (or clear <cache>/cache/) after any module rebuild, or the stale jar sticks.
```

Add a per-plugin `*PERF*` timer around each activator `start()` before tackling Opt A so the
slow activators (dicom-codec, OpenCV native load) are ranked by data, not guessed.

> Numbers in §1–§2 and §3–§4 are from this machine and will differ elsewhere; what matters is the
> **relative** progression as each option is applied. Update the tables in place as rows move
> from 📐 to ⏱.
