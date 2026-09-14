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

import {Component, EventEmitter, Input, OnDestroy, OnInit, Output} from "@angular/core";
import {FunctionConfigurationComponent, FunctionConfigurationData} from "@valtimo/plugin";
import {MultiInputKeyValue} from "@valtimo/components";
import {BehaviorSubject, combineLatest, filter, map, Observable, Subscription, switchMap, take} from "rxjs";
import {AskClaudeConfig} from "../../models";

/**
 * What `v-form` emits: the textareas contribute one block of text rather than lines, and
 * the multi-input key/value rows rather than source/target.
 */
type AskClaudeFormValue = Omit<AskClaudeConfig, "prompt" | "systemPrompt" | "resultMappings"> & {
  prompt?: string;
  systemPrompt?: string;
  resultMappings?: MultiInputKeyValue[];
};

const asText = (lines?: string[]): string => (lines ?? []).join("\n");
const asLines = (text?: string): string[] => (text ?? "").split("\n");

@Component({
  standalone: false,
  selector: "valtimo-ask-claude-configuration",
  templateUrl: "./ask-claude-configuration.component.html",
})
export class AskClaudeConfigurationComponent implements FunctionConfigurationComponent, OnInit, OnDestroy {
  @Input() save$!: Observable<void>;
  @Input() disabled$!: Observable<boolean>;
  @Input() pluginId!: string;
  @Input() prefillConfiguration$!: Observable<AskClaudeConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<FunctionConfigurationData> = new EventEmitter<FunctionConfigurationData>();

  /**
   * The saved configuration in the shape the form's own fields take, resolved once per
   * prefill. Deliberately not method calls in the template: that would hand the
   * multi-input a new array on every change-detection run, and each one restarts its
   * value stream.
   */
  readonly prefill$ = new BehaviorSubject<AskClaudeFormValue>({resultMappings: []});

  private saveSubscription!: Subscription;
  private prefillSubscription!: Subscription;
  private readonly formValue$ = new BehaviorSubject<AskClaudeConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.openSaveSubscription();
    this.openPrefillSubscription();
  }

  ngOnDestroy(): void {
    this.saveSubscription?.unsubscribe();
    this.prefillSubscription?.unsubscribe();
  }

  formValueChange(formValue: AskClaudeFormValue): void {
    this.formValue$.next(this.toConfig(formValue));
    this.handleValid(formValue);
  }

  private handleValid(formValue: AskClaudeFormValue): void {
    const valid = !!formValue.prompt?.trim();
    this.valid$.next(valid);
    this.valid.emit(valid);
  }

  private openPrefillSubscription(): void {
    this.prefillSubscription = this.prefillConfiguration$?.subscribe(prefill => {
      this.prefill$.next({
        prompt: asText(prefill?.prompt),
        systemPrompt: asText(prefill?.systemPrompt),
        documentResourceId: prefill?.documentResourceId,
        resultVariable: prefill?.resultVariable,
        resultMappings: (prefill?.resultMappings ?? []).map(mapping => ({
          key: mapping.source,
          value: mapping.target,
        })),
      });
    });
  }

  /**
   * The form speaks text and key/value; the plugin action speaks lines and source/target.
   * Mapping rows that are only half filled in are dropped rather than saved as broken
   * mappings, and an empty system prompt is left out entirely so the action falls back to
   * the one on the plugin configuration.
   */
  private toConfig(formValue: AskClaudeFormValue): AskClaudeConfig {
    const {prompt, systemPrompt, resultMappings, ...rest} = formValue;
    return {
      ...rest,
      prompt: asLines(prompt),
      ...(systemPrompt?.trim() ? {systemPrompt: asLines(systemPrompt)} : {}),
      resultMappings: (resultMappings ?? [])
        .filter(row => !!row.key && !!row.value)
        .map(row => ({source: row.key!, target: row.value!})),
    };
  }

  private openSaveSubscription(): void {
    this.saveSubscription = this.save$
      ?.pipe(
        switchMap(() => combineLatest([this.formValue$, this.valid$]).pipe(take(1))),
        filter(([_, valid]) => valid),
        map(([formValue]) => formValue)
      )
      .subscribe(formValue => {
        this.configuration.emit(formValue!);
      });
  }
}
