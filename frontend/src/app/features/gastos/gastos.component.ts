import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { CategoriasGastoComponent } from './categorias-gasto.component';
import { GastosListaComponent } from './gastos-lista.component';
import { GastosRecurrentesComponent } from './gastos-recurrentes.component';

type TabGastos = 'categorias' | 'gastos' | 'recurrentes';

const ORDEN_TABS: TabGastos[] = ['categorias', 'gastos', 'recurrentes'];

@Component({
  selector: 'app-gastos',
  standalone: true,
  imports: [MatTabsModule, CategoriasGastoComponent, GastosListaComponent, GastosRecurrentesComponent],
  templateUrl: './gastos.component.html',
  styleUrl: './gastos.component.scss',
})
export class GastosComponent implements OnInit {
  tabActivo: TabGastos = 'gastos';

  constructor(
    private route: ActivatedRoute,
    private router: Router
  ) {}

  get indiceTab(): number {
    return ORDEN_TABS.indexOf(this.tabActivo);
  }

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const tab = params['tab'] as TabGastos | undefined;
      if (tab && ORDEN_TABS.includes(tab)) {
        this.tabActivo = tab;
      } else {
        this.tabActivo = 'gastos';
        if (tab) {
          this.router.navigate(['/gastos', 'gastos'], { replaceUrl: true });
        }
      }
    });
  }

  onTabChange(indice: number): void {
    const tab = ORDEN_TABS[indice];
    if (tab && tab !== this.tabActivo) {
      this.router.navigate(['/gastos', tab], { replaceUrl: true });
    }
  }
}