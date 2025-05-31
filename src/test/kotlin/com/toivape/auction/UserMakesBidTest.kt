package com.toivape.auction

import arrow.core.getOrElse
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post

@WithMockUser(username = "test-user@toivape.com", roles = ["USER"])
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserMakesBidTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val bidDao: BidDao,
    @Autowired private val bidService: BidService,
) {
    /**
     * Helper method to make a bid POST request
     *
     * @param auctionItemId ID of the auction item
     * @param bidRequest The bid request object
     * @return ResultActions for further assertions
     */
    private fun makeBid(auctionItemId: String, bidRequest: BidRequest): ResultActionsDsl {
        return mvc.post("/api/auctionitems/$auctionItemId/bids") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(bidRequest)
        }
    }

    /**
     * Helper method to assert that a bid was successful
     */
    private fun assertBidSuccess(result: ResultActionsDsl, expectedBidPrice: Int? = null) {
        result.andExpect {
            status { isCreated() }
            if (expectedBidPrice != null) {
                content { string(Matchers.containsString("\"bidPrice\":$expectedBidPrice")) }
            }
        }
    }

    /**
     * Helper method to assert that a bid failed with a specific error message
     */
    private fun assertBidFailure(result: ResultActionsDsl, errorMessage: String? = null) {
        result.andExpect {
            status { isBadRequest() }
            if (errorMessage != null) {
                content { string(Matchers.containsString(errorMessage)) }
            }
        }
    }

    /**
     * Helper method to get the latest bid for an auction item
     */
    private fun getLatestBid(auctionItemId: String): Bid {
        return bidService.getLatestBid(auctionItemId).getOrElse {
            fail("Unable to get latest bid for auction item: $auctionItemId")
        }
    }

    @Sql(statements = ["INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('01951f4a-48ac-7c5f-8db1-1ef9efc5e10d','01951f4a-48ac-7ece-acac-f7c443787795','Ubiquiti UniFi 7 Pro -WiFi-tukiasema', 'Network', '2023-10-06', '249.99',  NOW() + interval '3' month, 42, 1)"])
    @Test
    fun `User makes a bid as a first bidder`() {
        val auctionItemId = "01951f4a-48ac-7c5f-8db1-1ef9efc5e10d"
        val bidRequest = BidRequest(amount = 42, lastBidId = "")

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidSuccess(result)

        // Make sure the bid is in the database
        bidDao.findBids(auctionItemId).apply {
            withClue("There should be one bid for auction item $auctionItemId") {
                size shouldBe 1
                // The Value of the first bid should be the bid amount
                first().bidPrice shouldBe 42
            }
        }
    }

    @Test
    fun `User places a bid when there are existing bids`() {
        val auctionItemId = "b2ce636c-9d81-4ba4-bab8-f2ffaa91293c"
        val bidRequest = BidRequest(amount = 230, lastBidId = getLatestBid(auctionItemId).id)

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidSuccess(result, 230)

        // Make sure the bid is in the database
        bidDao.findBids(auctionItemId).apply {
            shouldHaveSize(4)
            first().bidPrice shouldBe bidRequest.amount
        }
    }

    @Test
    fun `User makes a bid which is less than the minimum raise`() {
        val auctionItemId = "b2ce636c-9d81-4ba4-bab8-f2ffaa91293c"
        val bidRequest = BidRequest(amount = 5, lastBidId = getLatestBid(auctionItemId).id)

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidFailure(result, "Minimum bid is 240.")
    }

    @Test
    fun `User is not able to bid on an expired auction item`() {
        val auctionItemId = "271aebdf-b53d-4748-8dce-a67f6ece3399"
        val bidRequest = BidRequest(amount = 5, lastBidId = "f40d0d08-8f37-4e60-bb65-54207c98e015")

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidFailure(result, "Auction item is expired")
    }

    @Test
    fun `New bid request has invalid auction item id`() {
        val auctionItemId = "01951f4a-48ac-7c5f-8db1-1ef9efc5e10d-bad"
        val bidRequest = BidRequest(amount = 1, lastBidId = "")

        val result = makeBid(auctionItemId, bidRequest)

        assertBidFailure(result)
    }

    @Test
    fun `Users makes a bid with invalid data`() {
        val auctionItemId = "01951f4a-48ac-7c5f-8db1-1ef9efc5e10d"
        val bidRequest = BidRequest(amount = null, lastBidId = "invalid-uuid")

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidFailure(result)
    }

    @Test
    fun `Users makes duplicate bid and gets error`() {
        val auctionItemId = "271e446e-5f83-44ef-9a64-cbd1139d26c9"
        val bidRequest = BidRequest(amount = 220, lastBidId = "04c4656a-5f4c-40b2-aff1-07f367bfcfc2")

        val result = makeBid(auctionItemId, bidRequest)
            .andDo { print() }

        assertBidFailure(result, "You cannot bid twice in a row")
    }
}