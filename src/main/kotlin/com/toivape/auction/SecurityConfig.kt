package com.toivape.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.security.web.SecurityFilterChain

private val log = KotlinLogging.logger {}

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    companion object{
        val PUBLIC_URI = arrayOf(
            "/favicon.ico",
            "/css/**",
            "/js/**",
            "/error",
            "/actuator/**",
            "/healthCheck"
        )
    }

    @Bean
    fun inMemoryUsers(pwencoder:PasswordEncoder): UserDetailsManager {
        log.info { "inMemoryUsers() called. Initializing in-memory user details manager." }

        val admin = User.builder()
            .username("dummy-admin@toivape.com")
            .password(pwencoder.encode("stork"))
            .roles("ADMIN", "USER")
            .build()

        val bidder = User.builder()
            .username("dummy-user@toivape.com")
            .password(pwencoder.encode("stork"))
            .roles("USER")
            .build()

        return InMemoryUserDetailsManager(admin, bidder)
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        log.info { "filterChain() called. Initializing security filter chain." }
        http {
            authorizeHttpRequests {
                PUBLIC_URI.forEach { authorize(it, permitAll) }
                authorize(anyRequest, authenticated)
            }
            formLogin { }
        }
        return http.build()
    }
}