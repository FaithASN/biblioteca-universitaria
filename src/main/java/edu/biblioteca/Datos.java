package edu.biblioteca;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

final class Datos {
    private Datos() {}

    record Registro(@NotBlank @Size(max = 120) String nombre,
                    @NotBlank @Email @Size(max = 254) String email,
                    @NotBlank @Size(min = 12, max = 72) String clave) {}

    record Login(@NotBlank @Email @Size(max = 254) String email,
                 @NotBlank @Size(max = 72) String clave) {}

    record AltaLibro(@NotBlank @Pattern(regexp = "[0-9]{13}") String isbn,
                     @NotBlank @Size(max = 200) String titulo,
                     @NotBlank @Size(max = 150) String autor,
                     @NotNull @Positive Integer ejemplares) {}

    record AltaPrestamo(@NotNull @Positive Long usuarioId,
                        @NotNull @Positive Long libroId,
                        @NotNull @Min(1) @Max(30) Integer dias) {}

    record Token(String accessToken, String tokenType, long expiresIn) {}
    record Cuenta(long id, String nombre, String email, List<String> roles) {}
    record Ficha(long id, String isbn, String titulo, String autor, int ejemplares, int disponibles) {}
    record Movimiento(long id, long usuarioId, long libroId, Instant prestadoEn,
                      Instant venceEn, Instant devueltoEn, String estado) {}

    record Pagina<T>(List<T> contenido, int pagina, int tamano, long total, int paginas) {
        static <T> Pagina<T> de(Page<T> page) {
            return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
