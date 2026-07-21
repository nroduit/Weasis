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
**Measured 2026-07-19 on the packaged Linux app-image (Temurin 26.0.1, base CDS present, A+B+C
in the build), values in ms; end-to-end = relaunch overhead + Global starting.**

| # | Configuration | JVM start | relaunch | Global starting | **end-to-end** | Δ vs baseline |
|---|---------------|-----------|----------|-----------------|----------------|---------------|
| 0 | **Baseline** — AOT off (single process) | 116–118 | — | ~2040 | **~2040** | — |
| 1 | AOT **use**, re-exec (warm), prog off | 70–73 | ~485 | ~1612 | **~2097** | +57 (worse) |
| 2 | AOT **use**, re-exec (warm), prog on | 70–75 | ~482 | ~1590–1732 | **~2130** | +90 (worse) |
| 3 | AOT **train** (cold, first launch) | 163 | 542 | 1953 | ~2495 (once) | +455 once |
| 4 | AOT off, progressive on | 116 | — | ~2036 | **~2036** | ~0 |
| 5 | **AOT use, persisted flag (single process)** | 75–77 | **none** | ~1581–1593 | **~1585** | **−455 (−22%)** ✅ |

Row 5 is the fix: after the one training run persists `-XX:AOTCache` into the launcher `.cfg`, every
warm launch starts with it already active — **single process, no re-exec** — so the ~430 ms
in-process AOT gain is no longer cancelled by the ~485 ms relaunch. Measured single-process warm
launches were consistently 1581/1584/1593 ms (2 processes → 1; relaunch-overhead line gone).

**Reading (the decisive result)**

- **AOT is genuinely ~430 ms faster in-process** (Global starting 2040 → 1612; JVM start 116 → 71;
  plugin-start total 834 → 521; `dicom-codec` 413 → 226; `base-ui` 249 → 159). But the stage-1
  **relaunch costs ~485 ms** (a whole second JVM: parent bootstrap + fork + child start), so AOT
  ends up **~57 ms net worse end-to-end** even with the Bootstrap split. The second-JVM spawn is
  the wall; C trims the parent's class loading but can't remove the JVM-start cost itself.
- **Progressive (A) buys ~0** — the plugins it defers are all cheap (2–15 ms each, ~60 ms total),
  and the one expensive plugin, `dicom-codec` (226–413 ms), must stay eager for DICOM. Points
  straight at **Opt F** (un-shade codec so its classes get AOT-cached — codec already dropped
  413 → 226 just from cached common-libs).
- **B works** — SSL trust store (~100–130 ms) is now fully overlapped: the `*PERF* Awaited system
  trust store` line is **gone** in every run (before the https-only fix it blocked ~101 ms on the
  first `file://` read). ~100 ms saved every launch, no downside.

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

Applied in impact order. **Cumulative (measured)** = end-to-end to the main window with this row
and every row above it applied, on this machine. Pre-optimization start (no B, no AOT) ≈ **2.14 s**.

| Opt | Change | Mechanism | Gain | Cumulative (measured) | Status |
|-----|--------|-----------|------|-----------------------|--------|
| **B** | **Overlap independent boot phases** | SSL trust-store merge (~100 ms) runs on a daemon thread while `ConfigData` + L&F/splash build; every HTTPS use joins it first, so downloads never precede the trust store | **−~100 ms** | **~2.04 s** | **implemented** |
| **C** | **`Bootstrap` split + persist AOT flag (C′)** | Bootstrap trims the stage-1 relaunch; C′ persists `-XX:AOTCache` into the launcher `.cfg` so warm launches are single-process with no re-exec | **−455 ms** | **~1.585 s** | **shipped default** |
| **D** | **`AOTClassLinking` + JEP 515 profiles** | Pre-linked classes + method profiles. Crashed at use on JDK ≤ 26 (JDK-8373392); **fixed in JDK 27**, so **auto-enabled from JDK 27** (version-gated, no flag needed) | **−335 ms** (JDK 27) | **~1.25 s** (JDK 27) | **auto on JDK 27+** |
| **A** | ~~Show window before non-essential plugins~~ | Progressive start — deferred plugins all cheap; the slow one, `dicom-codec`, must stay eager. Code removed. | **~0** | — | **dropped** |
| **E** | Ship common-lib jars uncompressed | `.jar.xz` → plain jars in `common-lib/` on classpath; no per-launch xz decompress for common libs | folded in | (in baseline) | done |
| **F** | ~~Un-shade plugin libraries into `common-lib/`~~ | Premise invalidated by measurement — dcm4che is already AOT-cached as a plugin class (see below) | **~0** | — | **dropped** |

**Shipped result (JDK 26):** ~2.14 s → **~1.585 s** to the main window on this machine (−26%),
from **B + C/C′**. **On JDK 27** (D on): **~1.25 s (−42%)**. A and F were tried and measured to add
~0, so both were dropped.

### Opt A — DROPPED (measured ~0 gain, code removed)

Progressive startup (defer secondary plugins, show the window on the DICOM path first) was
implemented behind `weasis.plugins.progressive` and measured: **no meaningful gain**. The deferred
plugins are all cheap (2–15 ms each, ~60 ms total) while the one expensive plugin, `dicom-codec`
(226 ms warm), must stay eager for DICOM — so there was nothing worth deferring. The flag, the
eager/deferred split (`startEagerPlugins`/`startRemainingPlugins`), and the deferred-set config
were removed; only the harmless per-plugin `startPluginsTimed` instrumentation (`*PERF* Plugin
start, id:<id>`) was kept.

### Opt D detail — AOTClassLinking on JDK 27-EA (measured working)

The faster AOT tier (`AOTClassLinking`: classes stored **pre-linked** + JEP 515 method profiles)
crashed at use on JDK ≤ 26 (`InternalError` in `AppClassLoader.<clinit>`) — the root cause is
[JDK-8373392](https://bugs.openjdk.org/browse/JDK-8373392) (*Replace CDS object subgraphs with
`@AOTSafeClassInitializer`*), **fixed in JDK 27**. So `AotCache` **version-gates** it:
`useClassLinking(Runtime.version().feature())` enables the linked tier only from JDK 27 (training
adds `-XX:-AOTClassLinking` on JDK ≤ 26). `-Dweasis.aot.classlinking=true|false` overrides the gate
for testing. Verified on **`openjdk-ea-27-ea+31` (2026-09-15)** by repackaging the app-image with
that JDK:

- **No crash.** The linked cache is used cleanly, single-process (`mode:use`, no relaunch line).
  (The `InternalError`/`AppClassLoader` strings in the log are only pf4j DEBUG lines naming the
  classloader — not a fault.)
- Measured warm: **JVM start 35 ms** (vs 71 ms non-linked, 116 ms baseline), **Global starting
  1224/1280 ms** (vs ~1585 non-linked, ~2040 baseline), **`dicom-codec` 128 ms** (vs 226 / 413).
- Cache is ~84.5 MB (vs ~73 MB non-linked) — the extra profiles/pre-linking.
- **Net: ~1.25 s end-to-end = −790 ms (−39%) vs baseline**, i.e. ~335 ms beyond the persisted-flag
  non-linked AOT.

**Auto-gated, no manual step.** On the current JDK 26 build `useClassLinking(26)` is false, so the
safe non-linked tier is used automatically; when the packaged runtime moves to **JDK 27 GA** the
linked tier turns on by itself. Re-verify once on 27 GA (27-EA is not GA). Notes:
1. **Bad-cache recovery — implemented and verified.** The flag is now persisted only *after* a
   re-exec has used the cache without an early crash (**verify-before-persist**), so a fatal cache
   can never be baked into the `.cfg`. If a cache does crash early, it is deleted, the `.cfg` flag
   is stripped, and a `weasis-<ver>-<stamp>.disabled` marker is written so AOT is skipped for that
   build (no loop, no brick) until an app/runtime update changes the stamp. Verified end-to-end on
   **JDK 26 with `classlinking=true`** (a genuinely fatal cache): L1 trains (not persisted), L2
   crashes → recovers and starts without AOT, L3 skips AOT single-process — the app started every
   time. This is the safety prerequisite for ever defaulting `classlinking` on.
2. On the 27-EA jlink runtime, `jogamp-linux-x86-64` failed to load (`libjawt.so` missing) — a
   runtime/module packaging issue to check for JDK 27, unrelated to AOT (3D only).

### Opt B detail — overlap SSL trust store with the boot work

The merged system trust store (platform certs + JVM cacerts, ~98 ms) was built **synchronously**
before `ConfigData` and the L&F/splash. None of that non-network work needs it, so it now runs on
a daemon thread and is **joined at the one network choke point** instead.

- **Mechanism:** `PlatformCertificateLoader.startAsyncSetup()` (fired first thing in
  `AppLauncher.main`) + `awaitReady()`, called at the top of `FileUtil.getAdaptedConnection` — the
  single method every launcher HTTPS use (remote config, remote prefs, plugin downloads,
  resources) routes through. So **consistency is structural**: no connection opens until the trust
  store is installed, exactly the "SSL before downloads" requirement — and it also covers remote
  config/prefs, not just downloads.
- **Gain:** overlaps ~98 ms of SSL with `ConfigData` + `loadProperties` (~488 ms of non-network
  work) → hidden on the common **local-config** launch (packaged/dev), saving ~0.1 s every launch.
  A **remote-config** launch (`cdb=`) makes its first HTTPS call during `ConfigData`, so `awaitReady`
  blocks there ~98 ms — same as before, no regression, just no gain in that case.
- **Markers:** `*PERF* Loading system trust store` (now logged from the async thread) and
  `*PERF* Awaited system trust store` (only if a consumer actually had to wait — a signal the
  overlap didn't fully hide it).
- **Not done (bigger, but warm-neutral):** overlapping the *download* phase with L&F was considered
  and rejected for now — on a warm stable-release launch there are **no downloads** (classpath +
  cached non-SNAPSHOT jars are all skipped), so it would only help cold/remote launches, and it
  couples to the splash/`WeasisLoader` progress UI. SSL is the universally-present cost, so it was
  the right target.

### Opt C detail — Bootstrap split (make the AOT relaunch net-positive)

The AOT cache is faster in-process (−300 ms) but the stage-1 relaunch cancels it end-to-end. The
cost was that stage 1 only reached `AotCache.relaunch` **after** the JVM loaded `AppLauncher`,
whose static initializer builds the whole Logback boot-log stack (and pulls in the launcher's
dependency graph). The parent paid all of that just to spawn the child and exit.

- **Mechanism:** a new dependency-free `Bootstrap.main` is the packaged `--main-class`. It calls
  `AotCache.relaunch(argv)` first; only in the non-relaunch path (stage-2 child / AOT off) does it
  reference `AppLauncher.main`, so the JVM loads `AppLauncher` (and its static Logback setup)
  **lazily, never in the stage-1 parent**. `AppLauncher.main` no longer does the relaunch check.
- **Measurement fix:** `t0` (the relaunch-overhead baseline in `AotCache.buildToolOptions`) now uses
  the parent **JVM start time** instead of the spawn time, so `*PERF* AOT relaunch overhead` +
  `*PERF* Global starting` together give the honest end-to-end number. The old spawn-time baseline
  would have hidden C's improvement (C shrinks the parent's *pre-spawn* class loading, which the old
  baseline started counting after).
- **Scope:** only the main app uses `Bootstrap` (packaged `package-weasis.sh` ×2 +
  `build-installer.yml`); the **Dicomizer** and the **IDE dev** run keep `AppLauncher` directly —
  they don't want the relaunch (Dicomizer sets `-Dweasis.aot.cache=false`). Linux
  `Weasis.desktop` `StartupWMClass` updated to `org-weasis-launcher-Bootstrap` to keep taskbar
  grouping (AWT derives WM_CLASS from the main class).
- **Validate on a packaged build:** cold run trains; warm run should show `*PERF* AOT relaunch
  overhead` markedly lower than before C, and a lower end-to-end `Global starting`. Watch the Linux
  taskbar icon still groups under the app.
- **Measured verdict:** C was necessary but **not sufficient** — the re-exec still costs ~485 ms
  (a whole second JVM), which cancels AOT's ~430 ms in-process gain (net +57 ms). The follow-up
  below removes the re-exec on warm launches and is what actually makes AOT pay off.

### Opt C′ detail — persist the AOT flag, drop the warm re-exec (the real win)

`-XX:AOTCache` must be present at JVM start, which is the *only* reason for the re-exec. So after
the cache is trained once, `AotCache.persistAotFlag` appends `java-options=-XX:AOTCache=<cache>` to
the launcher `.cfg`; every later launch then starts the JVM with the flag already set — **single
process, no re-exec**. `relaunch()` returns immediately when it sees the flag active
(`hasAotCacheFlag`).

- **Flow:** launch 1 (cache missing) trains via the one re-exec, then persists the flag (never
  *before* the training child, or it would get both `-XX:AOTCache` and `-XX:AOTCacheOutput` and
  refuse to start); launch 2+ are single-process `mode:use`. Measured **~1585 ms end-to-end vs
  ~2040 ms baseline = −455 ms (−22%)** (see §1 row 5).
- **Read-only installs:** if the `.cfg` is not writable (system install, `/opt`, Program Files),
  `persistAotFlag` returns false and it falls back to the per-launch re-exec — correct, just not
  single-process. A writable `.cfg` (app-image / per-user install) is also effectively single-user,
  so persisting a per-user cache path into it is safe.
- **Staleness / update:** the persisted line is version+runtime-stamped; a reinstall ships a fresh
  `.cfg` (line gone → retrain+repersist), and a stale path just degrades gracefully (missing cache
  → JVM runs normally) then gets replaced on the next training.

### Opt F — DROPPED (premise invalidated by measurement, 2026-07-19)

Opt F assumed pf4j **plugin-classloader** classes (and the libraries shaded into a plugin jar)
are never AOT-cached, so un-shading dcm4che into `common-lib/` would let the cache reach it. A
`-Xlog:class+load=info` run on the warm single-process AOT build proved otherwise:

- `org.dcm4che3.*` at startup: **46/46 classes `source: shared objects file`** (AOT-cached) —
  *while still shaded inside the `dicom-codec` plugin jar*.
- `org.weasis.dicom.codec.*` (the plugin's own classes): **48/48 cached** too.

So on **JDK 26 the AOT cache already serves pf4j plugin classes** (this is unlike *agent-appended*
jars via `appendToSystemClassLoaderSearch`, which are never cached — the earlier note that
misled this plan). Un-shading would move dcm4che from "cached plugin class" to "cached classpath
class" — the same cache at the same speed, **~0 warm-up gain**. `dicom-codec`'s residual ~226 ms
warm is the dcm4che **dictionary XML parse** (`uids.xml`/`dataelements.xml`, data not classes) plus
SPI/init work, neither of which un-shading touches. F's only remaining upside is cosmetic (thinner
jar, possible `osgi-resource-locator` removal) — not worth the build-refactor risk for the warm-up
goal. **Not pursued.** Original analysis kept below for the record.

### Opt F detail (original, superseded) — un-shade `dicom-codec`

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
