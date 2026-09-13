# Release notes

Overzicht van wijzigingen per versie van de Claude plugin.

## 0.0.1

Eerste versie.

- Plugin `claude` met de actie **Claude vragen** (`ask-claude`) op een service task.
- Prompt en systeemprompt met `{{pv:...}}`- en `{{doc:...}}`-placeholders voor zaakgegevens.
- Optioneel een document (PDF, afbeelding of tekst) meesturen via een Valtimo resource-id.
- Antwoord, model, stopreden en tokenverbruik als procesvariabelen.
- Optionele resultaatmapping: velden uit een JSON-antwoord naar `pv:`- of `doc:`-doelen.
- Instelbaar model, inspanning, adaptive thinking, maximumlengte en time-out per configuratie.
