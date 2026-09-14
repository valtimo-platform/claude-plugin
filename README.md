# Claude plugin

A Valtimo plugin that asks Claude a question from a BPMN process and puts the answer back into the case.

An `Ask Claude` service task sends a prompt — with case data woven into it — to the Anthropic Messages API and
stores the answer as a process variable. Optionally it sends a document along, and writes fields out of a JSON
answer straight into the case document.

```
┌──────────────┐   {{doc:/question}}   ┌────────────┐          ┌────────┐
│  BPMN case   │ ────────────────────▶ │ Ask Claude │ ───────▶ │ Claude │
│              │ ◀──────────────────── │            │ ◀─────── │        │
└──────────────┘   claudeAnswer,       └────────────┘          └────────┘
                   doc:/zaak/urgentie
```

## Getting started

1. Add the plugin to a Valtimo application — see [Plugin documentation](documentation/plugin.md).
2. Create a configuration under **Admin → Plugins** with an Anthropic API key.
3. Link a service task to the **Ask Claude** action.

To try it without writing a process, run the [demo application](documentation/demo-application.md). It ships a
fixture case with one process per thing the action can do — plain question, prompt placeholders, result
mapping, document attachment — each startable on its own.

## Documentation

- [Handleiding](documentation/handleiding.md) — Dutch, for administrators and process designers: setting it up in the admin UI, no technical background needed
- [Plugin documentation](documentation/plugin.md) — configuration, the action, prompt placeholders, result mapping
- [Demo application](documentation/demo-application.md) — the fixture case, and running it locally
- [Getting started](documentation/getting-started.md) — developing on this repository
- [Release notes](documentation/release-notes.md) — version history and changes

## Contact

-- contact person name (company name) --
