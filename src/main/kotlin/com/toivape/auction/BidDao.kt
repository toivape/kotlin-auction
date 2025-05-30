package com.toivape.auction

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.toivape.auction.Bid.Companion.toBid
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.query
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*

data class Bid(
    val id: String,
    val auctionItemId: String,
    val bidPrice: Int,
    val bidderEmail: String,
    val bidTime: LocalDateTime
) {
    companion object {
        fun toBid(rs: ResultSet) = Bid(
            id = rs.getString("id"),
            auctionItemId = rs.getString("fk_auction_item_id"),
            bidPrice = rs.getInt("bid_price"),
            bidderEmail = rs.getString("bidder_email"),
            bidTime = rs.getTimestamp("bid_time").toLocalDateTime()
        )
    }
}

private val log = KotlinLogging.logger {}

@Repository
class BidDao(val db: JdbcTemplate) {
    companion object {

        private val FIND_BIDS_LATEST_FIRST = """
            SELECT 
                id,
                fk_auction_item_id,
                bid_price,
                bidder_email,
                bid_time
            FROM 
                bid
            WHERE 
                fk_auction_item_id = ?
                and is_deleted = false
            ORDER BY 
                bid_time DESC         
        """.trimIndent()

        private val CREATE_BID = """
            INSERT INTO bid 
            (id, fk_auction_item_id, bid_price, bidder_email, bid_time) 
            VALUES 
            (?, ?, ?, ?, CURRENT_TIMESTAMP)
        """.trimIndent()
    }

    fun findByAuctionItemIdLatestFirst(itemId: String): Either<Exception, List<Bid>> =
        try {
            db.query(FIND_BIDS_LATEST_FIRST, itemId) { rs, _ -> toBid(rs) }.right()
        } catch (e: DataAccessException) {
            log.error(e) { "Failed to get bids for item $itemId" }
            Exception("Failed to get bids for item $itemId").left()
        }


    fun addBid(auctionItemId: String, bidderEmail: String, amount: Int): Either<Exception, Unit> = try {
        val id = UUID.randomUUID()
        db.update(
            CREATE_BID,
            id,
            auctionItemId,
            amount,
            bidderEmail
        )

        log.info { "Created bid ($id): amount: $amount, bidder: $bidderEmail, auctionItem: $auctionItemId" }

        Unit.right()
    } catch (e: DataAccessException) {
        log.error(e) { "Failed to create bid: amount: $amount, bidder: $bidderEmail, auctionItem: $auctionItemId\"" }
        Exception("Failed to add new auction bid").left()
    }

    fun removeBid(itemId: String, bidId: String): Either<Exception, Unit> = try {
        if (db.update("UPDATE bid SET is_deleted = true WHERE id = ? AND fk_auction_item_id = ?", bidId, itemId) == 0) {
            NotFoundException("Failed to remove bid: Bid not found $bidId").left()
        } else {
            Unit.right()
        }
    } catch (e: DataAccessException) {
        log.error(e) { "Failed to remove bid: bidId: $bidId, auctionItem: $itemId" }
        Exception("Failed to remove bid: ${e.message}").left()
    }
}