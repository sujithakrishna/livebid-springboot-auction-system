package com.sujitha.livebidspringboot.controller;

import com.sujitha.livebidspringboot.dto.LoginRequest;
import com.sujitha.livebidspringboot.dto.LoginResponse;
import com.sujitha.livebidspringboot.dto.RegisterRequest;
import com.sujitha.livebidspringboot.model.User;
import com.sujitha.livebidspringboot.security.JwtService;
import com.sujitha.livebidspringboot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(
            UserService userService,
            JwtService jwtService) {

        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @Valid @RequestBody RegisterRequest request) {

        User savedUser = userService.registerUser(
                request.getName(),
                request.getEmail(),
                request.getPassword()
        );

        return Map.of(
                "id", savedUser.getId(),
                "name", savedUser.getName(),
                "email", savedUser.getEmail(),
                "createdAt", savedUser.getCreatedAt()
        );
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {

        User user = userService.findByEmail(request.getEmail());

        if (user == null ||
                !userService.verifyPassword(
                        request.getPassword(),
                        user.getPasswordHash())) {

            throw new IllegalArgumentException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail()
        );

        return new LoginResponse(
                token,
                "Bearer",
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}