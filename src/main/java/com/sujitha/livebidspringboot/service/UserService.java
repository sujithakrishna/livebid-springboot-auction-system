package com.sujitha.livebidspringboot.service;

import com.sujitha.livebidspringboot.dao.UserDAO;
import com.sujitha.livebidspringboot.model.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserDAO userDAO;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User registerUser(
            String name,
            String email,
            String password) {

        validateRegistrationData(name, email, password);

        String normalizedEmail =
                email.trim().toLowerCase();

        User existingUser =
                userDAO.findByEmail(normalizedEmail);

        if (existingUser != null) {
            throw new IllegalArgumentException(
                    "An account with this email already exists."
            );
        }

        String passwordHash =
                passwordEncoder.encode(password);

        User user = new User(
                name.trim(),
                normalizedEmail,
                passwordHash
        );

        return userDAO.save(user);
    }

    public User findByEmail(String email) {

        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        return userDAO.findByEmail(
                email.trim().toLowerCase()
        );
    }

    public boolean verifyPassword(
            String rawPassword,
            String passwordHash) {

        if (rawPassword == null ||
                passwordHash == null) {

            return false;
        }

        return passwordEncoder.matches(
                rawPassword,
                passwordHash
        );
    }

    private void validateRegistrationData(
            String name,
            String email,
            String password) {

        if (name == null ||
                name.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Name cannot be empty."
            );
        }

        if (name.trim().length() > 100) {

            throw new IllegalArgumentException(
                    "Name must not exceed 100 characters."
            );
        }

        if (email == null ||
                email.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Email cannot be empty."
            );
        }

        if (email.trim().length() > 150) {

            throw new IllegalArgumentException(
                    "Email must not exceed 150 characters."
            );
        }

        if (!email.matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {

            throw new IllegalArgumentException(
                    "Invalid email address."
            );
        }

        if (password == null ||
                password.length() < 6) {

            throw new IllegalArgumentException(
                    "Password must contain at least 6 characters."
            );
        }
    }
}