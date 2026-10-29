package com.sujitha.livebidspringboot.service;

import com.sujitha.livebidspringboot.dao.NotificationDAO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuctionClosingService {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDAO notificationDAO;

    public AuctionClosingService(
            JdbcTemplate jdbcTemplate,
            NotificationDAO notificationDAO) {

        this.jdbcTemplate = jdbcTemplate;
        this.notificationDAO = notificationDAO;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void updateAuctionStatuses() {

        // ---------------------------------------------------------
        // STEP 1: SCHEDULED -> LIVE
        // ---------------------------------------------------------

        String activateSql = """
                UPDATE auctions
                SET status = 'LIVE'
                WHERE status = 'SCHEDULED'
                  AND start_time <= NOW()
                  AND end_time > NOW()
                """;

        jdbcTemplate.update(activateSql);


        // ---------------------------------------------------------
        // STEP 2: LIVE -> CLOSED
        // ---------------------------------------------------------

        String findExpiredSql = """
                SELECT
                    id,
                    current_highest_bidder_id
                FROM auctions
                WHERE status = 'LIVE'
                  AND end_time <= NOW()
                ORDER BY end_time ASC
                """;

        List<ExpiredAuction> expiredAuctions =
                jdbcTemplate.query(
                        findExpiredSql,
                        (resultSet, rowNumber) ->
                                new ExpiredAuction(
                                        resultSet.getLong("id"),
                                        getNullableLong(
                                                resultSet,
                                                "current_highest_bidder_id"
                                        )
                                )
                );

        for (ExpiredAuction auction : expiredAuctions) {

            String closeSql = """
                    UPDATE auctions
                    SET status = 'CLOSED'
                    WHERE id = ?
                      AND status = 'LIVE'
                      AND end_time <= NOW()
                    """;

            int rowsUpdated =
                    jdbcTemplate.update(
                            closeSql,
                            auction.auctionId()
                    );

            /*
             * Only the thread that successfully changed
             * LIVE -> CLOSED is allowed to create the
             * winner notification.
             */
            if (rowsUpdated != 1) {
                continue;
            }

            if (auction.highestBidderId() != null) {

                notificationDAO.createNotification(
                        auction.highestBidderId(),
                        auction.auctionId(),
                        "WON"
                );
            }
        }
    }

    // ---------------------------------------------------------
    // GET NULLABLE LONG
    // ---------------------------------------------------------

    private Long getNullableLong(
            java.sql.ResultSet resultSet,
            String columnName)
            throws java.sql.SQLException {

        long value =
                resultSet.getLong(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    // ---------------------------------------------------------
    // EXPIRED AUCTION RECORD
    // ---------------------------------------------------------

    private record ExpiredAuction(
            long auctionId,
            Long highestBidderId) {
    }
}