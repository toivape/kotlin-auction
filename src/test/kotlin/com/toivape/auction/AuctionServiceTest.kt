package com.toivape.auction

import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class AuctionServiceTest(@Autowired val auctionService: AuctionService) {

    @Test
    fun `should return auction item with bids, latest bid first`() {
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        auctionService.getAuctionItem(auctionItemId).fold(
            { _ -> fail("Failed to get item with id: $auctionItemId") },
            { item ->
                // Bids are ordered latest first
                item.id shouldBe auctionItemId
                item.bids.first().id shouldBe "7f0c311d-2f02-4562-a5e6-254908568f8b"
                item.bids.first().bidPrice shouldBe 170
                item.bids.last().id shouldBe "75467def-b8cf-44dd-89a6-9fc0aa1a010f"
                item.bids.last().bidPrice shouldBe 150
                item.description shouldBe "Apple iPhone 15 Pro Max 512 Gt -puhelin, sinititaani (MU7F3)"
                item.currentPrice shouldBe 170
            }
        )
    }

    @Test
    fun `Deleted bids should not be fetched`(){
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        auctionService.getAuctionItem(auctionItemId).fold(
            { _ -> fail("Failed to get item with id: $auctionItemId") },
            { item ->
                item.bids.size shouldBe 5
                item.bids.find { it.id == "b780e365-1628-4755-803d-87ec1eccd8e8" }.shouldBeNull()
            }
        )
    }

    @Test
    fun `should return only auction item details if there are no bids`() {
        val auctionItemId = "76bce495-219d-4632-a0bb-3e2977b7ae83"
        auctionService.getAuctionItem(auctionItemId).fold(
            { _ -> fail("Failed to get item with id: $auctionItemId") },
            { item ->
                item.id shouldBe auctionItemId
                item.description shouldBe "Apple 96 W USB-C-virtalähde (MX0J2)"
                item.currentPrice shouldBe 7
                item.bids.shouldBeEmpty()
            })
    }

    @Test
    fun `minimumRaise is set if it is missing from the message`(){
        val newItem = NewAuctionItem(
            externalId = UUID.randomUUID().toString(),
            description = "Test Item for minimumRaise",
            category = "Test",
            purchaseDate = LocalDate.now().minusDays(30),
            purchasePrice = BigDecimal("100.00"),
            startingPrice = 50,
            minimumRaise = 0 // Setting to 0 which should trigger automatic setting
        )

        val result = auctionService.addAuctionItem(newItem)
        result.fold(
            { _ -> fail("Failed to add item with id: ${newItem.externalId}") },
            { item -> {
                item.externalId shouldBe newItem.externalId
                item.minimumRaise shouldBe 5
                }
            }
        )
    }

    @Sql(statements = ["INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('0196cdcf-e2e4-7247-bad7-4db5fb68af56','0196cdcf-e2e4-742d-9943-0c4db7fd3517',' Jabra Evolve2 65 Stereo', 'Headphones', '2024-06-06', '198.99',  NOW() + interval '1' month, 100, 5)",
        "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196cdcf-e2e4-773a-b7b1-d0a2b44e4322','0196cdcf-e2e4-7247-bad7-4db5fb68af56', 100, 'bidder-1st@toivape.com','2025-02-16 13:000:00')",
        "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196cdcf-e2e4-7929-b5e9-81b25b1d9867','0196cdcf-e2e4-7247-bad7-4db5fb68af56', 105, 'bidder-2nd@toivape.com','2025-02-16 13:10:00')",
        "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196cdcf-e2e4-7d2c-9570-1b4b9ff03900','0196cdcf-e2e4-7247-bad7-4db5fb68af56', 110, 'bidde-3rd@toivape.com','2025-02-16 13:20:00')",
    ])
    @Test
    fun `Auction item current price the price of latest bid`(){
        val auctionItemId = "0196cdcf-e2e4-7247-bad7-4db5fb68af56"
        auctionService.getAuctionItem(auctionItemId).fold(
            { _ -> fail("Failed to get item with id: $auctionItemId") },
            { item ->
                item.currentPrice shouldBe 110
            })

    }

    @Sql(statements = ["INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('0195dbb6-d239-7f6e-9e76-d4e3811523e8','0195dbb6-d239-70da-a1e4-05600380c65a','Apple Thunderbolt 5 Pro ‑kaapeli ', 'Computer accessories', '2024-08-22', '78.99 ',NOW() + interval '14' day, 22, 1)"])
    @Test
    fun `Auction item current price is starting price when there are no bids`(){
        val auctionItemId = "0195dbb6-d239-7f6e-9e76-d4e3811523e8"
        auctionService.getAuctionItem(auctionItemId).fold(
            { _ -> fail("Failed to get item with id: $auctionItemId") },
            { item ->
                item.currentPrice shouldBe item.startingPrice
            })
    }
}