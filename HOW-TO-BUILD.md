# HOW TO BUILD — soybean-admin-quarkus

Build and run guide for the Soybean Admin backend. Written to be **OS- and
architecture-agnostic**: every command has a Linux/macOS form (`./gradlew`) and a
Windows form (`gradlew.bat`). No absolute paths, no machine-specific assumptions.

---

## 1. Stack (what you are building)

| Component        | Version            | Notes                                             |
|------------------|--------------------|---------------------------------------------------|
| Language         | Kotlin `2.1.0`     | KSP `2.1.0-1.0.29`                                 |
| Framework        | Quarkus `3.17.7`   | Reactive (Vert.x), SmallRye JWT, Panache          |
| Build tool       | Gradle `8.12`      | Via the bundled wrapper — do **not** install Gradle manually |
| JDK              | **21** (LTS)       | `.sdkmanrc` pins `21.0.4-graal`; any JDK 21 works |
| Formatter        | Spotless + ktlint `1.5.0` | Enforces a license header + ktlint rules   |
| Modules          | `shared`, `system`, `domain` | `system` is the runnable Quarkus app    |

Runtime infrastructure (only needed to **run**, not to compile): PostgreSQL, Redis,
MongoDB, Kafka — all provided by `compose.yml`.

---

## 2. Prerequisites

### 2.1 A JDK 21 (required)

Quarkus 3.17 + Kotlin 2.1 target **JDK 21**. You need a JDK 21 on the machine; the
distribution/vendor does not matter (Temurin, GraalVM, Zulu, Corretto, Oracle, or an
IDE-bundled JBR all work).

Check what you have:

```bash
java -version
```

If it does not say `21`, get one via any of these (pick one):

- **SDKMAN (Linux/macOS/WSL)** — respects the project's `.sdkmanrc`:
  ```bash
  sdk env install      # installs the pinned 21.0.4-graal
  sdk env              # switches the current shell to it
  ```
- **Homebrew (macOS):** `brew install openjdk@21`
- **Windows:** install "Eclipse Temurin 21" (MSI) or `winget install EclipseAdoptium.Temurin.21.JDK`
- **Any JDK 21 you already have** (including one bundled with an IDE): see
  §5.1 for how to point the build at it without changing your global setup.

### 2.2 Docker + Docker Compose (only to run, or for native builds)

Needed to start the databases (§4.2) and for container-based native builds (§4.4).
Docker Desktop (macOS/Windows) or Docker Engine + `docker compose` (Linux) both work.

> You do **not** need Docker just to compile the project (§4.1).

---

## 3. First checkout

Nothing to install beyond a JDK — the Gradle **wrapper** downloads the correct Gradle
version automatically on first run. From the project root:

```bash
# Linux / macOS
./gradlew --version

# Windows
gradlew.bat --version
```

This prints the Gradle and JVM it will use. Confirm the JVM line reads `21`.

---

## 4. Build & run

All commands run from the project root. There is also a `Makefile` with shortcuts
(`make build`, `make dev-run`, etc.) if you have `make`.

### 4.1 Clean build (compile everything, no cache)

```bash
# Linux / macOS
./gradlew clean build

# Windows
gradlew.bat clean build
```

To skip tests: append `-x test`.

### 4.2 Run in dev mode (live reload)

Dev mode needs the infrastructure up first:

```bash
docker compose up -d          # postgres, redis, mongo, kafka
```

Then start the app (`system` is the Quarkus module):

```bash
# Linux / macOS
./gradlew :system:quarkusDev

# Windows
gradlew.bat :system:quarkusDev
```

App: <http://localhost:8080> — Swagger UI: <http://localhost:8080/q/swagger-ui>.

Stop the infra when done: `docker compose down` (add `-v` to also wipe the volumes).

### 4.3 Production JVM build

```bash
./gradlew clean build          # produces system/build/quarkus-app/
```

Run it:

```bash
java -jar system/build/quarkus-app/quarkus-run.jar
```

### 4.4 Native build (optional, needs Docker or a local GraalVM)

Container build (no local GraalVM required):

```bash
# Linux
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true

# macOS (see Makefile target build-native-mac)
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false -x test
```

### 4.5 Formatting (Spotless / ktlint)

The build fails if code is not formatted. Check and auto-fix:

```bash
./gradlew spotlessCheck        # verify
./gradlew spotlessApply        # auto-format
```

---

## 5. Troubleshooting

### 5.1 "Build fails: wrong Java version / needs JDK 21" (most common)

Symptom: errors mentioning an unsupported class file version, a toolchain that cannot
be provisioned, or Quarkus refusing to start; `./gradlew --version` shows a JVM other
than 21.

**Fix — point the build at a JDK 21 for this build only, without changing your global
Java.** Set `JAVA_HOME` in the current shell to any JDK 21 directory:

```bash
# Linux / macOS — replace the path with your JDK 21 location
export JAVA_HOME=/path/to/any/jdk-21
./gradlew clean build
```

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\path\to\any\jdk-21"
gradlew.bat clean build
```

Any JDK 21 works, including one **bundled with an IDE**. For example, IntelliJ/Android
Studio ship a JetBrains Runtime 21 (JBR); point `JAVA_HOME` at that JBR directory if you
don't want to install a separate JDK. Verify with `./gradlew --version` (the JVM line
must read `21`).

> The project applies the `foojay-resolver` plugin, so if a Gradle **toolchain** is
> requested Gradle can auto-download a matching JDK. Setting `JAVA_HOME` as above is the
> most reliable fix and does not depend on network access to the toolchain repository.

### 5.2 Gradle configuration cache errors

`gradle.properties` enables `org.gradle.configuration-cache=true`. If a task misbehaves
after unusual changes, run once with the cache disabled to isolate the issue:

```bash
./gradlew clean build --no-configuration-cache
```

### 5.3 spotlessCheck fails the build

Formatting is enforced (ktlint + a mandatory license header). Auto-fix and rebuild:

```bash
./gradlew spotlessApply && ./gradlew build
```

### 5.4 Dev mode / tests fail to connect to a database

`quarkusDev` and integration tests expect PostgreSQL/Redis/MongoDB/Kafka reachable on
their default ports. Start them first:

```bash
docker compose up -d
docker compose ps        # confirm all services are healthy
```

Port already in use? Either stop the conflicting local service or edit the host-side
port mappings in `compose.yml`.

### 5.5 Out-of-memory during build

The build is memory-hungry (`org.gradle.jvmargs=-Xmx2g`). On a constrained machine,
lower parallelism or raise the heap:

```bash
./gradlew clean build --no-parallel
# or edit org.gradle.jvmargs in gradle.properties
```

### 5.6 Stale build / weird incremental errors

Do a fully clean build and stop stale daemons:

```bash
./gradlew --stop
./gradlew clean build
```

### 5.7 First run is slow / "hangs"

The first `./gradlew` invocation downloads Gradle 8.12 and then the full Quarkus/Kotlin
dependency graph. This is normal and one-time (cached afterwards). Ensure network access
to Maven Central.

---

## 6. Quick reference

| Task                        | Command                                             |
|-----------------------------|-----------------------------------------------------|
| Show Gradle/JVM             | `./gradlew --version`                               |
| Clean build                 | `./gradlew clean build`                             |
| Build, skip tests           | `./gradlew clean build -x test`                     |
| Dev mode (needs infra)      | `./gradlew :system:quarkusDev`                      |
| Start infra                 | `docker compose up -d`                              |
| Stop infra                  | `docker compose down`                               |
| Format check / apply        | `./gradlew spotlessCheck` / `spotlessApply`         |
| Run built jar               | `java -jar system/build/quarkus-app/quarkus-run.jar`|
| Stop Gradle daemons         | `./gradlew --stop`                                  |

On Windows, replace `./gradlew` with `gradlew.bat` in every command above.
