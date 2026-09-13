# Demo application

`backend/app` is a Valtimo application that autodeploys a Claude plugin configuration and a **Claude** case
whose processes are fixtures: one per thing the `Ask Claude` action can do, each startable on its own from
**Cases → Claude → Create case**.

| Fixture process                 | What it exercises                                                                   |
|---------------------------------|-------------------------------------------------------------------------------------|
| `claude-fixture-ask`            | The plain path: a question in, an answer in `claudeAnswer`, plus response metadata.   |
| `claude-fixture-case-data`      | `{{doc:/...}}` and `{{pv:...}}` placeholders in the prompt and the system prompt.     |
| `claude-fixture-result-mapping` | A JSON answer routed into `doc:` and `pv:` targets, and `claudeMappingError`.         |
| `claude-fixture-document`       | A file uploaded on the start form and sent along with the question.                   |

Each ends in a user task that shows what came back, so a fixture is run by clicking through it — there is no
assertion step. The two `doc:` targets that `claude-fixture-result-mapping` writes also show up on the case
summary tab, which is where to check that a document write really landed.

The BPMN of the fixtures is generated, not hand-written — see `backend/app/tools/generate-fixture-bpmn.py`.
Edit the task list in that script and re-run it rather than editing the XML.

## Running it

All commands below should be run from the **project root** directory.

### Prerequisites

- Java 21
- Node 20
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/)
- An Anthropic API key

### Supply the API key

The autodeployed plugin configuration reads its key from the environment:

```shell
cp .env.properties.example .env.properties
# then put your key in ANTHROPIC_API_KEY
```

Without it the application still starts, but every `Ask Claude` service task fails with an authentication
error — which is itself a reasonable first thing to see, since it arrives as a BPMN incident.

### Start docker

Make sure docker is running.

```shell
./gradlew :backend:app:composeUp
```

### Start backend

```shell
./gradlew :backend:app:bootRun
```

### Start frontend

```shell
cd frontend
nvm use 20
npm install
npm run build
npm start
```

### Keycloak users

| Name         | Role           | Username  | Password  |
|--------------|----------------|-----------|-----------|
| James Vance  | ROLE_USER      | user      | user      |
| Asha Miller  | ROLE_ADMIN     | admin     | admin     |
| Morgan Finch | ROLE_DEVELOPER | developer | developer |

## Notes on the document fixture

Its start form uses the `documenten-api-file` upload component, which posts to core Valtimo's
`v1/resource/temp` and hands back the temporary resource id the action reads. No Documenten API plugin or Open
Zaak is needed — only `@valtimo/zgw`, which the demo frontend imports for that one component.

Upload a PDF, an image, or any text-like file. A `.docx` is the quickest way to watch the media-type check
refuse an attachment by name before anything is sent.

## Source code

1. [Frontend](/frontend)
2. [Backend](/backend)
