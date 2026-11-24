package edu.biblioteca;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@SecurityScheme(name = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class Seguridad {
    static final String EMISOR = "biblioteca-universitaria";

    @Bean
    SecretKey clave(@Value("${app.jwt-secret}") String secreto) {
        byte[] bytes = Base64.getDecoder().decode(secreto);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe codificar al menos 32 bytes en Base64");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder encoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder decoder(SecretKey clave) {
        var decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISOR));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    static boolean longitudBcrypt(String clave) {
        return clave.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, Errores errores) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(roles);

        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/libros", "/api/libros/{id}", "/api/usuarios/yo",
                                "/api/prestamos/mis").hasAnyRole("ADMIN", "USUARIO")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios", "/api/prestamos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/libros", "/api/prestamos", "/api/prestamos/{id}/devolucion")
                                .hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(errores::sinToken)
                        .accessDeniedHandler((req, res, ex) -> errores.escribir(res, 403, "Acceso denegado")))
                .oauth2ResourceServer(o -> o.authenticationEntryPoint(errores::sinToken)
                        .jwt(j -> j.jwtAuthenticationConverter(conversor)))
                .build();
    }
}
