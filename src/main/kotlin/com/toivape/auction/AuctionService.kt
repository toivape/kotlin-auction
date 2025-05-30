package com.toivape.auction

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestBody
import java.time.LocalDate
import java.time.LocalDateTime

data class AuctionItem(
    val id: String,
    val externalId: String,
    val description: String,
    val category: String,
    val purchaseDate: LocalDate,
    val purchasePrice: String,
    val biddingEndDate: LocalDate,
    val startingPrice: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val currentPrice: Int,
    val minimumRaise:Int,
    val isTransferred: Boolean,
    val bids: List<Bid>
){
    fun isExpired() = biddingEndDate.isBefore(LocalDate.now()) || isTransferred
}

private val log = KotlinLogging.logger {}

@Service
class AuctionService(private val auctionDao: AuctionDao, private val bidDao: BidDao) {

    @Transactional
    fun addAuctionItem(@Valid @RequestBody item: NewAuctionItem): Either<Exception, AuctionItem> {
        if (item.minimumRaise == 0){
            item.minimumRaise =
                when (item.startingPrice) {
                    in 0 until 100 -> 1
                    in 100 until 200 -> 5
                    in 200 until 300 -> 10
                    in 300 until 1000 -> 20
                    else -> 50
            }
            log.info { "Set new auction item ${item.externalId} minimumRaise to ${item.minimumRaise}" }
        }

        return auctionDao.addAuctionItem(item).flatMap { id ->
            getAuctionItem(id)
        }
    }

    fun getAuctionItem(auctionItemId: String): Either<Exception, AuctionItem> {
        val auctionItem = auctionDao.findById(auctionItemId) ?: return Exception("Auction item not found").left()
        return bidDao.findByAuctionItemIdLatestFirst(auctionItemId).flatMap { bids ->

            // Calculate the sum of bids directly from the fetched bids
            // Skip the last bid in the list (which is the first/oldest bid)
            val currentBidPrice = if (bids.isEmpty()) auctionItem.startingPrice else bids.first().bidPrice

            AuctionItem(
                id = auctionItem.id.toString(),
                externalId = auctionItem.externalId,
                description = auctionItem.description,
                category = auctionItem.category,
                purchaseDate = auctionItem.purchaseDate,
                purchasePrice = auctionItem.purchasePrice.toString(),
                biddingEndDate = auctionItem.biddingEndDate,
                startingPrice = auctionItem.startingPrice,
                createdAt = auctionItem.createdAt,
                updatedAt = auctionItem.updatedAt,
                currentPrice = currentBidPrice,
                minimumRaise = auctionItem.minimumRaise,
                isTransferred = auctionItem.isTransferred,
                bids = bids
            ).right()
        }
    }

    @Transactional
    fun updateAuctionItem(itemId: String, request: UpdateAuctionItemRequest): Either<Exception, AuctionItem> {
        log.info { "Updating auction item $itemId with $request" }

        if (auctionDao.isTransferred(itemId) == true){
            log.error { "Item $itemId has been transferred. Can not update." }
            return Exception("Auction has finished for this item. Can not update.").left()
        }

        return auctionDao.updateAuctionItem(itemId, request)
            .flatMap { _ ->
                // Get the updated item with bids to return
                getAuctionItem(itemId)
            }
    }

    @Transactional
    fun renewExpiredAuctions() {
        val numUpdated = auctionDao.renewExpiredAuctions()
        log.info { "Number of renewed auctions: $numUpdated" }
    }

    fun exportFinishedAuctions() {
        log.info { "Exporting finished auctions" }
        // TODO Find auctions which are expired and have bids, export them to originating system, and mark them exported by settings is_exported = true
        // TODO Export should go to a separate class which is responsible for exporting
    }
}