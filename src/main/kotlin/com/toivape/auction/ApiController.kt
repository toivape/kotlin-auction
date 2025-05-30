package com.toivape.auction

import arrow.core.Either
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

data class ErrorResponse(val error: String? = "Unknown error")

data class NewAuctionItem(
    @field:NotBlank(message = "externalId is mandatory")
    @field:Size(max = 50, message="externalId must be less than 50 characters")
    var externalId: String?,

    @field:NotBlank(message = "description is mandatory")
    @field:Size(max = 500, message="description must be less than 500 characters")
    var description: String?,

    @field:Size(max = 50, message="category must be less than 50 characters")
    var category: String?,

    var purchaseDate: LocalDate?,
    var purchasePrice: BigDecimal?,

    @field:NotNull(message = "startingPrice is mandatory")
    @field:Min(value = 1, message = "startingPrice must be greater than 0")
    @field:Max(value = 10000,message = "startingPrice must be less than 10000")
    var startingPrice: Int?,

    @field:NotNull(message = "minimumRaise is mandatory")
    @field:Min(value = 0, message = "minimumRaise must be greater than 0")
    @field:Max(value = 200, message = "minimumRaise must be less than 200")
    var minimumRaise: Int?
)

data class BidRequest(
    @field:NotNull(message = "Bid amount is mandatory")
    @field:Min(value = 1, message = "Bid amount must be at least 1")
    val amount: Int?,

    // The last bid id is empty if there are no earlier bids
    // Used to check that there are no concurrent bids
    @field:ValidUUID(message = "lastBidId is invalid")
    val lastBidId: String = ""
)

private val log = KotlinLogging.logger {}

@RestController
class ApiController(val bidService: BidService, val auctionService: AuctionService) {

    @PostMapping("/api/auctionitems")
    fun createAuctionItem(@Valid @RequestBody item: NewAuctionItem): ResponseEntity<Any> {
        return when (val result = auctionService.addAuctionItem(item)) {
            is Either.Right -> ResponseEntity(result.value, HttpStatus.CREATED)
            is Either.Left -> {
                val errorMessage = result.value.message ?: "Unknown error"
                val status = if (errorMessage.contains("already exists")) {
                    HttpStatus.BAD_REQUEST
                } else {
                    HttpStatus.INTERNAL_SERVER_ERROR
                }
                ResponseEntity(ErrorResponse(errorMessage), status)
            }
        }
    }

    @PostMapping("/api/auctionitems/{auctionItemId}/bids")
    fun placeBid(
        @PathVariable @ValidUUID auctionItemId: String,
        @Valid @RequestBody bid: BidRequest
    ): ResponseEntity<Any> {
        val dummyUser = "dummy-user@toivape.com"
        log.info { "New bid $bid on auction item $auctionItemId by user $dummyUser" }

        return when (val result: Either<Exception, Bid> =
            bidService.addBid(auctionItemId, dummyUser, bid.amount!!, bid.lastBidId)) {
            is Either.Right -> ResponseEntity(result.value, HttpStatus.CREATED)
            is Either.Left -> {
                when (result.value) {
                    is ConcurrentBidException -> ResponseEntity(
                        ErrorResponse("Other user has placed a bid"),
                        HttpStatus.BAD_REQUEST
                    )
                    is DuplicateBidException -> ResponseEntity(
                        ErrorResponse("You cannot bid twice in a row"),
                        HttpStatus.BAD_REQUEST
                    )
                    is ExpiredException -> ResponseEntity(
                        ErrorResponse("Auction item is expired"),
                        HttpStatus.BAD_REQUEST
                    )
                    is InvalidRaiseException -> ResponseEntity(
                        ErrorResponse(result.value.message!!),
                        HttpStatus.BAD_REQUEST
                    )
                    else -> {
                        ResponseEntity(ErrorResponse(result.value.message), HttpStatus.INTERNAL_SERVER_ERROR)
                    }
                }
            }
        }
    }

    @GetMapping("/api/auctionitems/{auctionItemId}")
    fun getItemWithBids(@PathVariable @ValidUUID auctionItemId: String): ResponseEntity<AuctionItem> {
        return when (val result: Either<Exception, AuctionItem> = auctionService.getAuctionItem(auctionItemId)) {
            is Either.Right -> ResponseEntity(result.value, HttpStatus.OK)
            is Either.Left -> ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }

}