# Claude plugin

Asks Claude a question from a BPMN process and puts the answer back into the case.

## Overview

One plugin configuration holds the Anthropic credentials and the model settings. Each `Ask Claude` service task
holds its own prompt. The answer is written to a process variable; optionally, fields from a JSON answer are
written to process variables or straight into the case document.

The call is synchronous: the service task waits for the answer. There is no queue and no callback — a failed
call raises a BPMN incident, which is visible in the task list and retryable like any other.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:claude-plugin:0.1.1")
}
```

This pulls in `com.anthropic:anthropic-java` transitively. Every other dependency of the plugin is
`compileOnly` and comes from the Valtimo application itself.

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/claude-plugin": "0.1.1"
  }
}
```

In your `app.module.ts`:

```typescript
import {
    ClaudePluginModule, claudePluginSpecification,
} from '@valtimo-plugins/claude-plugin';

@NgModule({
    imports: [
        ClaudePluginModule,
    ],
    providers: [
        {
            provide: PLUGINS_TOKEN,
            useValue: [
                claudePluginSpecification,
            ]
        }
    ]
})
```

## Configuration

| Property         | Type    | Required | Default                 | Description                                                                                      |
|------------------|---------|----------|-------------------------|--------------------------------------------------------------------------------------------------|
| `apiKey`         | string  | Yes      | —                       | Anthropic API key. Stored encrypted; not returned when the configuration is edited.               |
| `baseUrl`        | string  | No       | `https://api.anthropic.com` | A different API host, for a gateway or proxy.                                                 |
| `model`          | string  | No       | `claude-opus-5`         | Any model id the API accepts; the dropdown lists the common ones.                                 |
| `effort`         | string  | No       | `high`                  | `low`, `medium`, `high`, `xhigh` or `max` — how much Claude may spend on a question.               |
| `thinking`       | boolean | No       | `true`                  | Adaptive thinking. Only switchable off at effort `high` or below.                                  |
| `maxTokens`      | number  | No       | `16000`                 | Maximum tokens in the answer. Hitting it sets `claudeStopReason` to `max_tokens`.                  |
| `timeoutSeconds` | number  | No       | `600`                   | How long to wait for an answer.                                                                    |
| `systemPrompt`   | string  | No       | —                       | Instructions applied to every action on this configuration, unless the action overrides them.      |

A configuration property may reference a Spring Environment property with `${...}`, which is how the demo
application reads its key from `ANTHROPIC_API_KEY`.

### Application properties

| Property                          | Default | Description                                                            |
|-----------------------------------|---------|------------------------------------------------------------------------|
| `valtimo.claude.max-document-size` | `10MB` | Largest file the `Ask Claude` action will attach to a question.          |

## Actions

### Ask Claude (`ask-claude`)

A service task (`bpmn:ServiceTask:start`).

| Parameter            | Type            | Required | Description                                                                            |
|----------------------|-----------------|----------|-----------------------------------------------------------------------------------------|
| `prompt`             | list of strings | Yes      | The question, as its lines. Supports `{{pv:variable}}` and `{{doc:/path}}` placeholders.  |
| `systemPrompt`       | list of strings | No       | Overrides the configuration's system prompt for this action. Same placeholders.           |
| `documentResourceId` | string          | No       | Resolver expression for a file to send along, usually `pv:resourceId`.                    |
| `resultVariable`     | string          | No       | Name the answer is stored under. Defaults to `claudeAnswer`.                              |
| `resultMappings`     | list            | No       | `source` (JSON pointer into the answer) → `target` (`pv:name` or `doc:/path`).             |

#### Why the prompt is a list of lines

The configurator shows one text box; what is *stored* is an array, joined with newlines
before the question is sent:

```json
"actionProperties": {
    "prompt": [
        "Context: you triage incoming messages.",
        "",
        "Assess {{doc:/question}} for urgency."
    ],
    "resultVariable": "claudeAnswer"
}
```

That is not cosmetic. Valtimo runs every **textual** action property through its value
resolvers before an action is invoked, and reads a leading `word:` as a resolver prefix — so
a prompt stored as the string `"Context: ..."` fails the step with `No resolver factory found
for value prefix Context`, and takes the other properties in the same batch with it. A JSON
array is not textual, so it is handed to the action untouched. It also spares a hand-written
process link a wall of `\n` escapes.

#### Prompt placeholders

`{{pv:...}}` and `{{doc:...}}` are filled with case data before the prompt leaves Valtimo:

```
Assess {{doc:/question}} for urgency. Customer number: {{pv:customerNumber}}
```

`{{...}}` rather than `${...}` deliberately: `${...}` already means "Spring Environment property" on plugin
*configuration* fields. Braces that are not a resolver expression — a JSON example in the prompt, say — are
left exactly as typed. A placeholder that cannot be resolved fails the step rather than sending a half-empty
prompt.

#### Attaching a document

`documentResourceId` points at a Valtimo temporary-resource id, which is what a form upload or the Documenten
API plugin's download action leaves in a process variable.

It accepts either a bare id or the *list of file references* the stock upload components produce —
`[{filename, sizeInBytes, id}]` from the Documenten API uploader, `[{data: {resourceId}}]` from the plain
Valtimo one — so `pv:resourceId` works straight off an upload with no script task in between. Uploading more
than one file is an error rather than a silent "first one wins".

Supported media types: PDF, JPEG, PNG, GIF, WebP, and anything text-like (the `text/` family plus JSON, XML
and YAML). Anything else is refused, by file name, before the request is sent.

#### Process variables written

| Variable                    | Description                                                                 |
|-----------------------------|-----------------------------------------------------------------------------|
| `claudeAnswer`              | The answer text (or whatever `resultVariable` names).                        |
| `claudeModel`               | The model that actually answered.                                            |
| `claudeStopReason`          | `end_turn`, `max_tokens`, `refusal`, …                                       |
| `claudeInputTokens`         | Tokens read.                                                                 |
| `claudeOutputTokens`        | Tokens written.                                                              |
| `claudeRefusalCategory`     | Only on a refusal.                                                           |
| `claudeRefusalExplanation`  | Only on a refusal.                                                           |
| `claudeMappingError`        | Only when the result mapping did not (fully) succeed.                        |

A refusal and a truncated answer both arrive as a normal `200` and are **not** errors: the step completes and
the process can branch on `claudeStopReason`. Both are logged as warnings.

#### Result mapping

Only works when Claude answers in JSON, so ask for that explicitly in the prompt:

```
Answer only with JSON: {"urgency": "high|normal|low", "reason": "..."}
```

| Source      | Target               | Effect                                      |
|-------------|----------------------|---------------------------------------------|
| `/urgency`  | `pv:urgency`         | Process variable `urgency`.                  |
| `/urgency`  | `doc:/zaak/urgentie` | Case document field `/zaak/urgentie`.        |

A `​```json` fence around the answer is stripped. Scalars keep their JSON type; objects and arrays are written
as JSON text. A mapping that fails never fails the step — the reason lands in `claudeMappingError`.

## Usage

1. Add a Claude plugin configuration under **Admin → Plugins** and paste an Anthropic API key.
2. Add a service task to a process and link it to the **Ask Claude** action.
3. Write the prompt, weaving in case data with `{{doc:/...}}` / `{{pv:...}}`.
4. Read the answer from `claudeAnswer` downstream, or map fields out of a JSON answer.

The demo application in `backend/app` ships a fixture case with a process for each of the above — see
[demo-application.md](demo-application.md).
