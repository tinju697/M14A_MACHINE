# MES A31 — M14a API Automation

Rest Assured + TestNG API automation for the **M14a** machine, with the FitMesWpfServer backend launched automatically by the suite.
Covers **valid** and **invalid** flows for login, status, start/end operation, and batch loading.

## What it does

1. `ServerSuiteListener` launches `FitMesWpfServer.exe` once at suite start.
2. TestNG runs the positive and negative `M14aApiTests` against the REST API.
3. The server is stopped when the suite finishes (or on JVM shutdown).

No UI driver, no Selenium, no WinAppDriver — pure API tests.

## Prerequisites

- JDK 11+
- Maven 3.8+ (or use Eclipse's bundled m2e)
- `FitMesWpfServer.exe` available locally

## Configure

Edit `src/main/resources/config.properties`:

- `server.exePath` / `server.workingDir` — path to `FitMesWpfServer.exe` and its folder
- `server.healthUrl` — optional readiness probe. Leave blank to just check the process is alive.
- `api.baseUrl` and each `api.endpoint.*`
- `api.auth.username` / `api.auth.password`
- `machine.id` (`M14a`), `machine.invalid.id` (`M99z`)
- `batch.valid.id`, `batch.invalid.id`

## Run

From the project folder:

```powershell
mvn clean test
```

Filter by group:

```powershell
mvn test -Dgroups=positive
mvn test -Dgroups=negative
```

From Eclipse:

- Right-click `testng.xml` → `Run As → TestNG Suite` (so the server listener fires).

## Layout

```
src/main/java/com/mes/m14a/
  api/          ApiClient, AuthApi, M14aApi   (Rest Assured)
  server/       ServerManager                 (launches FitMesWpfServer.exe)
  config/       ConfigReader
  listeners/    ServerSuiteListener, TestListener

src/test/java/com/mes/m14a/tests/api/
  M14aApiTests   positive + negative @Test groups

testng.xml     M14a-API-Positive, M14a-API-Negative
```

## What to fill in before first run

- [ ] `server.exePath` (already defaults to `F:\E Drive\MES Project- A31\M14\wpf_client Server\FitMesWpfServer.exe`)
- [ ] `api.baseUrl` + each `api.endpoint.*`
- [ ] `api.auth.username` / `api.auth.password`
- [ ] Token field name in `AuthApi.login()` (currently reads `r.path("token")`)

> The server is launched once per suite by `ServerSuiteListener` and stopped on suite finish. Set `server.autoStart=false` if you prefer to launch it manually.
