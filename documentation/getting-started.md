# Getting Started

Developing on this repository.

## Prerequisites

- Java 21
- Node 20
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/), for the test database

## Layout

| Path                           | What lives there                                                                 |
|--------------------------------|----------------------------------------------------------------------------------|
| `backend/plugin`               | The plugin itself: the plugin definition, the client, and the services behind it. |
| `backend/app`                  | A Valtimo application that autodeploys a configuration and the fixture case.       |
| `frontend/projects/plugin`     | The Angular library with the configuration screens.                               |
| `frontend/src`                 | A Valtimo frontend that loads that library.                                        |

## Build and test

```shell
./gradlew :backend:plugin:build          # compile and test (starts a postgres container)
./gradlew :backend:plugin:ktlintFormat   # fix formatting
cd frontend && npx ng build @valtimo-plugins/claude-plugin
```

The tests need Docker running: the integration test boots Spring against a postgres container that Gradle
starts and stops for you.

`ClaudeClientTest` runs against a stand-in Messages API on `MockWebServer` — no API key and no network calls,
so the whole suite is offline.

## Running it for real

See [demo-application.md](demo-application.md). Copy `.env.properties.example` to `.env.properties` and put an
Anthropic API key in it first; the autodeployed plugin configuration reads it.

Its fixture case has one process per thing the action can do — plain question, prompt placeholders, result
mapping, document attachment — which together are the manual counterpart to the test suite.

## Where things are

The boundary worth knowing is `ClaudeClient`: it is the only class that mentions the Anthropic SDK. Above it
everything speaks `ClaudeRequest` / `ClaudeAnswer`, which is what makes a multi-turn or tool-using variant an
addition rather than a rewrite.

For more on building Valtimo plugins, see the
[Custom Plugin Definition](https://docs.valtimo.nl/features/plugins/plugins/custom-plugin-definition) documentation.
