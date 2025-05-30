package com.toivape.auction

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.toivape.auction.AdminItem.Companion.toAdminItem
import com.toivape.auction.AuctionItemRecord.Companion.toAuctionItem
import com.toivape.auction.FrontPageItem.Companion.toFrontPageItem
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun String.toUUID(): UUID = UUID.fromString(this)

data class AuctionItemRecord(
    val id: UUID = UUID.randomUUID(),
    val externalId: String,
    val description: String,
    val category: String,
    val purchaseDate: LocalDate,
    val purchasePrice: BigDecimal,
    val biddingEndDate: LocalDate,
    val startingPrice: Int,
    val minimumRaise: Int,
    val isTransferred: Boolean,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) {

    companion object {
        fun toAuctionItem(rs: ResultSet) = AuctionItemRecord(
            id = UUID.fromString(rs.getString("id")),
            externalId = rs.getString("external_id"),
            description = rs.getString("description"),
            category = rs.getString("category"),
            purchaseDate = rs.getDate("purchase_date").toLocalDate(),
            purchasePrice = rs.getBigDecimal("purchase_price"),
            biddingEndDate = rs.getDate("bidding_end_date").toLocalDate(),
            startingPrice = rs.getInt("starting_price"),
            minimumRaise = rs.getInt("minimum_raise"),
            isTransferred = rs.getBoolean("is_transferred"),
            createdAt = rs.getTimestamp("created_at").toLocalDateTime(),
            updatedAt = rs.getTimestamp("updated_at").toLocalDateTime(),
        )
    }
}

data class AdminItem(
    val id: String,
    val description: String,
    val biddingEndDate: LocalDate,
    val timesRenewed: Int,
    val isTransferred: Boolean,
    val currentPrice: Int,
    val numberOfBids: Int
) {
    companion object {
        fun toAdminItem(rs: ResultSet) = AdminItem(
            id = rs.getString("id"),
            description = rs.getString("description"),
            biddingEndDate = rs.getDate("bidding_end_date").toLocalDate(),
            timesRenewed = rs.getInt("times_renewed"),
            isTransferred = rs.getBoolean("is_transferred"),
            currentPrice = rs.getInt("current_price"),
            numberOfBids = rs.getInt("number_of_bids")
        )
    }
}

data class FrontPageItem(
    val id: String,
    val description: String,
    val category: String,
    val purchaseDate: LocalDate,
    val biddingEndDate: LocalDate,
    val currentPrice: Int
) {
    companion object {
        fun toFrontPageItem(rs: ResultSet) = FrontPageItem(
            id = rs.getString("id"),
            description = rs.getString("description"),
            category = rs.getString("category"),
            purchaseDate = rs.getDate("purchase_date").toLocalDate(),
            biddingEndDate = rs.getDate("bidding_end_date").toLocalDate(),
            currentPrice = rs.getInt("current_price"),
        )
    }
}

private val log = KotlinLogging.logger {}

@Repository
class AuctionDao(val db: JdbcTemplate) {

    companion object {
        private val FIND_ALL_ADMIN = """
            SELECT     
            ai.id,
            ai.description,
            ai.bidding_end_date,
            ai.times_renewed,
            ai.is_transferred,
            COALESCE(
                (SELECT b.bid_price
                 FROM bid b
                 WHERE b.fk_auction_item_id = ai.id
                 AND b.is_deleted = false
                 ORDER BY b.bid_time DESC
                 LIMIT 1
                ), ai.starting_price
            ) AS current_price,
            COUNT(CASE WHEN b.is_deleted = false THEN b.id END) AS number_of_bids
        FROM 
            auction_item ai
        LEFT JOIN 
            bid b ON ai.id = b.fk_auction_item_id
        GROUP BY 
            ai.id, ai.description, ai.bidding_end_date, ai.times_renewed, ai.is_transferred, ai.starting_price
        ORDER BY 
            ai.bidding_end_date ASC
        """.trimIndent()

        private val FIND_ALL_ACTIVE = """
                SELECT 
                    ai.id,
                    ai.description,
                    ai.category,
                    ai.purchase_date,
                    ai.bidding_end_date,
                    ai.starting_price,
                    COALESCE(
                        (SELECT b.bid_price
                         FROM bid b
                         WHERE b.fk_auction_item_id = ai.id
                         AND b.is_deleted = false
                         ORDER BY b.bid_time DESC
                         LIMIT 1
                        ), ai.starting_price
                    ) AS current_price
                FROM 
                    auction_item ai
                WHERE 
                    ai.bidding_end_date > CURRENT_DATE
                ORDER BY 
                    ai.bidding_end_date ASC          
        """.trimIndent()


        private val INSERT_AUCTION = """
           INSERT INTO auction_item 
               (id, external_id, description, category, purchase_date, purchase_price, starting_price, minimum_raise, bidding_end_date, created_at, updated_at) 
           VALUES 
               (?, ?, ?, ?, ?, ?, ? ,? ,NOW() + interval '3' month, NOW(), NOW()) 
        """.trimIndent()

        private val GET_ITEM = """
            SELECT 
                ai.id,
                ai.external_id,
                ai.description,
                ai.category,
                ai.purchase_date,
                ai.purchase_price,
                ai.bidding_end_date,
                ai.starting_price,
                ai.minimum_raise,
                ai.is_transferred,
                ai.created_at,
                ai.updated_at
            FROM 
                auction_item ai
            WHERE 
                ai.id = ?
        """.trimIndent()

        private val UPDATE_AUCTION_ITEM = """
            UPDATE auction_item 
            SET 
                external_id = ?, 
                description = ?, 
                category = ?, 
                purchase_date = ?, 
                purchase_price = ?, 
                bidding_end_date = ?, 
                starting_price = ?,
                minimum_raise = ?,
                updated_at = NOW()
            WHERE id = ? and is_transferred = false
        """.trimIndent()

        private val RENEW_EXPIRED = """
            UPDATE auction_item ai
            SET 
                bidding_end_date = CURRENT_DATE + INTERVAL '30 days',
                times_renewed = times_renewed + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE 
                bidding_end_date < CURRENT_DATE
                AND is_transferred = FALSE
                AND NOT EXISTS (
                    SELECT 1 
                    FROM bid b 
                    WHERE b.fk_auction_item_id = ai.id
                )""".trimIndent()
    }

    fun findFrontPageItems() = db.query(FIND_ALL_ACTIVE) { rs, _ -> toFrontPageItem(rs) }

    fun findAllAdminPageItems() = db.query(FIND_ALL_ADMIN) { rs, _ -> toAdminItem(rs) }

    fun findById(id: String): AuctionItemRecord? = runCatching {
        db.queryForObject(GET_ITEM, { rs, _ -> toAuctionItem(rs) }, id.toUUID())
    }.getOrNull()

    fun addAuctionItem(item: NewAuctionItem): Either<Exception, String> = try {
        val id = UUID.randomUUID()
        db.update(
            INSERT_AUCTION,
            id,
            item.externalId,
            item.description,
            item.category,
            item.purchaseDate,
            item.purchasePrice,
            item.startingPrice,
            item.minimumRaise
        )

        log.info { "Created auction item ($id): $item" }

        id.toString().right()
    } catch (e: DuplicateKeyException) {
        val error = "Can't create auction item. Item already exists with external id ${item.externalId}."
        log.error(e) { error }
        Exception(error).left()
    } catch (e: DataAccessException) {
        log.error(e) { "Failed to create auction item: $item" }
        Exception("Failed to add new auction item ${e.message}").left()
    }

    fun updateAuctionItem(itemId: String, request: UpdateAuctionItemRequest): Either<Exception, AuctionItemRecord> {
        val uuid = try {
            UUID.fromString(itemId)
        } catch (_: IllegalArgumentException) {
            return Exception("Invalid UUID format: $itemId").left()
        }

        return try {
            val numUpdated = db.update(
                UPDATE_AUCTION_ITEM,
                request.externalId,
                request.description,
                request.category,
                request.purchaseDate,
                request.purchasePrice,
                request.biddingEndDate,
                request.startingPrice,
                request.minimumRaise,
                uuid
            )

            if (numUpdated == 0) {
                return Exception("Auction item not found").left()
            }

            log.info { "Updated auction item ($itemId)" }
            findById(itemId)!!.right()
        } catch (e: DataAccessException) {
            log.error(e) { "Failed to update auction item: $itemId" }
            Exception("Failed to update auction item: ${e.message}").left()
        }
    }

    fun renewExpiredAuctions() = db.update(RENEW_EXPIRED)

    fun isTransferred(itemId: String) =
        db.queryForObject("SELECT is_transferred FROM auction_item WHERE id = ?", Boolean::class.java, itemId)

}