package com.toivape.auction

import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val auctionService: AuctionService
) {

    private fun getAuctionItem(id: String): AuctionItem =
        auctionService.getAuctionItem(id).getOrNull() ?: error("Auction item $id was not found in database")


    @Test
    fun `Show admin page with auction items`() {
        mvc.get("/admin")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("Satechi USB-C Multi-Port Adapter 4K Gigabit Ethernet V2")) }
            }
    }

    @Test
    fun `Health check is successful`() {
        mvc.get("/healthCheck")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }

    @Test
    fun `Auction item edit pages is shown`() {
        mvc.get("/admin/edit/b030b21b-73f9-40ff-8518-4a45f2c9b769")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("Apple iPhone 15 Pro Max 512 Gt -puhelin, sinititaani (MU7F3)")) }
            }
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('73a3f76c-5c9b-48e0-9f91-f52d753f4ea7', 'test-ext-id-123', 'Test Item', 'Test Category', '2023-10-15', '99.99', '2024-12-01', 10, 1)",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('b72e1e6a-9c5f-4803-a43a-103e02210e5c', '73a3f76c-5c9b-48e0-9f91-f52d753f4ea7', 0, 'test.bidder@toivape.com', '2023-11-10 13:45:00')"
        ]
    )
    @Test
    fun `Update auction item successfully`() {
        val itemId = "73a3f76c-5c9b-48e0-9f91-f52d753f4ea7"
        val updatedValues = mapOf(
            "externalId" to "updated-ext-id-456",
            "description" to "Updated Test Item",
            "category" to "Updated Category",
            "purchaseDate" to LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "biddingEndDate" to LocalDate.now().plusMonths(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "startingPrice" to "25",
            "purchasePrice" to "199.99",
            "minimumRaise" to "6",
        )

        mvc.post("/admin/edit/$itemId") {
            updatedValues.forEach { param(it.key, it.value) }
        }
            .andExpect {
                status { isOk() }
                model { attributeExists("success") }
                content { string(Matchers.containsString("Item updated successfully")) }
                updatedValues.forEach { content { string(Matchers.containsString(it.value)) } }
            }

        getAuctionItem(itemId).let {
            it.description shouldBe updatedValues["description"]
            it.category shouldBe updatedValues["category"]
            it.externalId shouldBe updatedValues["externalId"]
            it.purchaseDate.toString() shouldBe updatedValues["purchaseDate"]
            it.biddingEndDate.toString() shouldBe updatedValues["biddingEndDate"]
            it.startingPrice.toString() shouldBe updatedValues["startingPrice"]
            it.purchasePrice shouldBe updatedValues["purchasePrice"]
            it.minimumRaise.toString() shouldBe updatedValues["minimumRaise"]
        }
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('5b203a12-e429-472e-a0a0-9a8fbd4136d3', 'e2ebe555-0330-4744-9076-942bd477683c', 'Super Tablet 6000', 'Tablet', '2023-10-15', '99.99', '2044-12-01', 200, 10)",
        ]
    )
    @Test
    fun `auction item update fails validation`() {
        val itemId = "5b203a12-e429-472e-a0a0-9a8fbd4136d3"
        val updatedValues = mapOf(
            "externalId" to "updated-ext-id-456",
            "description" to "",
            "category" to "Updated Category",
            "purchaseDate" to LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "biddingEndDate" to LocalDate.now().plusMonths(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "startingPrice" to "-1",
            "purchasePrice" to "10199.99",
        )

        mvc.post("/admin/edit/$itemId") {
            updatedValues.forEach { param(it.key, it.value) }
        }
            .andExpect {
                status { isOk() }
                model { attributeExists("errors") }
                content { string(Matchers.containsString("<li>description is required</li>")) }
                content { string(Matchers.containsString("<li>startingPrice must be greater than or equal to 0</li>")) }
                content { string(Matchers.containsString("<li>minimumRaise is mandatory</li>")) }
            }
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise, is_transferred) VALUES ('221041da-244e-4124-985b-f4959719d50a', 'cecf069e-3684-4933-93f7-bec36df0ab9c', 'Transferred Test Item', 'Test Category', '2022-10-15', '99.99', '2025-05-01', 10, 1, true)",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('a0b19318-0207-4abf-97cd-45e65f78032c', '221041da-244e-4124-985b-f4959719d50a', 0, 'test.bidder@toivape.com', '2025-04-30 12:00:00')"
        ]
    )
    @Test
    fun `Transferred item can not be updated`() {
        val itemId = "221041da-244e-4124-985b-f4959719d50a"
        val updatedValues = mapOf(
            "externalId" to "cecf069e-3684-4933-93f7-bec36df0ab9c",
            "description" to "Updated Test Item",
            "category" to "Updated Category",
            "purchaseDate" to LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "biddingEndDate" to LocalDate.now().plusMonths(5).format(DateTimeFormatter.ISO_LOCAL_DATE),
            "startingPrice" to "25",
            "purchasePrice" to "199.99",
            "minimumRaise" to "6"
        )

        mvc.post("/admin/edit/$itemId") {
            updatedValues.forEach { param(it.key, it.value) }
        }
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("<span>Auction has finished for this item. Can not update.</span>")) }
            }
    }
}