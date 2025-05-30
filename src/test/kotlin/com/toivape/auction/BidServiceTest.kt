package com.toivape.auction

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class BidServiceTest {

    @Autowired
    private lateinit var bidService: BidService

    @Autowired
    private lateinit var bidDao: BidDao

    @Test
    fun `should return error when auction item not found`() {
        bidService.addBid("non-existent-id", "test@example.com", 10, "").apply {
            shouldBeLeft()
            value.message.shouldBe("Auction item not found")
        }
    }

    @Test
    fun `first bid price should be at least the starting price`() {
        val auctionItemId = "76bce495-219d-4632-a0bb-3e2977b7ae83"
        bidService.addBid(auctionItemId, "test@example.com", 7, "").shouldBeRight()
        bidDao.findBids(auctionItemId).apply {
            shouldHaveAtLeastSize(1)
            first().bidPrice.shouldBe(7)
        }
    }

    @Test
    fun `should return error when last bid id is empty but bids exist`() {
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        bidService.addBid(auctionItemId, "test@example.com", 175, "").apply {
            shouldBeLeft()
            value.message.shouldBe("This is no longer the first bid")
        }
    }

    @Test
    fun `should return error when last bid id is not the last bid id`() {
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        val lastBidId = "75467def-b8cf-44dd-89a6-9fc0aa1a010f"
        bidService.addBid(auctionItemId, "test@example.com", 175, lastBidId).apply {
            shouldBeLeft()
            value.message.shouldBe("Other user has made a simultaneous bid")
        }
    }

    @Test
    fun `should add bid successfully`() {
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        val lastBidId = "7f0c311d-2f02-4562-a5e6-254908568f8b"

        bidService.addBid(auctionItemId, "test@example.com", 175, lastBidId).shouldBeRight()

        bidDao.findBids(auctionItemId).apply {
                shouldHaveSize(6)
                first().bidPrice shouldBe 175
            }
    }

    @Test
    fun `should return exception when auction item not found`() {
        bidService.getLatestBid("2fd4f12b-f71e-4afe-97bb-45996f3104a2").shouldBeLeft()
    }

    @Test
    fun `Should return error when bid is less than previous bid plus minimum raise`(){
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        val lastBidId = "7f0c311d-2f02-4562-a5e6-254908568f8b"

        bidService.addBid(auctionItemId, "test@example.com", 172, lastBidId).apply {
            shouldBeLeft()
            value.message.shouldBe("Minimum bid is 175.")
        }
    }

}