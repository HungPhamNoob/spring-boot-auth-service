package com.example.demo.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity // bật Spring Security cho web
@EnableMethodSecurity // cho phép dùng @PreAuthorize, @PostAuthorize
public class SecurityConfig {

    // Các endpoint cho phép truy cập mà KHÔNG cần token (anonymous access)
    private final String[] PUBLIC_ENDPOINTS = {
            "/users",          // đăng ký user mới
            "/auth/token",     // lấy token
            "/auth/introspect", // introspect token
            "/auth/logout",
            "/auth/refresh"
    };

    @Autowired
    private CustomJwtDecoder customJwtDecoder;

    @Value("${jwt.signerKey}") // key ký JWT lấy từ application.yml
    private String signerKey;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // Config rule cho request
        httpSecurity.authorizeHttpRequests(request ->
                request.requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll() // cho phép POST vào các endpoint public
                        .anyRequest().authenticated()); // các request khác thì bắt buộc phải có JWT hợp lệ

        // Config để Spring Security hiểu app này là Resource Server (sử dụng JWT để authen)
        httpSecurity.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwtConfigurer ->
                        jwtConfigurer.decoder(customJwtDecoder) // validate chữ ký + hạn token
                                // chuyển claims ("user", "admin") thành GrantedAuthorities ("ROLE_user", "ROLE_admin")
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())

                )
        );

        // Disable CSRF (vì API không dùng form submit truyền thống)
        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        return httpSecurity.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(){
        // Converter để đọc claim "roles" hoặc "scope" từ JWT rồi convert thành GrantedAuthority
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_"); // thêm prefix ROLE_ để Spring Security hiểu

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }

    @Bean
    JwtDecoder jwtDecoder(){
        // Giải mã + verify JWT dùng thuật toán HMAC SHA512
        SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HS512");
        return NimbusJwtDecoder
                .withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder(){
        // Dùng BCrypt để hash password với strength = 10
        return new BCryptPasswordEncoder(10);
    }
}
