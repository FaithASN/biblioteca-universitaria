package edu.biblioteca;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface Roles extends JpaRepository<Rol, String> {}

interface Usuarios extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
}

interface Libros extends JpaRepository<Libro, Long> {
    Page<Libro> findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(
            String titulo, String autor, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Libro l where l.id = :id")
    Optional<Libro> bloquear(@Param("id") long id);
}

interface Prestamos extends JpaRepository<Prestamo, Long> {
    Page<Prestamo> findByUsuarioId(long usuarioId, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prestamo p where p.id = :id")
    Optional<Prestamo> bloquear(@Param("id") long id);
}
