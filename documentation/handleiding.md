# Handleiding

Voor beheerders en procesontwerpers die Claude in een proces willen laten meedenken. Je hebt
hiervoor geen programmeerkennis nodig; alles in deze handleiding gebeurt in de beheerinterface
en in de procesmodelleur.

Zoek je de precieze naam of het type van een instelling, dan staan die in de
[pluginreferentie](plugin.md).

## Wat deze plugin doet

De plugin stelt Claude een vraag en zet het antwoord terug in de zaak.

Je schrijft de vraag zelf, in gewone taal, en je kunt er gegevens uit de lopende zaak in
verwerken: "Beoordeel deze klacht op spoed", met de tekst van de klacht erbij. Claude
antwoordt, en dat antwoord komt beschikbaar in het proces — als tekst om te tonen, of als
waarde waarop het proces verder beslist.

Een paar dingen om vooraf te weten:

- **De processtap wacht op het antwoord.** Er is geen wachtrij en geen terugkoppeling
  achteraf. Een moeilijke vraag kan tientallen seconden duren, soms langer.
- **Claude geeft niet elke keer exact hetzelfde antwoord.** Dat is geen fout; het hoort bij
  hoe een taalmodel werkt. Laat een proces nooit onomkeerbare stappen zetten op een antwoord
  dat niemand gezien heeft — zet er een controlestap van een behandelaar tussen.
- **Wat je meestuurt, gaat naar Anthropic.** Alles wat in de vraag staat, inclusief de
  zaakgegevens die je erin verwerkt en het document dat je meestuurt, verlaat je eigen
  omgeving. Denk na over welke gegevens dat zijn.
- **Elke vraag kost geld.** Hoe langer de vraag, het document en het antwoord, hoe hoger de
  kosten. Een proces dat duizend zaken per dag langs Claude stuurt, kost duizend keer zoveel
  als een proces dat er tien doet.

## Voordat je begint

| Wat je nodig hebt | Wie levert dat meestal |
| --- | --- |
| Een Anthropic API-sleutel | de beheerder van het Anthropic-account |
| Een proces met een servicetaak waar de vraag in past | de procesontwerper |
| Toestemming om de betreffende zaakgegevens naar Anthropic te sturen | de privacy-officer of functionaris gegevensbescherming |

## Stap 1 — De verbinding instellen

Ga in de beheerinterface naar **Plugins** en kies **Plugin configureren**. Kies bij
*Kies je plugin* de tegel **Claude**. Je krijgt dan een formulier met de onderstaande velden.
Bewaar met **Configuratie opslaan**.

Alleen de eerste twee velden zijn verplicht. Laat de rest leeg als je geen reden hebt ze te
veranderen; de standaardwaarden zijn voor vrijwel elk gebruik de juiste.

| Veld | Wat je invult |
| --- | --- |
| **Configuratienaam** | Een naam die jij herkent, bijvoorbeeld `Claude beoordelingen`. Deze naam kies je later bij het koppelen aan een processtap. |
| **API-sleutel** | De sleutel van het Anthropic-account. Deze wordt versleuteld opgeslagen. Bewerk je later deze configuratie, dan is het veld leeg: laat het leeg om de opgeslagen sleutel te behouden, of vul een nieuwe in om hem te vervangen. |
| **API-URL** | Laat leeg. Alleen invullen als het verkeer via een eigen gateway of proxy moet lopen. |
| **Model** | Welk model de vraag beantwoordt. Laat leeg voor Claude Opus 5. |
| **Inspanning** | Hoeveel Claude aan een vraag mag besteden. Laat leeg voor de standaard (`high`). |
| **Nadenken** | Laat aan staan. Claude bepaalt dan zelf hoe lang hij over een vraag nadenkt. |
| **Maximumlengte antwoord** | Hoe lang het antwoord maximaal mag zijn. Laat leeg voor de standaard. Zie [Wat er mis kan gaan](#wat-er-mis-kan-gaan). |
| **Time-out** | Hoelang op een antwoord gewacht wordt. Laat leeg voor tien minuten. |
| **Systeemprompt** | Optioneel: instructies die gelden voor élke vraag op deze configuratie, bijvoorbeeld de rol ("Je bent een medewerker bezwaarafhandeling") of de toon. Een losse processtap kan dit overschrijven. |

### Model en inspanning

Deze twee bepalen samen hoe goed, hoe snel en hoe duur een antwoord is.

| Model | Waarvoor |
| --- | --- |
| **Claude Opus 5** | De standaard. Het sterkste model; kies dit voor beoordelen, afwegen en samenvatten van lastige stukken. |
| **Claude Sonnet 5** | Sneller en goedkoper, en voor veel taken ruim voldoende. |
| **Claude Haiku 4.5** | Het snelste en goedkoopste. Voor eenvoudig werk: iets indelen in een categorie, een veld eruit halen. |

**Inspanning** loopt van `low` naar `max`. Hoger betekent een beter doordacht antwoord, maar
ook trager en duurder. De standaard `high` voldoet bijna altijd; verhoog alleen als de
antwoorden aantoonbaar tekortschieten, en verlaag als snelheid belangrijker is dan diepgang.

Wil je **Nadenken** uitzetten, dan kan dat alleen bij inspanning `low`, `medium` of `high`.
De combinatie "geen nadenken" met `xhigh` of `max` wordt geweigerd bij het opslaan.

Meerdere configuraties naast elkaar mag: bijvoorbeeld één met Haiku voor het routinewerk en
één met Opus voor de beoordelingen. Elke processtap kiest zelf welke hij gebruikt.

## Stap 2 — De vraag aan een processtap koppelen

Open het proces in de procesmodelleur en zet een **servicetaak** (service task) op de plek
waar de vraag gesteld moet worden. Klik de taak aan, kies **Proceskoppeling aanmaken**, kies
de configuratie uit stap 1 en de actie **Claude vragen**.

| Veld | Wat je invult |
| --- | --- |
| **Vraag** | De vraag aan Claude. Verplicht. Zie [Zaakgegevens in de vraag](#zaakgegevens-in-de-vraag-verwerken). |
| **Systeemprompt** | Optioneel: overschrijft voor deze ene stap de systeemprompt van de configuratie. |
| **Document** | Optioneel: een bestand dat met de vraag meegaat. Zie [Een document meesturen](#een-document-meesturen). |
| **Procesvariabele voor het antwoord** | De naam waaronder het antwoord wordt opgeslagen. Laat leeg voor `claudeAnswer`. |
| **Antwoord verwerken** | Optioneel: velden uit het antwoord rechtstreeks in de zaak zetten. Zie [Een antwoord automatisch verwerken](#een-antwoord-automatisch-verwerken). |

### Zaakgegevens in de vraag verwerken

Een vaste vraag is zelden genoeg — je wilt Claude iets vragen *over deze zaak*. Dat doe je
met twee soorten verwijzingen in de tekst van de vraag:

| Wat je typt | Wat er komt te staan |
| --- | --- |
| `{{doc:/pad}}` | Een gegeven uit het zaakdossier, bijvoorbeeld `{{doc:/klacht/omschrijving}}`. |
| `{{pv:naam}}` | Een procesvariabele, bijvoorbeeld `{{pv:klantnummer}}`. |

Bijvoorbeeld:

```
Beoordeel de volgende klacht op spoed: {{doc:/klacht/omschrijving}}
Het gaat om klantnummer {{pv:klantnummer}}.
```

De verwijzingen worden ingevuld voordat de vraag Valtimo verlaat. Klopt een verwijzing niet —
een verkeerd pad, of een procesvariabele die op dat moment niet bestaat — dan mislukt de
stap. Dat is met opzet: liever een zichtbare fout dan een vraag met een gat erin, want Claude
beantwoordt die gewoon en niemand ziet dat de helft ontbrak.

Accolades die géén verwijzing zijn blijven staan zoals je ze typt. Je kunt dus gerust een
voorbeeld van een JSON-antwoord in de vraag zetten.

### Het antwoord gebruiken

Het antwoord komt als procesvariabele beschikbaar — standaard `claudeAnswer`. Een
gebruikerstaak die daarna volgt kan hem tonen, een gateway kan erop beslissen, een script kan
hem in het zaakdossier zetten.

Daarnaast legt de plugin bij elke vraag vast welk model geantwoord heeft, hoe het antwoord is
geëindigd, en hoeveel er verbruikt is. Die gegevens heb je nodig om te controleren of een
antwoord bruikbaar is — zie hieronder. De technische namen ervan staan in de
[pluginreferentie](plugin.md).

### Een antwoord automatisch verwerken

Wil je niet de hele tekst maar één waarde — de urgentie, het bedrag, de categorie — dan kun
je die er automatisch uit laten halen. Daarvoor moet Claude in JSON antwoorden, en dat moet
je hem in de vraag expliciet opdragen:

```
Beoordeel {{doc:/klacht/omschrijving}} op spoed.
Antwoord alleen met JSON: {"spoed": "hoog|normaal|laag", "reden": "..."}
```

Bij **Antwoord verwerken** geef je vervolgens per regel op welk veld waarheen moet:

| Veld in antwoord | Doel | Wat er gebeurt |
| --- | --- | --- |
| `/spoed` | `pv:spoed` | Komt in de procesvariabele `spoed`, waar een gateway op kan beslissen. |
| `/spoed` | `doc:/zaak/urgentie` | Komt rechtstreeks in het zaakdossier te staan. |

Lukt het verwerken niet — Claude antwoordde toch in gewone tekst, of het veld ontbrak — dan
mislukt de stap **niet**. Het antwoord staat er gewoon, en de reden waarom het verwerken niet
lukte komt in een aparte procesvariabele te staan. Wil je daarop reageren, laat het proces er
dan expliciet op controleren.

### Een document meesturen

Bij **Document** kun je een bestand meesturen: een geüploade bijlage, een brief uit de
Documenten API. Je vult daar de procesvariabele in waarin het bestand terechtgekomen is,
meestal `pv:resourceId`. De veldvormen die de standaard uploadcomponenten opleveren worden
herkend, dus er hoeft geen tussenstap tussen de upload en deze taak.

- Toegestaan: PDF, afbeeldingen (JPEG, PNG, GIF, WebP) en tekstbestanden. Iets anders wordt
  geweigerd voordat de vraag verstuurd wordt.
- Maximaal 10 MB, tenzij de beheerder dat heeft aangepast.
- Eén bestand per stap. Zijn er meerdere geüpload, dan mislukt de stap — er wordt niet
  stilzwijgend de eerste gekozen. Wil je meerdere documenten laten beoordelen, gebruik dan
  meerdere stappen.

## Wat er mis kan gaan

| Wat je ziet | Wat er aan de hand is | Wat je doet |
| --- | --- | --- |
| De stap blijft hangen met een incident | De vraag kon niet gesteld worden: verkeerde API-sleutel, geen verbinding, of een verwijzing in de vraag die niet opgelost kon worden | Los de oorzaak op en herstart de taak; een incident is gewoon opnieuw uit te voeren |
| De stap duurt erg lang | Een moeilijke vraag met hoge inspanning | Normaal. Loopt de time-out af, kies dan een lichter model of een lagere inspanning |
| Het antwoord houdt midden in een zin op | Het antwoord raakte de maximumlengte | Verhoog **Maximumlengte antwoord**, of vraag om een korter antwoord |
| Claude gaat niet op de vraag in | Claude heeft de vraag geweigerd | Dit is geen fout: de stap is gewoon klaar. De reden staat in de procesvariabelen. Herformuleer de vraag |
| Het antwoord klopt inhoudelijk niet | Geen technisch probleem | Scherp de vraag aan, of zet er een hogere inspanning of een sterker model op |

Een weigering en een afgekapt antwoord zijn dus **geen** fouten: de stap loopt normaal door.
Gaat het proces iets doen met het antwoord dat niet mag misgaan, laat het dan eerst
controleren hoe het antwoord geëindigd is, of leg het voor aan een behandelaar.

## Waar je op moet letten

- **Zet een mens in de lus bij beslissingen die ertoe doen.** Laat Claude voorstellen,
  samenvatten en sorteren; laat een behandelaar beslissen.
- **Stuur niet meer gegevens mee dan de vraag nodig heeft.** Een verwijzing naar het hele
  zaakdossier is snel getypt, maar stuurt ook alles mee wat er verder in staat.
- **Vraag om een kort antwoord als je een kort antwoord wilt.** Dat scheelt tijd en geld.
- **Test met echte zaken voordat je live gaat.** Draai het proces op een paar tientallen
  bestaande gevallen en kijk of de antwoorden houdbaar zijn.

## Meer lezen

- [Pluginreferentie](plugin.md) — alle instellingen met hun technische naam, type en
  standaardwaarde
- [Demo-applicatie](demo-application.md) — een voorbeeldzaak met een proces per mogelijkheid
- [Release-notities](release-notes.md) — wat er per versie is veranderd
