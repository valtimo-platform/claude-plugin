# Release notes

Overzicht van wijzigingen per versie van de Claude plugin.

## 0.1.2

- **De applicatie startte niet op als er al iets anders met dezelfde naam in de applicatie
  zat.** Eén onderdeel van de plugin had een te algemene naam en botste daarmee. Alle
  onderdelen heten nu naar de plugin.

## 0.1.1

- **Een vraag zonder document liep altijd stuk.** Een service task met de actie **Claude
  vragen** waar geen document bij zat — verreweg het gewone geval — faalde met
  `Cannot read a resource id from a ExecutionEntity`. Valtimo vult een actie-eigenschap
  waarvoor het geen waarde heeft namelijk met de procesuitvoering zelf zodra het type dat
  toelaat, en het type van het documentveld is `Any`, wat alles toelaat. De plugin herkent dat
  nu en leest het als "geen document", zoals bedoeld.
- **Het gepubliceerde artefact was voor Maven niet op te halen.** De Spring Boot Gradle-plugin
  zet de jar onder de classifier `plain`, terwijl de gepubliceerde POM naar de jar zonder
  classifier verwijst. Voor Gradle ging dat goed — die leest de module-metadata — maar een
  Maven-project dat `com.ritense.valtimoplugins:claude-plugin` opnam, kreeg een 404 op de jar.

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
