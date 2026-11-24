package edu.biblioteca;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static edu.biblioteca.Datos.*;

@RestController
@RequestMapping("/api")
class Api {
    private final Biblioteca biblioteca;

    Api(Biblioteca biblioteca) {
        this.biblioteca = biblioteca;
    }

    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una cuenta con rol USUARIO")
    Cuenta registro(@Valid @RequestBody Registro datos) {
        return biblioteca.registrar(datos);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Obtener un JWT con vigencia de una hora")
    Token login(@Valid @RequestBody Login datos) {
        return biblioteca.login(datos);
    }

    @GetMapping("/usuarios/yo")
    @Operation(summary = "Consultar el perfil de la cuenta autenticada", security = @SecurityRequirement(name = "jwt"))
    Cuenta perfil(Principal principal) {
        return biblioteca.perfil(principal.getName());
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Listar cuentas (ADMIN)", security = @SecurityRequirement(name = "jwt"))
    Pagina<Cuenta> usuarios(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return biblioteca.usuarios(orden(pagina, tamano));
    }

    @PostMapping("/libros")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un libro y sus ejemplares (ADMIN)", security = @SecurityRequirement(name = "jwt"))
    Ficha registrarLibro(@Valid @RequestBody AltaLibro datos) {
        return biblioteca.registrarLibro(datos);
    }

    @GetMapping("/libros")
    @Operation(summary = "Consultar el catálogo y buscar por título o autor", security = @SecurityRequirement(name = "jwt"))
    Pagina<Ficha> catalogo(@RequestParam(defaultValue = "") @Size(max = 150) String q,
                          @RequestParam(defaultValue = "0") @Min(0) int pagina,
                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return biblioteca.catalogo(q, orden(pagina, tamano));
    }

    @GetMapping("/libros/{id}")
    @Operation(summary = "Consultar una ficha y su disponibilidad", security = @SecurityRequirement(name = "jwt"))
    Ficha libro(@PathVariable @Positive long id) {
        return biblioteca.libro(id);
    }

    @PostMapping("/prestamos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un préstamo (ADMIN)", security = @SecurityRequirement(name = "jwt"))
    Movimiento prestar(@Valid @RequestBody AltaPrestamo datos) {
        return biblioteca.prestar(datos);
    }

    @PostMapping("/prestamos/{id}/devolucion")
    @Operation(summary = "Registrar la devolución de un préstamo (ADMIN)", security = @SecurityRequirement(name = "jwt"))
    Movimiento devolver(@PathVariable @Positive long id) {
        return biblioteca.devolver(id);
    }

    @GetMapping("/prestamos/mis")
    @Operation(summary = "Consultar el historial de la cuenta autenticada", security = @SecurityRequirement(name = "jwt"))
    Pagina<Movimiento> historial(Principal principal, @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return biblioteca.historial(principal.getName(), orden(pagina, tamano));
    }

    @GetMapping("/prestamos")
    @Operation(summary = "Listar todos los préstamos (ADMIN)", security = @SecurityRequirement(name = "jwt"))
    Pagina<Movimiento> prestamos(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return biblioteca.prestamos(orden(pagina, tamano));
    }

    private Pageable orden(int pagina, int tamano) {
        if ((long) pagina * tamano > Integer.MAX_VALUE) {
            throw new Fallo(400, "El desplazamiento de la página es demasiado grande");
        }
        return PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id"));
    }
}
