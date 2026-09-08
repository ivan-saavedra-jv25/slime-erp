import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AdminAuthService } from '../services/admin-auth.service';
import { AuthService } from '../services/auth.service';

export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const adminAuth = inject(AdminAuthService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err) => {
      if (err.status === 401 && adminAuth.estaAutenticado) {
        adminAuth.cerrarSesion().subscribe();
        router.navigate(['/login']);
      } else if (err.status === 401 && auth.estaAutenticado) {
        auth.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    })
  );
};
