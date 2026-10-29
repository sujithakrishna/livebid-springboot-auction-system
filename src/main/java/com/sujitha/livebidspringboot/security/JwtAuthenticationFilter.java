
package com.sujitha.livebidspringboot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        System.out.println("========== JWT DEBUG ==========");
        System.out.println("Request: " + request.getMethod()
                + " " + request.getRequestURI());

        System.out.println("Authorization header present: "
                + (authorizationHeader != null));

        System.out.println("Bearer prefix present: "
                + (authorizationHeader != null
                && authorizationHeader.startsWith("Bearer ")));

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            System.out.println("JWT DEBUG: No Bearer token found.");
            System.out.println("================================");

            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHeader.substring(7).trim();

        try {
            if (!jwtService.isTokenValid(token)) {
                System.out.println(
                        "JWT DEBUG: Token is invalid or expired."
                );

                SecurityContextHolder.clearContext();

            } else {
                long userId = jwtService.extractUserId(token);
                String email = jwtService.extractEmail(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(
                                        new SimpleGrantedAuthority("ROLE_USER")
                                )
                        );

                authentication.setDetails(email);

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);

                System.out.println(
                        "JWT DEBUG: Token accepted for user ID: "
                                + userId
                );

                System.out.println(
                        "JWT DEBUG: Authentication established: "
                                + SecurityContextHolder.getContext()
                                .getAuthentication().isAuthenticated()
                );
            }

        } catch (Exception e) {
            System.out.println(
                    "JWT DEBUG: Validation failed: "
                            + e.getClass().getSimpleName()
            );

            SecurityContextHolder.clearContext();
        }

        System.out.println("================================");

        filterChain.doFilter(request, response);
    }
}
