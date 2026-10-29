package com.sujitha.livebidspringboot.dao;

import com.sujitha.livebidspringboot.exception.OptimisticLockException;
import com.sujitha.livebidspringboot.model.Bid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class BidDAO {

    /*
     * Anti-sniping configuration.
     *
     * If a valid bid arrives with 2 minutes or less remaining,
     * the auction end time is extended by 2 minutes.
     */
    private static final int ANTI_SNIPE_WINDOW_MINUTES = 2;
    private static final int ANTI_SNIPE_EXTENSION_MINUTES = 2;

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDAO notificationDAO;

    public BidDAO(
            JdbcTemplate jdbcTemplate,
            NotificationDAO notificationDAO) {

        this.jdbcTemplate = jdbcTemplate;
        this.notificationDAO = notificationDAO;
    }

    // ---------------------------------------------------------
    // CHECK USER EXISTS
    // ---------------------------------------------------------

    public boolean userExists(long userId) {

        String sql = """
                SELECT COUNT(*)
                FROM users
                WHERE id = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                userId
        );

        return count != null && count > 0;
    }

    // ---------------------------------------------------------
    // GET BID HISTORY
    // ---------------------------------------------------------

    public List<Bid> findByAuctionId(long auctionId) {

        String sql = """
                SELECT
                    id,
                    auction_id,
                    bidder_id,
                    amount,
                    placed_at,
                    auto_bid
                FROM bids
                WHERE auction_id = ?
                ORDER BY placed_at ASC, id ASC
                """;

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        mapBid(resultSet),
                auctionId
        );
    }

    // ---------------------------------------------------------
    // GET BID BY ID
    // ---------------------------------------------------------

    public Bid findById(long bidId) {

        String sql = """
                SELECT
                    id,
                    auction_id,
                    bidder_id,
                    amount,
                    placed_at,
                    auto_bid
                FROM bids
                WHERE id = ?
                """;

        List<Bid> bids = jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) ->
                        mapBid(resultSet),
                bidId
        );

        return bids.isEmpty()
                ? null
                : bids.get(0);
    }

    // ---------------------------------------------------------
    // PLACE BID
    // ---------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public Bid placeBid(
            Bid bid,
            long expectedVersion,
            Long previousHighestBidderId) {

        /*
         * Step 1:
         * Read the current auction state.
         */
        String auctionSql = """
                SELECT
                    current_highest_bid,
                    current_highest_bidder_id,
                    status,
                    end_time,
                    version
                FROM auctions
                WHERE id = ?
                """;

        AuctionState auction =
                jdbcTemplate.query(
                                auctionSql,
                                (resultSet, rowNumber) ->
                                        new AuctionState(
                                                resultSet.getBigDecimal(
                                                        "current_highest_bid"
                                                ),
                                                getNullableLong(
                                                        resultSet,
                                                        "current_highest_bidder_id"
                                                ),
                                                resultSet.getString(
                                                        "status"
                                                ),
                                                resultSet.getTimestamp(
                                                        "end_time"
                                                ) != null
                                                        ? resultSet
                                                        .getTimestamp(
                                                                "end_time"
                                                        )
                                                        .toLocalDateTime()
                                                        : null,
                                                resultSet.getLong(
                                                        "version"
                                                )
                                        ),
                                bid.getAuctionId()
                        )
                        .stream()
                        .findFirst()
                        .orElse(null);

        if (auction == null) {

            throw new IllegalArgumentException(
                    "Auction not found with ID: "
                            + bid.getAuctionId()
            );
        }

        /*
         * Step 2:
         * Auction must be LIVE.
         */
        if (!"LIVE".equals(auction.status())) {

            throw new IllegalArgumentException(
                    "Auction is not LIVE."
            );
        }

        /*
         * Step 3:
         * Auction must not have ended.
         */
        if (auction.endTime() == null ||
                !auction.endTime()
                        .isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Auction has ended."
            );
        }

        /*
         * Step 4:
         * Optimistic locking check.
         */
        if (auction.version() != expectedVersion) {

            throw new OptimisticLockException(
                    "Auction version changed. Retry the bid."
            );
        }

        /*
         * Step 5:
         * Bid must be higher than the current highest bid.
         */
        if (bid.getAmount()
                .compareTo(
                        auction.currentHighestBid()
                ) <= 0) {

            throw new IllegalArgumentException(
                    "Your bid must be greater than "
                            + "the current highest bid of "
                            + auction.currentHighestBid()
                            + "."
            );
        }

        /*
         * Step 6:
         * Atomically update the auction.
         *
         * This operation:
         *
         * 1. Updates highest bid.
         * 2. Updates highest bidder.
         * 3. Increments version.
         * 4. Applies anti-sniping.
         *
         * Anti-sniping:
         *
         * If the bid arrives while 2 minutes or less
         * remain, extend the auction by 2 minutes.
         *
         * Example:
         *
         * Existing end:
         * 23:47:35
         *
         * Bid arrives:
         * 23:46:50
         *
         * Remaining:
         * 45 seconds
         *
         * New end:
         * 23:49:35
         */
        String updateSql = """
                UPDATE auctions
                SET
                    current_highest_bid = ?,
                    current_highest_bidder_id = ?,

                    end_time =
                        CASE
                            WHEN end_time <=
                                 DATE_ADD(
                                     NOW(),
                                     INTERVAL ? MINUTE
                                 )
                            THEN
                                DATE_ADD(
                                    end_time,
                                    INTERVAL ? MINUTE
                                )
                            ELSE
                                end_time
                        END,

                    version = version + 1

                WHERE id = ?
                  AND version = ?
                  AND current_highest_bid < ?
                  AND status = 'LIVE'
                  AND end_time > NOW()
                """;

        int rowsUpdated =
                jdbcTemplate.update(
                        updateSql,

                        bid.getAmount(),
                        bid.getBidderId(),

                        ANTI_SNIPE_WINDOW_MINUTES,
                        ANTI_SNIPE_EXTENSION_MINUTES,

                        bid.getAuctionId(),
                        expectedVersion,
                        bid.getAmount()
                );

        /*
         * Zero rows means another operation changed
         * the auction first.
         */
        if (rowsUpdated == 0) {

            throw new OptimisticLockException(
                    "Concurrent bid conflict detected."
            );
        }

        /*
         * Step 7:
         * Insert successful bid.
         */
        String insertSql = """
                INSERT INTO bids (
                    auction_id,
                    bidder_id,
                    amount,
                    placed_at,
                    auto_bid
                )
                VALUES (?, ?, ?, NOW(), ?)
                """;

        KeyHolder keyHolder =
                new GeneratedKeyHolder();

        jdbcTemplate.update(
                connection -> {

                    PreparedStatement statement =
                            connection.prepareStatement(
                                    insertSql,
                                    Statement.RETURN_GENERATED_KEYS
                            );

                    statement.setLong(
                            1,
                            bid.getAuctionId()
                    );

                    statement.setLong(
                            2,
                            bid.getBidderId()
                    );

                    statement.setBigDecimal(
                            3,
                            bid.getAmount()
                    );

                    statement.setBoolean(
                            4,
                            bid.isAutoBid()
                    );

                    return statement;

                },
                keyHolder
        );

        /*
         * Step 8:
         * Get generated bid ID.
         */
        Number generatedId =
                keyHolder.getKey();

        if (generatedId == null) {

            throw new IllegalStateException(
                    "Bid was inserted but no ID was generated."
            );
        }

        /*
         * Step 9:
         * Retrieve saved bid.
         */
        Bid savedBid =
                findById(
                        generatedId.longValue()
                );

        if (savedBid == null) {

            throw new IllegalStateException(
                    "Bid was inserted but could not be retrieved."
            );
        }

        /*
         * Step 10:
         * Notify previous highest bidder.
         */
        if (previousHighestBidderId != null &&
                previousHighestBidderId != bid.getBidderId()) {

            notificationDAO.createNotification(
                    previousHighestBidderId,
                    bid.getAuctionId(),
                    "OUTBID"
            );
        }

        return savedBid;
    }

    // ---------------------------------------------------------
    // MAP BID
    // ---------------------------------------------------------

    private Bid mapBid(
            ResultSet resultSet)
            throws java.sql.SQLException {

        return new Bid(
                resultSet.getLong("id"),
                resultSet.getLong("auction_id"),
                resultSet.getLong("bidder_id"),
                resultSet.getBigDecimal("amount"),
                resultSet.getTimestamp("placed_at") != null
                        ? resultSet
                        .getTimestamp("placed_at")
                        .toLocalDateTime()
                        : null,
                resultSet.getBoolean("auto_bid")
        );
    }

    // ---------------------------------------------------------
    // GET NULLABLE LONG
    // ---------------------------------------------------------

    private Long getNullableLong(
            ResultSet resultSet,
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
    // AUCTION STATE
    // ---------------------------------------------------------

    private record AuctionState(
            BigDecimal currentHighestBid,
            Long currentHighestBidderId,
            String status,
            LocalDateTime endTime,
            long version) {
    }
}