import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';
import { AdminAuthService } from '../../core/services/admin-auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  email = 'admin@demo.cl';
  password = 'admin123';
  error = '';
  cargando = false;
  modoAdmin = false;

  constructor(
    private auth: AuthService,
    private adminAuth: AdminAuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    this.modoAdmin = this.route.snapshot.queryParamMap.get('admin') === '1';
    if (this.modoAdmin) {
      this.email = 'super@slimerp.cl';
      this.password = 'Super123!';
    }
  }

  toggleModo(): void {
    this.modoAdmin = !this.modoAdmin;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: this.modoAdmin ? { admin: '1' } : {},
      queryParamsHandling: 'merge',
    });
    this.email = this.modoAdmin ? 'super@slimerp.cl' : 'admin@demo.cl';
    this.password = this.modoAdmin ? 'Super123!' : 'admin123';
    this.error = '';
  }

  ingresar(): void {
    this.error = '';
    this.cargando = true;

    if (this.modoAdmin) {
      this.adminAuth.login(this.email, this.password).subscribe({
        next: () => {
          this.cargando = false;
          this.router.navigate(['/admin']);
        },
        error: () => {
          this.cargando = false;
          this.error = 'Credenciales administrativas incorrectas.';
        },
      });
      return;
    }

    this.auth.login(this.email, this.password).subscribe({
      next: () => {
        this.cargando = false;
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.cargando = false;
        this.error = 'Email o contraseña incorrectos.';
      },
    });
  }
}