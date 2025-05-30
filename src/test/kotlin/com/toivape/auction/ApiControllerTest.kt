package com.toivape.auction

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val auctionDao: AuctionDao,
    @Autowired private val objectMapper: ObjectMapper,
) {

    private val item = NewAuctionItem(
        externalId = "c22c60e8-2268-4c78-ac2f-4110ca5f169c",
        description = "OnePlus 13 5G puhelin, 512/16 Gt",
        category = "Phone",
        purchaseDate = LocalDate.of(2023, 2, 13),
        purchasePrice = BigDecimal("1149.00"),
        startingPrice = 100,
        minimumRaise = 1
    )

    private fun postItem(item: NewAuctionItem) =
        mvc.post("/api/auctionitems") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(item)
        }

    @Test
    fun `New auction item is added to database`() {
        val targetDescription = "My new phone 123"
        val newItem = item.copy(description = targetDescription)
        postItem(newItem).andExpect { status { isCreated() } }

        val dbItem = auctionDao.findFrontPageItems().find { it.description == targetDescription }
        withClue("Auction item with description ${item.description} should exist in database") {
            dbItem.shouldNotBeNull()
        }
    }

    @Test
    fun `New auction item has invalid data`() {
        val newItem = item.copy(externalId = null, description = null)
        postItem(newItem).andExpect {
            status { isBadRequest() }
            content { string(Matchers.containsString("description is mandatory")) }
            content { string(Matchers.containsString("externalId is mandatory")) }
        }
    }

    @Test
    fun `Adding auction item with duplicate external id fails`() {
        val newItem1 = item.copy(externalId = "c22c60e8-2268-4c78-ac2f-4110ca5f169d")
        postItem(newItem1).andExpect { status { isCreated() } }
        postItem(newItem1).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `Get auction item`() {
        mvc.get("/api/auctionitems/d1d018fe-cc1b-4f9c-9d53-bc8f5dd9b515")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString(",\"currentPrice\":290")) }
            }
    }

    @Test
    fun `Auction item not found`() {
        mvc.get("/api/auctionitems/01951e70-fd92-78b3-92ab-d2f92e0586ba")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `Get auction item with invalid id`() {
        mvc.get("/api/auctionitems/bad-uuid")
            .andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `Get the latest bid of auction item`() {
        mvc.get("/api/auctionitems/d1d018fe-cc1b-4f9c-9d53-bc8f5dd9b515")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString(",\"currentPrice\":290")) }
                content { string(Matchers.containsString("\"id\":\"cf9e1c37-3647-4ad4-9539-23f592a32597\"")) }
            }
    }

    @Sql(statements = ["INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('8e61bd74-b109-4bac-8ad3-552e3d3451df','8c031a7a-6c3f-411c-85b6-35a97a61da6b','Apple MagSafe -laturi 25 W (1 m) (MX6X3)', 'Phone accessories', '2024-06-06', '49.99',  NOW() + interval '3' month, 12, 1)"])
    @Test
    fun `Get the latest bid of auction item when there are no bids`() {
        mvc.get("/api/auctionitems/8e61bd74-b109-4bac-8ad3-552e3d3451df")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString(",\"currentPrice\":12")) }
                content { string(Matchers.containsString("\"bids\":[]")) }
            }
    }

    @Test
    fun `Get the latest bid for non-existing auction item`() {
        mvc.get("/api/auctionitems/01951e70-fd92-78b3-92ab-d2f92e0586ba")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `Get the latest bid with invalid auction item`() {
        mvc.get("/api/auctionitems/bad-uuid")
            .andExpect {
                status { isBadRequest() }
            }
    }
}