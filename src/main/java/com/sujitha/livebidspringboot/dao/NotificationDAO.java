package com.sujitha.livebidspringboot.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Repository
public class NotificationDAO {

    private final JdbcTemplate jdbcTemplate;

    public NotificationDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------
    // CREATE NOTIFICATION
    // ---------------------------------------------------------

    public void createNotification(
            long userId,
            long auctionId,
            String type) {

        String sql = """
                INSERT INTO notifications (
                    user_id,
                    auction_id,
                    type
                )
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                userId,
                auctionId,
                type
        );
    }

    // ---------------------------------------------------------
    // CHECK RECENT NOTIFICATION
    // ---------------------------------------------------------

    public boolean existsSince(
            long userId,
            long auctionId,
            String type,
            LocalDateTime since) {

        String sql = """
                SELECT COUNT(*)
                FROM notifications
                WHERE user_id = ?
                  AND auction_id = ?
                  AND type = ?
                  AND sent_at >= ?
                """;

        Integer count =
                jdbcTemplate.queryForObject(
                        sql,
                        Integer.class,
                        userId,
                        auctionId,
                        type,
                        java.sql.Timestamp.valueOf(since)
                );

        return count != null && count > 0;
    }

    public List<Map<String, Object>> findByUserId(long userId) {
        String sql = """
            SELECT
                n.id,
                n.user_id,
                n.auction_id,
                a.title AS auction_title,
                n.type,
                n.sent_at
            FROM notifications n
            LEFT JOIN auctions a ON a.id = n.auction_id
            WHERE n.user_id = ?
            ORDER BY n.sent_at DESC
            """;

        return jdbcTemplate.queryForList(sql, userId);
    }
}