import { Component, computed, input } from '@angular/core';
import { ClpPipe } from '../shared/clp.pipe';

export interface ChartPoint {
  label: string;
  value: number;
}

interface Point {
  x: number;
  y: number;
  label: string;
  value: number;
}

const WIDTH = 900;
const HEIGHT = 260;
const PADDING = { top: 16, right: 16, bottom: 28, left: 16 };

@Component({
  selector: 'app-balance-chart',
  standalone: true,
  imports: [ClpPipe],
  template: `
    <svg
      [attr.viewBox]="'0 0 ' + width + ' ' + height"
      preserveAspectRatio="none"
      role="img"
      aria-label="Evolución del saldo final por mes"
      class="chart"
    >
      <!-- Línea del cero: separa saldos positivos de negativos. -->
      @if (showZeroLine()) {
        <line
          [attr.x1]="padding.left"
          [attr.x2]="width - padding.right"
          [attr.y1]="zeroY()"
          [attr.y2]="zeroY()"
          class="zero-line"
        />
      }

      <path [attr.d]="areaPath()" class="area" />
      <path [attr.d]="linePath()" class="line" />

      @for (p of plot(); track p.label) {
        <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3.5" class="dot" [class.negative]="p.value < 0">
          <title>{{ p.label }}: {{ p.value | clp }}</title>
        </circle>
      }
    </svg>

    <div class="x-axis">
      @for (p of points(); track p.label) {
        <span class="x-label">{{ p.label }}</span>
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }

    .chart {
      width: 100%;
      height: 260px;
      overflow: visible;
    }

    .line {
      fill: none;
      stroke: var(--primary-base);
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
    }

    .area {
      fill: color-mix(in srgb, var(--primary-base) 14%, transparent);
      stroke: none;
    }

    .zero-line {
      stroke: var(--border-strong);
      stroke-dasharray: 4 4;
      stroke-width: 1;
      vector-effect: non-scaling-stroke;
    }

    .dot {
      fill: var(--primary-base);
    }

    .dot.negative {
      fill: var(--error-base);
    }

    .x-axis {
      display: flex;
      justify-content: space-between;
      gap: var(--space-1);
      margin-top: var(--space-1);
    }

    .x-label {
      flex: 1;
      text-align: center;
      font: var(--font-legals);
      color: var(--text-muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `,
})
export class BalanceChart {
  /** Puntos en orden cronológico, con `label` y `value` ya calculados. */
  readonly points = input.required<ChartPoint[]>();

  readonly width = WIDTH;
  readonly height = HEIGHT;
  readonly padding = PADDING;

  private readonly scale = computed(() => {
    const values = this.points().map((p) => p.value);
    let min = Math.min(0, ...values);
    let max = Math.max(0, ...values);
    // Un flujo plano dejaría min === max y dividiría por cero.
    if (min === max) {
      min -= 1;
      max += 1;
    }
    const innerHeight = HEIGHT - PADDING.top - PADDING.bottom;
    const innerWidth = WIDTH - PADDING.left - PADDING.right;
    return { min, max, innerHeight, innerWidth };
  });

  readonly plot = computed<Point[]>(() => {
    const data = this.points();
    const { min, max, innerHeight, innerWidth } = this.scale();
    const step = data.length > 1 ? innerWidth / (data.length - 1) : 0;
    return data.map((p, i) => ({
      x: PADDING.left + step * i + (data.length > 1 ? 0 : innerWidth / 2),
      y: PADDING.top + innerHeight - ((p.value - min) / (max - min)) * innerHeight,
      label: p.label,
      value: p.value,
    }));
  });

  readonly zeroY = computed(() => {
    const { min, max, innerHeight } = this.scale();
    return PADDING.top + innerHeight - ((0 - min) / (max - min)) * innerHeight;
  });

  readonly showZeroLine = computed(() => this.scale().min < 0);

  readonly linePath = computed(() =>
    this.plot()
      .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)},${p.y.toFixed(2)}`)
      .join(' '),
  );

  readonly areaPath = computed(() => {
    const pts = this.plot();
    if (pts.length === 0) return '';
    const base = this.showZeroLine() ? this.zeroY() : HEIGHT - PADDING.bottom;
    const first = pts[0];
    const last = pts[pts.length - 1];
    return `${this.linePath()} L${last.x.toFixed(2)},${base.toFixed(2)} L${first.x.toFixed(2)},${base.toFixed(2)} Z`;
  });
}