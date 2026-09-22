import { Component, computed, effect, inject, input } from '@angular/core';
import { HighchartsChartModule } from 'highcharts-angular';
import * as Highcharts from 'highcharts';
import { ClpPipe } from '../shared/clp.pipe';

export interface ChartPoint {
  label: string;
  value: number;
}

function cssVar(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

@Component({
  selector: 'app-balance-chart',
  standalone: true,
  imports: [HighchartsChartModule],
  providers: [ClpPipe],
  template: `
    <highcharts-chart
      [Highcharts]="Highcharts"
      [options]="chartOptions()"
      [(update)]="update"
      style="display: block; width: 100%; height: 260px;"
    ></highcharts-chart>
  `,
})
export class BalanceChart {
  private readonly clp = inject(ClpPipe);

  /** Puntos en orden cronológico, con `label` y `value` ya calculados. */
  readonly points = input.required<ChartPoint[]>();

  readonly Highcharts: typeof Highcharts = Highcharts;
  update = false;

  readonly chartOptions = computed<Highcharts.Options>(() => {
    const points = this.points();
    const primary = cssVar('--primary-base', '#2563eb');
    const error = cssVar('--error-base', '#dc2626');
    const muted = cssVar('--text-muted', '#6b7280');
    const border = cssVar('--border-strong', '#9ca3af');
    const formatClp = (value: number) => this.clp.transform(value);

    return {
      chart: {
        type: 'areaspline',
        height: 260,
        backgroundColor: 'transparent',
        style: { fontFamily: 'inherit' },
      },
      title: { text: undefined },
      credits: { enabled: false },
      legend: { enabled: false },
      xAxis: {
        categories: points.map((p) => p.label),
        labels: { style: { color: muted } },
        lineColor: border,
      },
      yAxis: {
        title: { text: undefined },
        gridLineDashStyle: 'Dash',
        labels: {
          style: { color: muted },
          formatter: function (): string {
            return formatClp(Number(this.value));
          },
        },
        plotLines: [
          {
            value: 0,
            color: border,
            dashStyle: 'Dash',
            width: 1,
          },
        ],
      },
      tooltip: {
        formatter: function (): string {
          return `${this.key}: <b>${formatClp(Number(this.y))}</b>`;
        },
      },
      plotOptions: {
        areaspline: {
          color: primary,
          fillColor: `color-mix(in srgb, ${primary} 14%, transparent)`,
          lineWidth: 2,
          marker: { radius: 3.5 },
          zones: [
            // -0.5: separa saldos negativos de saldos en 0 (los montos CLP son enteros).
            { value: -0.5, color: error, fillColor: `color-mix(in srgb, ${error} 14%, transparent)` },
            { color: primary },
          ],
        },
      },
      series: [{ type: 'areaspline', name: 'Saldo', data: points.map((p) => p.value) }],
    };
  });

  constructor() {
    effect(() => {
      this.chartOptions();
      this.update = true;
    });
  }
}
