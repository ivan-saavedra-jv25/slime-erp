import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/layout.component').then((m) => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'ventas',
        loadComponent: () => import('./features/ventas/ventas.component').then((m) => m.VentasComponent),
      },
      {
        path: 'ventas/historial',
        loadComponent: () =>
          import('./features/ventas/ventas-historial.component').then((m) => m.VentasHistorialComponent),
      },
      {
        path: 'compras',
        loadComponent: () => import('./features/compras/compras.component').then((m) => m.ComprasComponent),
      },
      {
        path: 'compras/historial',
        loadComponent: () =>
          import('./features/compras/compras-historial.component').then((m) => m.ComprasHistorialComponent),
      },
      {
        path: 'clientes',
        loadComponent: () => import('./features/clientes/clientes.component').then((m) => m.ClientesComponent),
      },
      {
        path: 'proveedores',
        loadComponent: () =>
          import('./features/proveedores/proveedores.component').then((m) => m.ProveedoresComponent),
      },
      {
        path: 'productos',
        loadComponent: () => import('./features/productos/productos.component').then((m) => m.ProductosComponent),
      },
      {
        path: 'bodegas',
        loadComponent: () => import('./features/bodegas/bodegas.component').then((m) => m.BodegasComponent),
      },
      {
        path: 'categorias',
        loadComponent: () => import('./features/categorias/categorias.component').then((m) => m.CategoriasComponent),
      },
      {
        path: 'formas-pago',
        loadComponent: () =>
          import('./features/formas-pago/formas-pago.component').then((m) => m.FormasPagoComponent),
      },
      {
        path: 'movimientos',
        loadComponent: () => import('./features/movimientos/movimientos.component').then((m) => m.MovimientosComponent),
      },
      {
        path: 'movimientos/historial',
        loadComponent: () =>
          import('./features/movimientos/movimientos-historial.component').then((m) => m.MovimientosHistorialComponent),
      },
      {
        path: 'usuarios',
        loadComponent: () => import('./features/usuarios/usuarios.component').then((m) => m.UsuariosComponent),
      },
      {
        path: 'admin/empresas',
        loadComponent: () => import('./features/empresas/empresas.component').then((m) => m.EmpresasComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
