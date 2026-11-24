package edu.biblioteca;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.hibernate.Hibernate;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static edu.biblioteca.Datos.*;

@Service
@Transactional(readOnly = true)
class Biblioteca {
    private final Usuarios usuarios;
    private final Roles roles;
    private final Libros libros;
    private final Prestamos prestamos;
    private final PasswordEncoder claves;
    private final JwtEncoder tokens;
    private final PersistenceUnitUtil identidad;
    private final String hashAlternativo;

    Biblioteca(Usuarios usuarios, Roles roles, Libros libros, Prestamos prestamos,
               PasswordEncoder claves, JwtEncoder tokens, EntityManagerFactory entidades) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.libros = libros;
        this.prestamos = prestamos;
        this.claves = claves;
        this.tokens = tokens;
        this.identidad = entidades.getPersistenceUnitUtil();
        this.hashAlternativo = claves.encode("comparacion-cuenta-inexistente");
    }

    @Transactional
    public Cuenta registrar(Registro datos) {
        return cuenta(crearUsuario(datos.nombre(), email(datos.email()), datos.clave(), "USUARIO"));
    }

    @Transactional
    public void inicializarAdmin(String email, String clave) {
        if (email.isBlank() && clave.isBlank()) {
            return;
        }
        if (email.isBlank() || email.length() > 254 || !email.matches("[^@\\s]+@[^@\\s]+")
                || clave.isBlank() || clave.length() < 12 || !Seguridad.longitudBcrypt(clave)) {
            throw new IllegalArgumentException("Revisa ADMIN_EMAIL y ADMIN_PASSWORD");
        }
        String correo = email(email);
        var existente = usuarios.findByEmail(correo);
        if (existente.isEmpty()) {
            crearUsuario("Administración", correo, clave, "ADMIN");
        } else if (!cuenta(existente.get()).roles().contains("ADMIN")) {
            throw new IllegalStateException("ADMIN_EMAIL pertenece a una cuenta de usuario existente");
        }
    }

    public Token login(Login datos) {
        if (!Seguridad.longitudBcrypt(datos.clave())) {
            throw new Fallo(401, "Credenciales inválidas");
        }
        var usuario = usuarios.findByEmail(email(datos.email()));
        boolean valida = claves.matches(datos.clave(), usuario.map(u -> u.claveHash).orElse(hashAlternativo));
        if (!valida || usuario.isEmpty()) {
            throw new Fallo(401, "Credenciales inválidas");
        }
        Cuenta cuenta = cuenta(usuario.get());
        Instant ahora = Instant.now();
        long segundos = 3600;
        var claims = JwtClaimsSet.builder().issuer(Seguridad.EMISOR)
                .subject(cuenta.email()).issuedAt(ahora).expiresAt(ahora.plusSeconds(segundos))
                .claim("roles", cuenta.roles()).build();
        var cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return new Token(tokens.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue(),
                "Bearer", segundos);
    }

    public Cuenta perfil(String email) {
        return cuenta(usuario(email));
    }

    public Pagina<Cuenta> usuarios(Pageable pagina) {
        return Pagina.de(usuarios.findAll(pagina).map(Biblioteca::cuenta));
    }

    @Transactional
    public Ficha registrarLibro(AltaLibro datos) {
        var libro = new Libro();
        libro.isbn = datos.isbn();
        libro.titulo = datos.titulo().strip();
        libro.autor = datos.autor().strip();
        libro.total = datos.ejemplares();
        libro.disponibles = libro.total;
        return ficha(libros.saveAndFlush(libro));
    }

    public Pagina<Ficha> catalogo(String busqueda, Pageable pagina) {
        String texto = busqueda.strip();
        return Pagina.de(libros.findByTituloContainingIgnoreCaseOrAutorContainingIgnoreCase(texto, texto, pagina)
                .map(Biblioteca::ficha));
    }

    public Ficha libro(long id) {
        return ficha(libros.findById(id).orElseThrow(() -> new Fallo(404, "Libro inexistente")));
    }

    @Transactional
    public Movimiento prestar(AltaPrestamo datos) {
        var usuario = usuarios.findById(datos.usuarioId())
                .orElseThrow(() -> new Fallo(404, "Usuario inexistente"));
        Libro libro = bloquearLibro(datos.libroId());
        if (libro.disponibles == 0) {
            throw new Fallo(409, "El libro no tiene ejemplares disponibles");
        }
        libro.disponibles--;
        var prestamo = new Prestamo();
        prestamo.usuario = usuario;
        prestamo.libro = libro;
        prestamo.prestadoEn = Instant.now().truncatedTo(ChronoUnit.MICROS);
        prestamo.venceEn = prestamo.prestadoEn.plus(datos.dias(), ChronoUnit.DAYS);
        return movimiento(prestamos.save(prestamo));
    }

    @Transactional
    public Movimiento devolver(long id) {
        var prestamo = prestamos.bloquear(id).orElseThrow(() -> new Fallo(404, "Préstamo inexistente"));
        if (prestamo.devueltoEn != null) {
            throw new Fallo(409, "El préstamo ya fue devuelto");
        }
        Libro libro = bloquearLibro(idDe(prestamo.libro));
        libro.disponibles++;
        prestamo.devueltoEn = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return movimiento(prestamo);
    }

    public Pagina<Movimiento> historial(String email, Pageable pagina) {
        return Pagina.de(prestamos.findByUsuarioId(usuario(email).id, pagina).map(this::movimiento));
    }

    public Pagina<Movimiento> prestamos(Pageable pagina) {
        return Pagina.de(prestamos.findAll(pagina).map(this::movimiento));
    }

    private Usuario crearUsuario(String nombre, String email, String clave, String rol) {
        if (!Seguridad.longitudBcrypt(clave)) {
            throw new Fallo(400, "La clave supera los 72 bytes admitidos");
        }
        var usuario = new Usuario();
        usuario.nombre = nombre.strip();
        usuario.email = email;
        usuario.claveHash = claves.encode(clave);
        usuario.roles.add(roles.findById(rol).orElseThrow());
        return usuarios.saveAndFlush(usuario);
    }

    private Usuario usuario(String email) {
        return usuarios.findByEmail(email).orElseThrow(() -> new Fallo(404, "Usuario inexistente"));
    }

    private Libro bloquearLibro(long id) {
        Libro libro = libros.bloquear(id).orElseThrow(() -> new Fallo(404, "Libro inexistente"));
        return Hibernate.unproxy(libro, Libro.class);
    }

    private long idDe(Object entidad) {
        return (Long) identidad.getIdentifier(entidad);
    }

    private Movimiento movimiento(Prestamo p) {
        return new Movimiento(p.id, idDe(p.usuario), idDe(p.libro), p.prestadoEn,
                p.venceEn, p.devueltoEn, p.devueltoEn == null ? "ACTIVO" : "DEVUELTO");
    }

    private static Ficha ficha(Libro l) {
        return new Ficha(l.id, l.isbn, l.titulo, l.autor, l.total, l.disponibles);
    }

    private static Cuenta cuenta(Usuario u) {
        return new Cuenta(u.id, u.nombre, u.email, u.roles.stream().map(r -> r.nombre).sorted().toList());
    }

    private static String email(String valor) {
        return valor.strip().toLowerCase(Locale.ROOT);
    }
}
