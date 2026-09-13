/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {PluginSpecification} from "@valtimo/plugin";
import {ClaudePluginConfigurationComponent} from "./components/claude-plugin-configuration/claude-plugin-configuration.component";
import {AskClaudeConfigurationComponent} from "./components/ask-claude-configuration/ask-claude-configuration.component";
import {CLAUDE_PLUGIN_LOGO_BASE64} from "./assets";

// `pluginId` must equal ClaudePlugin.PLUGIN_KEY on the backend, and the keys of
// functionConfigurationComponents must equal its @PluginAction keys.
const claudePluginSpecification: PluginSpecification = {
  pluginId: "claude",
  pluginConfigurationComponent: ClaudePluginConfigurationComponent,
  pluginLogoBase64: CLAUDE_PLUGIN_LOGO_BASE64,
  functionConfigurationComponents: {
    "ask-claude": AskClaudeConfigurationComponent,
  },
  pluginTranslations: {
    nl: {
      title: "Claude",
      description: "Stel Claude een vraag vanuit een proces en gebruik het antwoord in de zaak.",
      "ask-claude": "Claude vragen",
      configurationTitle: "Configuratienaam",
      configurationTitleTooltip:
        "De naam van de huidige plugin-configuratie. Onder deze naam kan de configuratie in de rest van de " +
        "applicatie teruggevonden worden.",
      apiKey: "API-sleutel",
      apiKeyTooltip:
        "De Anthropic API-sleutel waarmee deze configuratie vragen stelt. Bij het bewerken van een bestaande " +
        "configuratie blijft dit veld leeg; laat het leeg om de opgeslagen sleutel te behouden.",
      baseUrl: "API-URL",
      baseUrlTooltip:
        "Optioneel: een afwijkende API-URL, bijvoorbeeld die van een gateway of proxy. Laat leeg voor de " +
        "Anthropic API zelf.",
      model: "Model",
      modelTooltip: "Het Claude-model dat de vraag beantwoordt. Laat leeg voor Claude Opus 5.",
      effort: "Inspanning",
      effortTooltip:
        "Hoeveel Claude aan een vraag mag besteden. Hoger betekent een beter antwoord, maar ook trager en " +
        "duurder. Laat leeg voor de standaard ('high').",
      thinking: "Nadenken (adaptive thinking)",
      thinkingTooltip:
        "Laat Claude zelf bepalen hoe lang het over een vraag nadenkt. Aanbevolen. Uitzetten kan alleen bij " +
        "inspanning 'low', 'medium' of 'high'.",
      maxTokens: "Maximumlengte antwoord (tokens)",
      maxTokensTooltip:
        "Het maximum aantal tokens in het antwoord. Wordt het antwoord hierdoor afgekapt, dan is " +
        "claudeStopReason 'max_tokens'. Laat leeg voor 16000.",
      timeoutSeconds: "Time-out (seconden)",
      timeoutSecondsTooltip:
        "Hoelang op een antwoord gewacht wordt. Een moeilijke vraag met hoge inspanning duurt lang. " +
        "Laat leeg voor 600 seconden.",
      systemPrompt: "Systeemprompt",
      systemPromptTooltip:
        "Optioneel: instructies die voor elke actie op deze configuratie gelden, bijvoorbeeld de rol of de " +
        "toon. Een actie kan dit overschrijven.",
      actionDescription:
        "Stelt Claude de onderstaande vraag en slaat het antwoord op als procesvariabele. Optioneel worden " +
        "velden uit een JSON-antwoord naar procesvariabelen of het zaakdossier geschreven.",
      prompt: "Vraag",
      promptTooltip:
        "De vraag aan Claude. Gebruik {{pv:variabele}} of {{doc:/pad}} om zaakgegevens in de tekst te " +
        "verwerken, bijv. 'Beoordeel {{doc:/vraag}} op spoed'.",
      actionSystemPromptTooltip:
        "Optioneel: overschrijft de systeemprompt van de plugin-configuratie voor deze ene actie. " +
        "Ondersteunt dezelfde {{pv:...}}- en {{doc:...}}-placeholders.",
      documentResourceId: "Document",
      documentResourceIdTooltip:
        "Optioneel: het Valtimo resource-id van een bestand dat met de vraag wordt meegestuurd, meestal " +
        "pv:resourceId. PDF, afbeelding of tekst; maximaal 10 MB (instelbaar).",
      resultVariable: "Procesvariabele voor het antwoord",
      resultVariableTooltip: "De naam waaronder het antwoord wordt opgeslagen. Laat leeg voor claudeAnswer.",
      resultMappings: "Antwoord verwerken",
      resultMappingsTooltip:
        "Optioneel: haal velden uit een JSON-antwoord en zet ze in een procesvariabele of het zaakdossier. " +
        "Werkt alleen als Claude JSON antwoordt — vraag daar in de prompt expliciet om.",
      resultMappingSource: "Veld in antwoord (bijv. /nettoBedrag)",
      resultMappingTarget: "Doel (pv:naam of doc:/pad)",
      resultMappingAddRow: "Regel toevoegen",
    },
    en: {
      title: "Claude",
      description: "Ask Claude a question from a process and use the answer in the case.",
      "ask-claude": "Ask Claude",
      configurationTitle: "Configuration name",
      configurationTitleTooltip:
        "The name of the current plugin configuration. Under this name, the configuration can be found in " +
        "the rest of the application.",
      apiKey: "API key",
      apiKeyTooltip:
        "The Anthropic API key this configuration asks with. When editing an existing configuration this " +
        "field stays empty; leave it empty to keep the stored key.",
      baseUrl: "API URL",
      baseUrlTooltip:
        "Optional: a different API URL, for instance that of a gateway or proxy. Leave empty for the " +
        "Anthropic API itself.",
      model: "Model",
      modelTooltip: "The Claude model that answers the question. Leave empty for Claude Opus 5.",
      effort: "Effort",
      effortTooltip:
        "How much Claude may spend on a question. Higher means a better answer, but also slower and more " +
        "expensive. Leave empty for the default ('high').",
      thinking: "Thinking (adaptive thinking)",
      thinkingTooltip:
        "Let Claude decide how long to think about a question. Recommended. Switching it off is only " +
        "accepted at effort 'low', 'medium' or 'high'.",
      maxTokens: "Maximum answer length (tokens)",
      maxTokensTooltip:
        "The maximum number of tokens in the answer. If the answer is cut off by this, claudeStopReason is " +
        "'max_tokens'. Leave empty for 16000.",
      timeoutSeconds: "Timeout (seconds)",
      timeoutSecondsTooltip:
        "How long to wait for an answer. A hard question at high effort takes a while. Leave empty for 600 " +
        "seconds.",
      systemPrompt: "System prompt",
      systemPromptTooltip:
        "Optional: instructions that apply to every action on this configuration, such as the role or the " +
        "tone. An action can override this.",
      actionDescription:
        "Asks Claude the question below and stores the answer as a process variable. Optionally writes " +
        "fields from a JSON answer to process variables or the case document.",
      prompt: "Question",
      promptTooltip:
        "The question for Claude. Use {{pv:variable}} or {{doc:/path}} to weave case data into the text, " +
        "e.g. 'Assess {{doc:/question}} for urgency'.",
      actionSystemPromptTooltip:
        "Optional: overrides the plugin configuration's system prompt for this one action. Supports the " +
        "same {{pv:...}} and {{doc:...}} placeholders.",
      documentResourceId: "Document",
      documentResourceIdTooltip:
        "Optional: the Valtimo resource id of a file to send along with the question, usually " +
        "pv:resourceId. PDF, image or text; maximum 10 MB (configurable).",
      resultVariable: "Process variable for the answer",
      resultVariableTooltip: "The name the answer is stored under. Leave empty for claudeAnswer.",
      resultMappings: "Process the answer",
      resultMappingsTooltip:
        "Optional: take fields out of a JSON answer and put them in a process variable or the case " +
        "document. Only works when Claude answers in JSON — ask for that explicitly in the prompt.",
      resultMappingSource: "Field in answer (e.g. /netAmount)",
      resultMappingTarget: "Target (pv:name or doc:/path)",
      resultMappingAddRow: "Add row",
    },
  },
};

export {claudePluginSpecification};
