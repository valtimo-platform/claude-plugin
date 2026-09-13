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
import {PluginConfigurationComponent, PluginConfigurationData} from "@valtimo/plugin";
import {SelectItem} from "@valtimo/components";
import {BehaviorSubject, combineLatest, filter, map, Observable, Subscription, switchMap, take} from "rxjs";
import {CLAUDE_EFFORTS, CLAUDE_MODELS, ClaudePluginConfig} from "../../models";

@Component({
  standalone: false,
  selector: "valtimo-claude-plugin-configuration",
  templateUrl: "./claude-plugin-configuration.component.html",
})
export class ClaudePluginConfigurationComponent implements PluginConfigurationComponent, OnInit, OnDestroy {
  @Input() save$!: Observable<void>;
  @Input() disabled$!: Observable<boolean>;
  @Input() pluginId!: string;
  @Input() prefillConfiguration$!: Observable<ClaudePluginConfig>;
  @Output() valid: EventEmitter<boolean> = new EventEmitter<boolean>();
  @Output() configuration: EventEmitter<PluginConfigurationData> = new EventEmitter<PluginConfigurationData>();

  readonly modelItems: SelectItem[] = [
    {id: CLAUDE_MODELS.OPUS_5, text: "Claude Opus 5"},
    {id: CLAUDE_MODELS.SONNET_5, text: "Claude Sonnet 5"},
    {id: CLAUDE_MODELS.HAIKU_4_5, text: "Claude Haiku 4.5"},
  ];

  readonly effortItems: SelectItem[] = CLAUDE_EFFORTS.map(effort => ({id: effort, text: effort}));

  private saveSubscription!: Subscription;
  private readonly formValue$ = new BehaviorSubject<ClaudePluginConfig | null>(null);
  private readonly valid$ = new BehaviorSubject<boolean>(false);

  ngOnInit(): void {
    this.openSaveSubscription();
  }

  ngOnDestroy(): void {
    this.saveSubscription?.unsubscribe();
  }

  formValueChange(formValue: ClaudePluginConfig): void {
    this.formValue$.next(formValue);
    this.handleValid(formValue);
  }

  /**
   * The API key is deliberately not part of the check. Like every secret it is not
   * returned when an existing configuration is edited, so requiring it here would make
   * such a configuration impossible to save again — the backend requires it instead.
   */
  private handleValid(formValue: ClaudePluginConfig): void {
    const valid = !!formValue.configurationTitle;
    this.valid$.next(valid);
    this.valid.emit(valid);
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
