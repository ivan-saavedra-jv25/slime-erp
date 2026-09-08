import { registerLocaleData } from '@angular/common';
import localeEsCl from '@angular/common/locales/es-CL';
import { LOCALE_ID, Provider } from '@angular/core';

registerLocaleData(localeEsCl, 'es-CL');

/** Locale único de la app: montos como `$1.234.567`. */
export const localeProviders: Provider[] = [{ provide: LOCALE_ID, useValue: 'es-CL' }];
