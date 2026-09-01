export type Rol = 'SUPER_ADMIN' | 'ADMIN' | 'VENDEDOR' | 'COMPRADOR' | 'VISUALIZADOR';

export type Permiso =
  | 'CLIENTES_VER'
  | 'CLIENTES_EDITAR'
  | 'PRODUCTOS_VER'
  | 'PRODUCTOS_EDITAR'
  | 'USUARIOS_VER'
  | 'USUARIOS_EDITAR'
  | 'EMPRESAS_ADMINISTRAR';

export interface LoginResponse {
  token: string;
  usuarioId: number;
  tenantId: number;
  tenantNombre: string;
  nombre: string;
  email: string;
  rut: string;
  rol: Rol;
  permisos: Permiso[];
}

export interface Usuario {
  id: number;
  nombre: string;
  rut: string;
  email: string;
  rol: Rol;
  activo: boolean;
  fechaCreacion: string;
}

export interface Cliente {
  id: number;
  nombre: string;
  rut: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  activo: boolean;
}

export interface Producto {
  id: number;
  sku: string | null;
  nombre: string;
  descripcion: string | null;
  precioVenta: number;
  precioCompra: number;
  stock: number;
  controlaStock: boolean;
  activo: boolean;
}

export interface Empresa {
  id: number;
  nombre: string;
  rut: string;
  plan: string;
  activo: boolean;
  fechaAlta: string;
}

export interface CrearEmpresaRequest {
  nombre: string;
  rut: string;
  plan?: string;
  adminNombre: string;
  adminRut: string;
  adminEmail: string;
  adminPassword: string;
}
