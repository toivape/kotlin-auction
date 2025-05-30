package com.toivape.auction

import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class AuctionDaoTest(@Autowired val dao: AuctionDao) {

    @Test
    fun `Open auction items list does not contain expired auction item`() {
        val expiredAuctionId = UUID.fromString("4c36b5ec-eebc-4881-8e18-edc9c84a0b49").toString()
        dao.findFrontPageItems().apply {
            shouldHaveAtLeastSize(6)
            none { it.id == expiredAuctionId } shouldBe true
        }
    }

    @Test
    fun `Auctions ending soonest are shown first`() {
        dao.findFrontPageItems().apply {
            for (i in 1 until size) {
                get(i - 1).biddingEndDate shouldBeLessThanOrEqualTo get(i).biddingEndDate
            }
        }
    }

    @Test
    fun `List all items for admin view`() {
        dao.findAllAdminPageItems().apply {
            shouldHaveAtLeastSize(7)
        }
    }

    @Test
    fun `Front page items should have correct current price`() {
        val frontPageItems = dao.findFrontPageItems()

        // item with bids
        frontPageItems.find { it.id == "d1d018fe-cc1b-4f9c-9d53-bc8f5dd9b515"}!!.currentPrice shouldBe 290

        // item without bids
        frontPageItems.find { it.id == "4dca57db-23ca-4a8e-a63b-20b6f4d2a910"}!!.currentPrice shouldBe 10
    }

    @Test
    fun `Admin page should show correct current price`() {
        val adminPageItems = dao.findAllAdminPageItems()

        // item with bids
        adminPageItems.find { it.id == "d1d018fe-cc1b-4f9c-9d53-bc8f5dd9b515"}!!.currentPrice shouldBe 290

        // item without bids
        adminPageItems.find { it.id == "4dca57db-23ca-4a8e-a63b-20b6f4d2a910"}!!.currentPrice shouldBe 10
    }
}