# Release notes

Overzicht van wijzigingen per versie van de Claude plugin.

## 0.1.0

**Let op:** de vraag en de systeemprompt van de actie worden nu opgeslagen als een lijst
regels in plaats van als één tekst. Bestaande proceskoppelingen uit 0.0.1 moeten opnieuw
opgeslagen worden — in de beheerinterface verandert er niets, daar blijft het één tekstvak.

- Een vraag die begint met een los woord en een dubbele punt ("Context: ...") liet de
  processtap stuklopen voordat de plugin aan de beurt was: Valtimo las `Context` als een
  onbekende value-resolver-prefix. Vraag en systeemprompt worden daarom als lijst regels
  opgeslagen, wat Valtimo ongemoeid doorgeeft.
- Een document dat met de standaard Valtimo-uploadcomponent geüpload was, werd stilzwijgend
  niet meegestuurd: het resource-id staat daar een niveau dieper, onder `data`. Dat wordt nu
  gelezen, en een bestandsverwijzing zonder herkenbaar id laat de stap falen in plaats van de
  vraag zonder document te versturen.
- De velden voor de vraag en de systeemprompt waren één regel hoog en maximaal 250 tekens.
  Het zijn nu tekstvakken zonder lengtebeperking.
- Het vinkje **Nadenken** stond bij een nieuwe configuratie uit terwijl nadenken wel aan was.

## 0.0.1

Eerste versie.

- Plugin `claude` met de actie **Claude vragen** (`ask-claude`) op een service task.
- Prompt en systeemprompt met `{{pv:...}}`- en `{{doc:...}}`-placeholders voor zaakgegevens.
- Optioneel een document (PDF, afbeelding of tekst) meesturen via een Valtimo resource-id.
- Antwoord, model, stopreden en tokenverbruik als procesvariabelen.
- Optionele resultaatmapping: velden uit een JSON-antwoord naar `pv:`- of `doc:`-doelen.
- Instelbaar model, inspanning, adaptive thinking, maximumlengte en time-out per configuratie.
