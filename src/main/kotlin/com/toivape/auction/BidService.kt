package com.toivape.auction

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

class ConcurrentBidException(message: String) : Exception(message)

class DuplicateBidException(message: String) : Exception(message)

class ExpiredException(message: String) : Exception(message)

class InvalidRaiseException(message: String) : Exception(message)

private val log = KotlinLogging.logger {}

@Service
class BidService(
    private val bidDao: BidDao,
    private val auctionDao: AuctionDao,
    private val auctionService: AuctionService
) {

    @Transactional
    fun addBid(
        auctionItemId: String,
        bidderEmail: String,
        bidAmount: Int,
        lastBidId: String
    ): Either<Exception, Bid> {
        val auctionItem: AuctionItem = auctionService.getAuctionItem(auctionItemId).getOrElse {
            return Exception("Auction item not found").left()
        }

        return validateBid(auctionItem, bidderEmail, lastBidId, bidAmount).flatMap {
            bidDao.addBid(auctionItemId, bidderEmail, bidAmount).flatMap {
                getLatestBid(auctionItemId)
            }
        }
    }

    private fun validateBid(
        auctionItem: AuctionItem,
        bidderEmail: String,
        lastBidId: String,
        bidAmount: Int
    ): Either<Exception, Boolean> {
        return validateAuctionState(auctionItem)
            .flatMap { validateBidAmount(auctionItem, bidAmount) }
            .flatMap { validateBidSequence(auctionItem, lastBidId) }
            .flatMap { validateBidder(auctionItem, bidderEmail) }
    }

    private fun validateAuctionState(auctionItem: AuctionItem): Either<Exception, Boolean> {
        // Auction item must be open (not expired or transferred)
        if (auctionItem.isExpired() || auctionItem.isTransferred) {
            return ExpiredException("Auction has finished for this item.").left()
        }
        return true.right()
    }

    private fun validateBidAmount(auctionItem: AuctionItem, bidAmount: Int): Either<Exception, Boolean> {
        // Bid amount must be more than the current price plus minimum raise unless
        // this is the first bid, then bid amount can be the current price
        val existingBids = auctionItem.bids
        val minimumBid = if (existingBids.isEmpty()) {
            auctionItem.currentPrice
        } else {
            existingBids.first().bidPrice + auctionItem.minimumRaise
        }

        if (bidAmount < minimumBid) {
            return InvalidRaiseException("Minimum bid is $minimumBid.").left()
        }
        return true.right()
    }

    private fun validateBidSequence(auctionItem: AuctionItem, lastBidId: String): Either<Exception, Boolean> {
        // If last bid is empty then existing bid list must be empty
        // Potential time-of-check to time-of-use vulnerability
        val existingBids = auctionItem.bids
        if (existingBids.isNotEmpty() && lastBidId.isEmpty()) {
            return ConcurrentBidException("This is no longer the first bid").left()
        }

        // Check that last bid id is still the last bid id
        if (existingBids.isNotEmpty() && lastBidId != existingBids.first().id) {
            return ConcurrentBidException("Other user has made a simultaneous bid").left()
        }
        return true.right()
    }

    private fun validateBidder(auctionItem: AuctionItem, bidderEmail: String): Either<Exception, Boolean> {
        // Check that the previous bidder is different from the current bidder
        val existingBids = auctionItem.bids
        if (existingBids.isNotEmpty() && existingBids.first().bidderEmail == bidderEmail) {
            return DuplicateBidException("You cannot bid twice in a row").left()
        }
        return true.right()
    }

    fun getLatestBid(auctionItemId: String): Either<Exception, Bid> {
        return bidDao.findByAuctionItemIdLatestFirst(auctionItemId).flatMap { bids ->
            bids.firstOrNull()?.right() ?: Exception("No bids found for item $auctionItemId").left()
        }
    }

    @Transactional
    fun removeBid(itemId: String, bidId: String): Either<Exception, Unit> {

        if (auctionDao.isTransferred(itemId) == true) {
            log.error { "Item $itemId has been transferred. Can not update." }
            return Exception("Auction has finished for this item. Bid can not be deleted.").left()
        }

        return bidDao.removeBid(itemId, bidId)
    }
}