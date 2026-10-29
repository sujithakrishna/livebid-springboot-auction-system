package com.sujitha.livebidspringboot.dao;

import com.sujitha.livebidspringboot.model.Auction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class AuctionDAO {

    private final JdbcTemplate jdbcTemplate;

    public AuctionDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------
    // FIND / SEARCH / FILTER AUCTIONS
    // ---------------------------------------------------------

    public List<Auction> findAll(
            String status,
            String search,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            boolean endingSoon) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    id,
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                FROM auctions
                WHERE 1 = 1
                """);

        List<Object> parameters = new ArrayList<>();

        // -----------------------------------------------------
        // STATUS FILTER
        // -----------------------------------------------------

        if (status != null && !status.isBlank()) {

            sql.append(" AND status = ? ");
            parameters.add(status);
        }

        // -----------------------------------------------------
        // SEARCH FILTER
        // Searches title and description
        // -----------------------------------------------------

        if (search != null && !search.isBlank()) {

            sql.append("""
                    AND (
                        LOWER(title) LIKE ?
                        OR LOWER(description) LIKE ?
                    )
                    """);

            String searchPattern =
                    "%" + search.trim().toLowerCase() + "%";

            parameters.add(searchPattern);
            parameters.add(searchPattern);
        }

        // -----------------------------------------------------
        // MINIMUM STARTING PRICE
        // -----------------------------------------------------

        if (minPrice != null) {

            sql.append(
                    " AND starting_price >= ? "
            );

            parameters.add(minPrice);
        }

        // -----------------------------------------------------
        // MAXIMUM STARTING PRICE
        // -----------------------------------------------------

        if (maxPrice != null) {

            sql.append(
                    " AND starting_price <= ? "
            );

            parameters.add(maxPrice);
        }

        // -----------------------------------------------------
        // ENDING SOON
        // LIVE auctions ending within 30 minutes
        // -----------------------------------------------------

        if (endingSoon) {

            sql.append("""
                    AND status = 'LIVE'
                    AND end_time > NOW()
                    AND end_time <= DATE_ADD(
                        NOW(),
                        INTERVAL 30 MINUTE
                    )
                    """);
        }

        // -----------------------------------------------------
        // ORDER
        // Ending soon first when requested, otherwise ID
        // -----------------------------------------------------

        if (endingSoon) {

            sql.append(
                    " ORDER BY end_time ASC "
            );

        } else {

            sql.append(
                    " ORDER BY id ASC "
            );
        }

        return jdbcTemplate.query(
                sql.toString(),
                (resultSet, rowNumber) ->
                        mapAuction(resultSet),
                parameters.toArray()
        );
    }

    // ---------------------------------------------------------
    // FIND AUCTION BY ID
    // ---------------------------------------------------------

    public Auction findById(long auctionId) {

        String sql = """
                SELECT
                    id,
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                FROM auctions
                WHERE id = ?
                """;

        List<Auction> auctions =
                jdbcTemplate.query(
                        sql,
                        (resultSet, rowNumber) ->
                                mapAuction(resultSet),
                        auctionId
                );

        if (auctions.isEmpty()) {
            return null;
        }

        return auctions.get(0);
    }

    // ---------------------------------------------------------
    // CREATE AUCTION
    // ---------------------------------------------------------

    public Auction save(Auction auction) {

        String sql = """
                INSERT INTO auctions (
                    seller_id,
                    title,
                    description,
                    image_url,
                    starting_price,
                    current_highest_bid,
                    current_highest_bidder_id,
                    start_time,
                    end_time,
                    status,
                    version
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder =
                new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql,
                            Statement.RETURN_GENERATED_KEYS
                    );

            statement.setLong(
                    1,
                    auction.getSellerId()
            );

            statement.setString(
                    2,
                    auction.getTitle()
            );

            statement.setString(
                    3,
                    auction.getDescription()
            );

            statement.setString(
                    4,
                    auction.getImageUrl()
            );

            statement.setBigDecimal(
                    5,
                    auction.getStartingPrice()
            );

            statement.setBigDecimal(
                    6,
                    auction.getCurrentHighestBid()
            );

            if (auction.getCurrentHighestBidderId()
                    == null) {

                statement.setNull(
                        7,
                        java.sql.Types.BIGINT
                );

            } else {

                statement.setLong(
                        7,
                        auction.getCurrentHighestBidderId()
                );
            }

            statement.setTimestamp(
                    8,
                    Timestamp.valueOf(
                            auction.getStartTime()
                    )
            );

            statement.setTimestamp(
                    9,
                    Timestamp.valueOf(
                            auction.getEndTime()
                    )
            );

            statement.setString(
                    10,
                    auction.getStatus()
            );

            statement.setLong(
                    11,
                    auction.getVersion()
            );

            return statement;

        }, keyHolder);

        Number generatedId =
                keyHolder.getKey();

        if (generatedId == null) {

            throw new IllegalStateException(
                    "Auction was created but no ID was generated."
            );
        }

        return findById(
                generatedId.longValue()
        );
    }

    // ---------------------------------------------------------
    // MAP RESULT SET TO AUCTION
    // ---------------------------------------------------------

    private Auction mapAuction(
            java.sql.ResultSet resultSet)
            throws java.sql.SQLException {

        Timestamp startTimestamp =
                resultSet.getTimestamp("start_time");

        Timestamp endTimestamp =
                resultSet.getTimestamp("end_time");

        Long bidderId = null;

        long bidder =
                resultSet.getLong(
                        "current_highest_bidder_id"
                );

        if (!resultSet.wasNull()) {
            bidderId = bidder;
        }

        return new Auction(
                resultSet.getLong("id"),
                resultSet.getLong("seller_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("image_url"),
                resultSet.getBigDecimal(
                        "starting_price"
                ),
                resultSet.getBigDecimal(
                        "current_highest_bid"
                ),
                bidderId,
                startTimestamp != null
                        ? startTimestamp.toLocalDateTime()
                        : null,
                endTimestamp != null
                        ? endTimestamp.toLocalDateTime()
                        : null,
                resultSet.getString("status"),
                resultSet.getLong("version")
        );
    }
}