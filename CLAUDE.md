# CLAUDE.md — kinesis-mock

A mock of the AWS Kinesis API for local testing, written in Scala 3 (Typelevel/cats-effect stack). Cross-built for the JVM (fat JAR) and Scala.js (Node `main.js`, published to npm as `kinesis-local` and as a Docker image `ghcr.io/etspaceman/kinesis-mock`). Main class: `kinesis.mock.KinesisMockService`. Serves HTTPS on 4567 and HTTP on 4568.

## Toolchain

- **Java 21** via sdkman (`.sdkmanrc` = `21.0.11-amzn`). System Java is never used — `sdk env` to activate.
- **sbt 1.12.11** (`project/build.properties`); Scala **3.3.7**.
- Built on sbt-typelevel; the build is a `tlCrossRootProject` aggregating `kinesis-mock-jvm` and `kinesis-mock-js`. CI calls projects `kinesis-mock-rootJVM` / `kinesis-mock-rootJS`.
- Dependencies live in `project/LibraryDependencies.scala`. Stack: cats / cats-effect, http4s (ember-server, dsl, circe), fs2, circe, borer (CBOR), ciris (config), enumeratum, log4cats, scodec-bits. Tests: munit + munit-cats-effect + scalacheck(-effect); AWS SDK v2 / KPL / KCL are JVM `Test`-only.

## Commands

Run sbt from repo root. Pick a project explicitly since this is a cross build.

```
sbt 'project kinesis-mock-rootJVM' test
```
```
sbt 'project kinesis-mock-rootJS' test
```

Format / lint (scalafmt + scalafix are both CI-enforced; `-Xfatal-warnings` is on):

```
sbt pretty
```
(`pretty` = `fix` then `fmt`; `prettyCheck` = `fixCheck` + `fmtCheck`. CI runs `headerCheckAll scalafmtCheckAll 'project /' scalafmtSbtCheck` and `scalafixAll --check`.)

Build the JVM fat JAR (lands at `docker/image/lib/kinesis-mock.jar`):

```
sbt 'project kinesis-mock-rootJVM' assembly
```

Link the JS bundle to `docker/image/lib/main.js` (dev = fastLinkJS, release = fullLinkJS):

```
sbt 'project kinesis-mock-rootJS' fastLinkJS
```

Build / push the (JS-based) Docker image — uses buildx, multi-arch on push:

```
sbt 'project kinesis-mock-rootJS' buildDockerImage
```

Build the npm `kinesis-local` package:

```
sbt 'project kinesis-mock-rootJS' Compile/npmPackage
```

Functional/integration tests (Docker-based, see Gotchas):

```
sbt 'project kinesis-mock-rootJS' dockerComposeUp && sbt itTest && sbt 'project kinesis-mock-rootJS' dockerComposeDown
```

## Architecture / source layout

Single crossProject (`CrossType.Pure`) rooted at `kinesis-mock/`. Note the non-default source dirs (configured in `build.sbt`):

- `kinesis-mock/src/main/scala/kinesis/mock/` — shared business logic for both platforms. Key dirs: `api/`, `cache/`, `models/`, `validations/`, `instances/`, `syntax/`, `eventstream/`, `retry/`. Entry point `KinesisMockService.scala`; routing `KinesisMockRoutes.scala`; config `KinesisMockServiceConfig.scala`.
- `kinesis-mock/src/main/scalajvm/` — JVM-only impls (`AES.scala`, `TLS.scala`).
- `kinesis-mock/src/main/scalajs/` — JS-only impls (`AES.scala`, `TLS.scala`, same API).
- `kinesis-mock/src/main/resources/` — `server.json` (JS self-signed cert), `server.jks` (JVM keystore), `logback.xml`.
- `kinesis-mock/src/test/{scala,scalajvm}/` — shared tests + JVM-only tests.

Build plumbing in `project/`: `KinesisMockPlugin.scala` (command aliases, CI workflow gen, Scala version), `DockerImagePlugin.scala` (`buildDockerImage`/`pushDockerImage` via buildx), `DockerComposePlugin.scala` (`dockerComposeUp/Down/Logs/Ps`), `ScalacSettings.scala`. Docker bits in `docker/`: `Dockerfile` (node:25-alpine, runs `main.js`), `Dockerfile.JVM` (zulu-jdk21, runs the JAR), `docker-compose.yml` (mock + localstack + dynamodb-local + awscli for functional tests).

## Runtime config (env vars)

Configured via ciris env vars. Common: `INITIALIZE_STREAMS` (`name:shards:region` comma list, default 4 shards), `KINESIS_MOCK_TLS_PORT` (4567), `KINESIS_MOCK_PLAIN_PORT` (4568), `LOG_LEVEL`/`ROOT_LOG_LEVEL`, `SHOULD_PERSIST_DATA`+`PERSIST_PATH`, `AWS_ACCOUNT_ID`/`AWS_REGION`. CI also toggles `CBOR_ENABLED` and `SERVICE_PORT`. Full table in `README.md`.

## Gotchas

- **Cross build**: always specify `project kinesis-mock-rootJVM` or `...rootJS`; bare `test` is ambiguous. The Docker image and npm package are built from the **JS** linker output, not the JVM JAR.
- `Test / parallelExecution := false` on the JVM — JVM tests run serially by design; don't "fix" this.
- **Integration tests** (`itTest`, MUnit tag `integration`) require `dockerComposeUp` running first (localstack needs `LOCALSTACK_AUTH_TOKEN`); they run on the JVM project against the JS Docker image.
- The fat JAR's assembly output path and the JS linker output path are both hard-pinned to `docker/image/lib/` — that dir is the Docker build context input.
- `.github/workflows/ci.yml` is **generated** by sbt-typelevel (`sbt githubWorkflowGenerate`). Do not hand-edit it; change the build and regenerate.
- Scalafmt reformat commits are listed in `.git-blame-ignore-revs` — keep that updated when bumping scalafmt.

## Code style

Functional Scala 3 on the Typelevel ecosystem. scalafmt (`.scalafmt.conf`, scala3 dialect, new-syntax rewrites, custom import groups) and scalafix (`.scalafix.conf` `DisableSyntax`: no `var`, `null`, `throw`, `return`, `while`, `asInstanceOf`, default args, etc.) are enforced in CI. Compilation is `-Xfatal-warnings`, so warnings break the build. Run `sbt pretty` before committing.
