-- Periodicidad de gastos recurrentes: DIARIO, SEMANAL, MENSUAL (comportamiento
-- anterior) o ANUAL. Los registros existentes se tratan como MENSUAL.
ALTER TABLE gasto_recurrente
    ADD COLUMN frecuencia VARCHAR(20) NOT NULL DEFAULT 'MENSUAL'
        CHECK (frecuencia IN ('DIARIO', 'SEMANAL', 'MENSUAL', 'ANUAL'));

-- dia_mes solo aplica a plantillas MENSUAL; las demás frecuencias no lo usan.
ALTER TABLE gasto_recurrente
    ALTER COLUMN dia_mes DROP NOT NULL;