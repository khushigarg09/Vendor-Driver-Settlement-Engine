package com.moveinsync.settlement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${settlement.security.admin-password:admin123}")
    private String adminPassword;

    @Value("${settlement.security.approver-password:approver123}")
    private String approverPassword;

    @Value("${settlement.security.employee-password:employee123}")
    private String employeePassword;

    @Value("${settlement.security.driver2-password:driver123}")
    private String driver2Password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/v1/health",
                                "/h2-console/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "APPROVER", "EMPLOYEE")
                .build();

        UserDetails approver = User.builder()
                .username("approver")
                .password(passwordEncoder.encode(approverPassword))
                .roles("APPROVER", "EMPLOYEE")
                .build();

        UserDetails employee = User.builder()
                .username("employee")
                .password(passwordEncoder.encode(employeePassword))
                .roles("EMPLOYEE")
                .build();

        UserDetails driver2 = User.builder()
                .username("driver2")
                .password(passwordEncoder.encode(driver2Password))
                .roles("EMPLOYEE")
                .build();

        return new InMemoryUserDetailsManager(admin, approver, employee, driver2);
    }
}
