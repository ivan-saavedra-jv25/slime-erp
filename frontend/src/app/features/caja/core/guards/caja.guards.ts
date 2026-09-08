import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CajaFacade } from '../services/caja-facade.service';

/**
 * Protege las vistas que exigen una sesión abierta (spec §16.2).
 * Sin caja abierta, redirige a Apertura.
 */
export const sesionAbiertaGuard: CanActivateFn = () => {
  const facade = inject(CajaFacade);
  const router = inject(Router);
  return facade.hayCajaAbierta() ? true : router.createUrlTree(['/caja/apertura']);
};

/** Evita entrar a Apertura si ya hay una caja abierta (spec §16.1). */
export const sinSesionGuard: CanActivateFn = () => {
  const facade = inject(CajaFacade);
  const router = inject(Router);
  return facade.hayCajaAbierta() ? router.createUrlTree(['/caja/resumen']) : true;
};
