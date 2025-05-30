package com.toivape.auction

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

fun BidDao.findBids(auctionItem:String): List<Bid> =
    findByAuctionItemIdLatestFirst(auctionItem).getOrElse { fail("Unable to find bids for auction item: $auctionItem") }


@SpringBootTest
@ActiveProfiles("test")
class BidDaoTest(@Autowired val dao: BidDao) {

    @Test
    fun `List bids for a specific auction item`() {
        dao.findBids("0195dbb6-d239-74db-b9c1-a434ecf33d39").apply {
            shouldHaveSize(3)
            first().id shouldBe "0195dbb6-d239-7bae-9dc5-42f13d6b7fb9"
            last().id shouldBe "0195dbb6-d239-757c-93ae-2ce86b8718db"
        }
    }

    @Test
    fun `Auction item has no bids`() {
        dao.findBids("b6579e11-d0ef-4a21-a597-58961ddb801c").shouldBeEmpty()
    }

}