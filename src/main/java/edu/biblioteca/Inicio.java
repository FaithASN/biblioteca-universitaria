package edu.biblioteca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class Inicio {
    @Bean
    ApplicationRunner administrador(Biblioteca biblioteca,
                                    @Value("${app.admin-email}") String email,
                                    @Value("${app.admin-password}") String clave) {
        return args -> biblioteca.inicializarAdmin(email, clave);
    }
}
