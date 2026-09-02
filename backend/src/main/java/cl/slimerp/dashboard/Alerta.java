package cl.slimerp.dashboard;

public record Alerta(TipoAlerta tipo, SeveridadAlerta severidad, String mensaje, String ruta) {
}
