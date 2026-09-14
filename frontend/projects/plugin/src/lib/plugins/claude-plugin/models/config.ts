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

import {PluginConfigurationData} from "@valtimo/plugin";

/**
 * The models offered in the configuration dropdown. Not a closed set on the backend —
 * a configuration may name any model id the API accepts — but these are the ones worth
 * one click.
 */
const CLAUDE_MODELS = {
  OPUS_5: "claude-opus-5",
  SONNET_5: "claude-sonnet-5",
  HAIKU_4_5: "claude-haiku-4-5",
} as const;

/** `output_config.effort`; leaving it empty is the API's own default (`high`). */
const CLAUDE_EFFORTS = ["low", "medium", "high", "xhigh", "max"] as const;

type ClaudeEffort = (typeof CLAUDE_EFFORTS)[number];

interface ClaudePluginConfig extends PluginConfigurationData {
  apiKey: string;
  // All optional: every one of these has a default on the backend.
  baseUrl?: string;
  model?: string;
  maxTokens?: number;
  effort?: ClaudeEffort;
  thinking?: boolean;
  systemPrompt?: string;
  timeoutSeconds?: number;
}

/**
 * One line of the result mapping: `source` is a JSON pointer into Claude's answer,
 * `target` a value-resolver expression (`pv:urgency` or `doc:/zaak/urgentie`).
 */
interface ClaudeResultMapping {
  source: string;
  target: string;
}

interface AskClaudeConfig {
  /**
   * The prompt as its lines, joined with newlines by the backend.
   *
   * An array rather than a string, and not a matter of taste: Valtimo runs every *textual*
   * action property through its value resolvers before the action runs, and reads a leading
   * `word:` as a resolver prefix — so a prompt opening "Context: ..." would fail the step
   * with "No resolver factory found for value prefix Context". An array is passed through
   * untouched.
   */
  prompt: string[];
  systemPrompt?: string[];
  // Valtimo resource id of a file to send along, usually `pv:resourceId`.
  documentResourceId?: string;
  resultVariable?: string;
  resultMappings?: ClaudeResultMapping[];
}

export {AskClaudeConfig, CLAUDE_EFFORTS, CLAUDE_MODELS, ClaudeEffort, ClaudePluginConfig, ClaudeResultMapping};
