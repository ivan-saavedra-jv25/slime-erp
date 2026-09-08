import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { sesionAbiertaGuard, sinSesionGuard } from './features/caja/core/guards/caja.guards';

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
        path: 'tesoreria/cuentas',
        loadComponent: () =>
          import('./features/tesoreria/tesoreria-cuentas.component').then((m) => m.TesoreriaCuentasComponent),
      },
      {
        path: 'tesoreria/cuentas/:id',
        loadComponent: () =>
          import('./features/tesoreria/tesoreria-cuenta-detalle.component').then(
            (m) => m.TesoreriaCuentaDetalleComponent
          ),
      },
      {
        path: 'tesoreria/clientes/:clienteId',
        loadComponent: () =>
          import('./features/tesoreria/tesoreria-cliente-detalle.component').then(
            (m) => m.TesoreriaClienteDetalleComponent
          ),
      },
      {
        path: 'tesoreria/historial',
        loadComponent: () =>
          import('./features/tesoreria/tesoreria-historial.component').then((m) => m.TesoreriaHistorialComponent),
      },
      {
        path: 'flujo-caja',
        loadComponent: () => import('./features/flujo-caja/shell/shell').then((m) => m.Shell),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'resumen' },
          {
            path: 'resumen',
            loadComponent: () =>
              import('./features/flujo-caja/dashboard/dashboard').then((m) => m.Dashboard),
          },
          {
            path: 'mes',
            loadComponent: () =>
              import('./features/flujo-caja/month/month-detail').then((m) => m.MonthDetail),
          },
          {
            path: 'auditoria',
            loadComponent: () =>
              import('./features/flujo-caja/audit/audit-page').then((m) => m.AuditPage),
          },
          {
            path: 'ajustes',
            loadComponent: () =>
              import('./features/flujo-caja/settings/settings').then((m) => m.SettingsPage),
          },
        ],
      },
      {
        path: 'caja',
        loadComponent: () => import('./features/caja/shell/shell').then((m) => m.Shell),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'resumen' },
          {
            path: 'resumen',
            loadComponent: () => import('./features/caja/resumen/resumen').then((m) => m.ResumenComponent),
          },
          {
            path: 'apertura',
            canActivate: [sinSesionGuard],
            loadComponent: () => import('./features/caja/apertura/apertura').then((m) => m.AperturaComponent),
          },
          {
            path: 'movimientos',
            canActivate: [sesionAbiertaGuard],
            loadComponent: () =>
              import('./features/caja/movimientos/movimientos').then((m) => m.MovimientosComponent),
          },
          {
            path: 'entradas',
            canActivate: [sesionAbiertaGuard],
            loadComponent: () => import('./features/caja/entradas/entradas').then((m) => m.EntradasComponent),
          },
          {
            path: 'salidas',
            canActivate: [sesionAbiertaGuard],
            loadComponent: () => import('./features/caja/salidas/salidas').then((m) => m.SalidasComponent),
          },
          {
            path: 'arqueo',
            canActivate: [sesionAbiertaGuard],
            loadComponent: () => import('./features/caja/arqueo/arqueo').then((m) => m.ArqueoComponent),
          },
          {
            path: 'cierre',
            canActivate: [sesionAbiertaGuard],
            loadComponent: () => import('./features/caja/cierre/cierre').then((m) => m.CierreComponent),
          },
          {
            path: 'historial',
            loadComponent: () => import('./features/caja/historial/historial').then((m) => m.HistorialComponent),
          },
        ],
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
        path: 'usuarios/permisos',
        loadComponent: () =>
          import('./features/usuarios/roles-permisos.component').then((m) => m.RolesPermisosComponent),
      },
      {
        path: 'admin/empresas',
        loadComponent: () => import('./features/empresas/empresas.component').then((m) => m.EmpresasComponent),
      },
      {
        path: 'reportes/libro-ventas',
        loadComponent: () =>
          import('./features/reportes/libro-ventas.component').then((m) => m.LibroVentasComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
