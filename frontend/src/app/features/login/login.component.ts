import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  email = 'admin@demo.cl';
  password = 'admin123';
  error = '';
  cargando = false;

  constructor(private auth: AuthService, private router: Router) {}

  ingresar(): void {
    this.error = '';
    this.cargando = true;
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
