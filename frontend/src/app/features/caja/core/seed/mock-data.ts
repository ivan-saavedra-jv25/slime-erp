import { Branch, User } from '../models';

/** Usuario mock: esta versión no implementa autenticación real (spec §1, §19). */
export const MOCK_USER: User = { id: 'usr_admin', nombre: 'Administrador' };

/** Sucursal mock (spec §19). */
export const MOCK_BRANCH: Branch = { id: 'suc_principal', nombre: 'Sucursal Principal' };

export const MOCK_USERS: readonly User[] = [MOCK_USER];
export const MOCK_BRANCHES: readonly Branch[] = [MOCK_BRANCH];

export function nombreUsuario(id: string): string {
  return MOCK_USERS.find((u) => u.id === id)?.nombre ?? 'Desconocido';
}

export function nombreSucursal(id: string): string {
  return MOCK_BRANCHES.find((b) => b.id === id)?.nombre ?? 'Sin sucursal';
}
