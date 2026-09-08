import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AdminAuthService } from '../services/admin-auth.service';

const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN', 'SUPPORT', 'FINANCE', 'AUDITOR'];

/**
 * Protege la consola administrativa (/admin): permite los 5 roles admin y
 * redirige al login si no hay sesión administrativa activa.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);

  if (!auth.estaAutenticado) {
    router.navigate(['/login']);
    return false;
  }

  if (ADMIN_ROLES.includes(auth.adminRol ?? '')) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

/**
 * Protege la app de negocio: si existe una sesión administrativa activa, el
 * usuario es llevado a la consola /admin.
 */
export const negocioGuard: CanActivateFn = () => {
  const auth = inject(AdminAuthService);
  const router = inject(Router);

  if (auth.estaAutenticado) {
    router.navigate(['/admin']);
    return false;
  }

  return true;
};