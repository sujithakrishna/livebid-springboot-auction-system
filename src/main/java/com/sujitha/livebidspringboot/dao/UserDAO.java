package com.sujitha.livebidspringboot.dao;

import com.sujitha.livebidspringboot.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class UserDAO {

    private final JdbcTemplate jdbcTemplate;

    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User save(User user) {

        String sql = """
                INSERT INTO users (
                    name,
                    email,
                    password_hash
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                user.getName(),
                user.getEmail(),
                user.getPasswordHash()
        );

        Long generatedId = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
        );

        if (generatedId == null) {
            throw new IllegalStateException(
                    "User was inserted but no ID was generated."
            );
        }

        return findById(generatedId);
    }

    public User findById(long id) {

        String sql = """
                SELECT
                    id,
                    name,
                    email,
                    password_hash,
                    created_at
                FROM users
                WHERE id = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapUser(resultSet),
                id
        );

        return users.isEmpty() ? null : users.get(0);
    }

    public User findByEmail(String email) {

        String sql = """
                SELECT
                    id,
                    name,
                    email,
                    password_hash,
                    created_at
                FROM users
                WHERE email = ?
                """;

        List<User> users = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapUser(resultSet),
                email
        );

        return users.isEmpty() ? null : users.get(0);
    }

    private User mapUser(ResultSet resultSet)
            throws SQLException {

        return new User(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getTimestamp("created_at") != null
                        ? resultSet
                        .getTimestamp("created_at")
                        .toLocalDateTime()
                        : null
        );
    }
}