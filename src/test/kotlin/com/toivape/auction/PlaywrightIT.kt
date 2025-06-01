package com.toivape.auction

import arrow.core.getOrElse
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Page.WaitForSelectorOptions
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.fail
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
class PlaywrightIT(@Autowired val bidService: BidService, @Autowired val auctionService: AuctionService) {

    companion object {
        private const val AUCTION_ITEM_ID = "b030b21b-73f9-40ff-8518-4a45f2c9b769"

        // Add test user credentials
        private const val TEST_USERNAME = "dummy-user@toivape.com"
        private const val TEST_PASSWORD = "stork"
    }

    @LocalServerPort
    private var port: Int = 0

    private val baseUrl: String
        get() = "http://localhost:$port"

    private var playwright: Playwright? = null

    @BeforeAll
    fun setUp() {
        playwright = Playwright.create()
    }

    @AfterAll
    fun tearDown() {
        playwright?.close()
    }

    /**
     * Logs in a user and returns a browser context with the authenticated session
     */
    private fun Browser.login(): BrowserContext {
        val context = this.newContext()
        context.setDefaultTimeout(5000.0)  // Increased timeout for login
        val page = context.newPage()

        // Navigate to frontpage to trigger login
        page.navigate("$baseUrl/")

        // Fill in login form
        page.fill("input[name='username']", TEST_USERNAME)
        page.fill("input[name='password']", TEST_PASSWORD)

        // Submit the form
        page.click("button[type='submit']")

        // Wait for navigation to complete (should redirect to the homepage after login)
        page.waitForSelector("text=Happy bidding")
        log.info {"User logged in"}

        // Return the authenticated context that can be used for future requests
        return context
    }

    /**
     * Opens the front page with authentication
     */
    fun Browser.frontpage(): Page {
        val context = this.login()
        val page = context.newPage()
        page.navigate("$baseUrl/")
        return page
    }

    /**
     * Opens an auction item modal with authentication
     */
    fun Browser.openAuctionItemModal(itemId: String = AUCTION_ITEM_ID): Page {
        val page = frontpage()

        // Click Bid button to open auction item in modal
        page.locator("[data-bs-itemid='$itemId']").click()
        page.waitForSelector(".modal.show")
        return page
    }

    fun Page.placeBid(amount: String) {
        locator("#bid-amount").fill(amount)
        locator("#submit-bid").click()
    }

    private fun Page.takeScreenshot(path:String = "screenshot.png"){
        log.info{"takeScreenshot"}
        screenshot(Page.ScreenshotOptions()
            .setPath(Paths.get(path))
            .setFullPage(true))
    }

    @Test
    fun `Playwright can open front page`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.frontpage()
            page.title() shouldBe "Auction"
        }
    }

    @Test
    fun `Open auction item window with latest bid data`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.openAuctionItemModal()

            // Verify that auctionItemId and latestBidId are set correctly
            val auctionItemIdTag = page.locator("#item-id")
            auctionItemIdTag.getAttribute("value").shouldBe(AUCTION_ITEM_ID)

            val latestBid = bidService.getLatestBid(AUCTION_ITEM_ID).getOrElse { fail("Could not get latest bid") }
            val latestBidIdTag = page.locator("#last-bid-id")
            latestBidIdTag.getAttribute("value").shouldBe(latestBid.id)

            // Verify that bid history table is visible
            val bidHistoryTable = page.locator("#bid-history-table")
            bidHistoryTable.isVisible.shouldBeTrue()

            // Verify that there are rows in the bid history table
            val bidHistoryRows = page.locator("#bid-history-body tr")
            val rowCount = bidHistoryRows.count()
            rowCount.shouldBeGreaterThan(0)

            // Verify content of the first row (most recent bid)
            val firstRowCells = page.locator("#bid-history-body tr:first-child td")

            // Get all the cell contents from the first row
            val cellContents = (0 until firstRowCells.count()).map {
                firstRowCells.nth(it).textContent()
            }

            // The bid price in the 3rd column should match the latest bid price
            cellContents[2].shouldContain(latestBid.bidPrice.toString())

            // The bidder email in the 2nd column should match the latest bidder
            cellContents[1].shouldBe(latestBid.bidderEmail)

            // Check that the time cell contains some text (may be hard to match exactly due to formatting)
            cellContents[0].shouldNotBe("")
        }
    }

    @Test
    fun `Open auction item without any bids`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.openAuctionItemModal("ad0bc19f-79a6-45b7-978a-1b17fed94087")

            // Verify that auctionItemId is set correctly
            val auctionItemIdTag = page.locator("#item-id")
            auctionItemIdTag.getAttribute("value").shouldBe("ad0bc19f-79a6-45b7-978a-1b17fed94087")

            // Verify that latestBidId is empty
            val latestBidIdTag = page.locator("#last-bid-id")
            latestBidIdTag.getAttribute("value").shouldBe("")

            // Verify that last bid shows "-"
            val lastBidSpan = page.locator("#last-bid")
            lastBidSpan.textContent().shouldBe("-")

            // Verify that last bidder shows "-"
            val lastBidderSpan = page.locator("#last-bidder")
            lastBidderSpan.textContent().shouldBe("-")

            // Verify that bid list shows text "No bids yet"
            val bidHistoryRow = page.locator("#bid-history-body tr")
            bidHistoryRow.textContent().shouldContain("No bids yet")

            // Verify that the bid amount field is pre-filled and disabled
            val currentPrice = page.locator("#bid-current-price").textContent().toInt()
            val bidAmountInput = page.locator("#bid-amount")
            bidAmountInput.inputValue().shouldBe(currentPrice.toString())

            // Verify that submit button has text "I am first to bid!"
            val submitButton = page.locator("#submit-bid")
            submitButton.textContent().shouldBe("I am first to bid!")
        }
    }

    @Test
    fun `User makes simultaneous bid and gets error`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.openAuctionItemModal()

            // Make a simultaneous bid
            val latestBid = bidService.getLatestBid(AUCTION_ITEM_ID).getOrElse { fail("Could not get latest bid") }
            bidService.addBid(AUCTION_ITEM_ID, "test.bidder.bob@toivape.com", 175, latestBid.id).apply {
                shouldBeRight()
            }

            // Make bid with outdated lastBidId
            page.placeBid("180")

            // page.takeScreenshot()

            // Check if the alert box contains the correct error message
            page.waitForSelector("#error-alert:visible")
            val alertText = page.locator("#error-alert").textContent()
            alertText shouldBe "Other user has placed a bid"
        }
    }

    @Test
    fun `Attempt to make bid without bid amount shows error to user`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.openAuctionItemModal()

            // Make bid with empty amount
            page.placeBid("")

            //Check if the invalid-feedback contains the correct error message
            page.waitForSelector(".invalid-feedback:visible")
            val errorMessage = page.locator(".invalid-feedback").textContent()
            errorMessage shouldContain "Please enter a valid bid amount"
        }
    }

    @Sql(statements = ["INSERT INTO auction_item (id, external_id, description, category, purchase_date, purchase_price, bidding_end_date, starting_price, minimum_raise) VALUES ('0196e7c1-205d-70dc-9d15-650f02c7beca','0196e7c1-56a2-78a1-beb9-354db97b55c3','Playwrigth test equipment', 'Phone', '2024-06-06', '949.99',  NOW() + interval '3' month, 200, 10)"])
    @Test
    fun `User makes a bid when there are no previous bids`() {
        val auctionItemId = "0196e7c1-205d-70dc-9d15-650f02c7beca"
        playwright!!.chromium().launch().use { browser ->

            val page = browser.openAuctionItemModal(itemId = auctionItemId)
            val itemBefore = auctionService.getAuctionItem(auctionItemId).getOrElse { fail("Could not get auction item") }
            itemBefore.bids.shouldBeEmpty()

            // For the first bid expected minimum pid is the current price
            val expectedPrice = itemBefore.currentPrice

            page.locator("#submit-bid").click()

            // Wait for the modal to be hidden
            page.waitForSelector("#myBidModal", WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN))

            // Wait for the page to reload and finish loading
            page.waitForLoadState(LoadState.LOAD)

            val itemAfter = auctionService.getAuctionItem(auctionItemId).getOrElse { fail("Could not get auction item") }
            itemAfter.bids.shouldHaveSize(1)
            itemAfter.currentPrice shouldBe expectedPrice

            // Check that Current Price is updated to expectedPrice
            val currentPriceText = page.locator("#item_${auctionItemId} .card-text").textContent()
            currentPriceText shouldContain "$expectedPrice€"
        }
    }

    @Test
    fun `User makes a bid when there are previous bids`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.openAuctionItemModal()

            val itemBefore = auctionService.getAuctionItem(AUCTION_ITEM_ID).getOrElse { fail("Could not get auction item") }
            // After the first bid minimum bid is currentPrice + minimumRaise
            val bidAmount = itemBefore.currentPrice + itemBefore.minimumRaise

            page.placeBid(bidAmount.toString())

            // Wait for the modal to be hidden
            page.waitForSelector("#myBidModal", WaitForSelectorOptions().setState(WaitForSelectorState.HIDDEN))

            // Check database has correct price
            val itemAfter = auctionService.getAuctionItem(AUCTION_ITEM_ID).getOrElse { fail("Could not get auction item") }
            itemAfter.bids.size shouldBe itemBefore.bids.size + 1
            itemAfter.currentPrice shouldBe bidAmount
            val latestBid = itemAfter.bids.first()
            latestBid.bidPrice shouldBe bidAmount

            // Check that page is showing the correct price
            page.waitForSelector("#item_$AUCTION_ITEM_ID .card-text", WaitForSelectorOptions().setState(WaitForSelectorState.VISIBLE))
            val updatedPriceText = page.locator("#item_$AUCTION_ITEM_ID .card-text").textContent()
            updatedPriceText shouldContain "Current Bid: ${itemAfter.currentPrice}€"
        }
    }

    @Test
    fun `User is logged in and can not see Admin Panel navigation`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.frontpage()

            page.title() shouldBe "Auction"

            val navbarContainsAdmin = page.locator(".navbar").textContent().contains("Admin Panel")
            navbarContainsAdmin.shouldBeFalse()

        // Take a screenshot to verify that there is no admin link
            //page.takeScreenshot("admin-link-found.png")
        }
    }

    @Test
    fun `User can log out successfully`() {
        playwright!!.chromium().launch().use { browser ->
            val page = browser.frontpage()

            // Do logout
            page.click("form[action$='/logout'] button")
            page.waitForSelector("text=You have been signed out")

            // Check if button with text 'Sign in' exists
            val signInButtonExists = page.locator("button:has-text('Sign in')").count() > 0
            signInButtonExists.shouldBeTrue()
        }
    }
}