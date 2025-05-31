package com.toivape.auction

import arrow.core.getOrElse
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post

@WithMockUser(username = "test-admin@toivape.com", roles = ["ADMIN"])
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UserDeletesBidTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val auctionService: AuctionService,
) {

    /**
     * Helper method to get auction item by ID
     */
    private fun getAuctionItem(itemId: String): AuctionItem =
        auctionService.getAuctionItem(itemId).getOrElse { error ->
            fail("Could not get auction item $itemId: ${(error as? Throwable)?.message ?: error}")
        }

    /**
     * Helper method to delete a bid
     */
    private fun deleteBid(itemId: String, bidId: String): ResultActionsDsl {
        return mvc.post("/admin/edit/$itemId/bids/$bidId") {
            with(csrf())
        }
    }

    /**
     * Helper method to assert successful bid deletion
     */
    private fun assertBidDeleteSuccess(result: ResultActionsDsl) {
        result.andExpect {
            status { isOk() }
            content { string(containsString("Bid removed successfully")) }
        }
    }

    /**
     * Helper method to assert bid deletion failure
     */
    private fun assertBidDeleteFailure(result: ResultActionsDsl, errorMessage: String) {
        result.andExpect {
            status { isOk() }
            content { string(containsString(errorMessage)) }
        }
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('0196c959-58d5-78e9-991e-8fecec8b3633','0196c959-58d5-7ba8-b393-9379d7fff78b','Apple iPad Air 11\" M3 128 Gt WiFi', 'Tablet', '2024-06-06', '739.00',  NOW() + interval '1' month, 200, 5)",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196c959-58d5-7495-b48c-912cea40871c','0196c959-58d5-78e9-991e-8fecec8b3633', 200, 'bidder-1st@toivape.com','2025-02-16 13:000:00')",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196c959-58d5-7ac5-9c1b-aa43e65ba787','0196c959-58d5-78e9-991e-8fecec8b3633', 205, 'bidder-2nd@toivape.com','2025-02-16 13:10:00')",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('0196c959-58d5-7468-969c-f78e34d9189a','0196c959-58d5-78e9-991e-8fecec8b3633', 210, 'bidde-3rd@toivape.com','2025-02-16 13:20:00')",
        ]
    )
    @Test
    fun `Bid is not found after it has been deleted`() {
        val auctionItemId = "0196c959-58d5-78e9-991e-8fecec8b3633"

        // Find and delete the first bid.
        val auctionItemBefore = getAuctionItem(auctionItemId)
        auctionItemBefore.currentPrice shouldBe 210
        val bidIdToDelete = auctionItemBefore.bids.last().id.also {
            it shouldBe "0196c959-58d5-7495-b48c-912cea40871c"
        }

        val result = deleteBid(auctionItemId, bidIdToDelete)
        assertBidDeleteSuccess(result)

        // Bid should no longer be found and currentPrice should have changed
        val auctionItemAfter = getAuctionItem(auctionItemId)
        auctionItemAfter.bids.find { it.id == bidIdToDelete }.shouldBeNull()
        auctionItemAfter.currentPrice shouldBe 210
    }

    @Test
    fun `Attempting to remove non-existent bid returns appropriate error`() {
        val auctionItemId = "b030b21b-73f9-40ff-8518-4a45f2c9b769"
        val nonExistentBidId = "11111111-1111-1111-1111-111111111111"

        // Try to remove a non-existent bid
        val result = deleteBid(auctionItemId, nonExistentBidId)
        assertBidDeleteFailure(result, "<span>Failed to remove bid: Bid not found 11111111-1111-1111-1111-111111111111</span>")
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('83b4f86c-6c9b-48e0-9f91-f52d753f4ea8', 'test-delete-bid-item', 'Test Item for Bid Deletion', 'Test Category', '2023-10-15', '99.99', '2024-12-01', 10, 1)",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('c82e1e6a-9c5f-4803-a43a-103e02210e5d', '83b4f86c-6c9b-48e0-9f91-f52d753f4ea8', 15, 'delete.test@toivape.com', '2023-11-10 13:45:00')"
        ]
    )
    @Test
    fun `Delete a bid successfully`() {
        val itemId = "83b4f86c-6c9b-48e0-9f91-f52d753f4ea8"
        val bidId = "c82e1e6a-9c5f-4803-a43a-103e02210e5d"

        // Verify the bid exists before deletion
        getAuctionItem(itemId).bids.let {
            it.size shouldBe 1
            it[0].id shouldBe bidId
        }

        // Perform the deletion request
        val result = deleteBid(itemId, bidId)
        assertBidDeleteSuccess(result)

        withClue("Bid was not deleted from the database") {
            getAuctionItem(itemId).bids.shouldBeEmpty()
        }
    }

    @Sql(
        statements = [
            "INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise, is_transferred) VALUES ('9bb7b5b6-5ef2-4542-abc8-1b7938c05cb4', 'e31f2816-6bde-4990-866f-e235a4f9c3e5', 'Transferred Test Item', 'Test Category', '2022-10-15', '99.99', '2025-05-01', 10, 1, true)",
            "INSERT INTO bid (id, fk_auction_item_id, bid_price, bidder_email, bid_time) VALUES ('040804ff-c8de-44c9-ba42-34ed94e32afa', '9bb7b5b6-5ef2-4542-abc8-1b7938c05cb4', 0, 'test.bidder@toivape.com', '2025-04-30 12:00:00')"
        ]
    )
    @Test
    fun `Bid can not be deleted if item has been transferred`(){
        val itemId = "9bb7b5b6-5ef2-4542-abc8-1b7938c05cb4"
        val bidId = "040804ff-c8de-44c9-ba42-34ed94e32afa"

        // Perform the deletion request
        val result = deleteBid(itemId, bidId)
        assertBidDeleteFailure(result, "Auction has finished for this item. Bid can not be deleted.")

        withClue("Bid should not have been deleted from the database") {
            getAuctionItem(itemId).bids.shouldNotBeEmpty()
        }
    }
}