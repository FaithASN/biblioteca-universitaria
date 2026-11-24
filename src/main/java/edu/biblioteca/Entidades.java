package edu.biblioteca;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
class Rol {
    @Id
    @Column(length = 20)
    String nombre;
}

@Entity
class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 120)
    String nombre;
    @Column(nullable = false, unique = true, length = 254)
    String email;
    @Column(nullable = false, length = 100)
    String claveHash;
    @ManyToMany
    @JoinTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_nombre"))
    Set<Rol> roles = new HashSet<>();
}

@Entity
class Libro {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, unique = true, length = 13)
    String isbn;
    @Column(nullable = false, length = 200)
    String titulo;
    @Column(nullable = false, length = 150)
    String autor;
    int total;
    int disponibles;
}

@Entity
class Prestamo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "libro_id")
    Libro libro;
    @Column(nullable = false)
    Instant prestadoEn;
    @Column(nullable = false)
    Instant venceEn;
    Instant devueltoEn;
}
