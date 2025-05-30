package com.toivape.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

data class UpdateAuctionItemRequest(
    @field:NotBlank(message = "externalId is required")
    @field:Size(max = 50, message = "externalId must be less than 50 characters")
    val externalId: String?,

    @field:NotBlank(message = "description is required")
    @field:Size(max = 500, message = "description must be less than 500 characters")
    val description: String?,

    @field:NotBlank(message = "category is required")
    @field:Size(max = 50, message = "category must be less than 50 characters")
    val category: String?,

    val purchaseDate: LocalDate?,

    val purchasePrice: BigDecimal?,

    @field:NotNull(message = "biddingEndDate is required")
    val biddingEndDate: LocalDate?,

    @field:NotNull(message = "startingPrice is required")
    @field:Min(0, message = "startingPrice must be greater than or equal to 0")
    val startingPrice: Int?,

    @field:NotNull(message = "minimumRaise is mandatory")
    @field:Min(value = 1, message = "minimumRaise must be greater than 0")
    @field:Max(value = 200, message = "minimumRaise must be less than 200")
    var minimumRaise: Int?
)

fun Model.addError(message: String? = "Operation failed") = addAttribute("error", message)

fun Model.addSuccess(message: String) = addAttribute("success", message)

fun BindingResult.addErrorsToModel(model: Model) =
    model.addAttribute("errors", allErrors.map {it.defaultMessage.orEmpty()})

@Controller
class AdminController(private val auctionDao: AuctionDao, private val auctionService:AuctionService, private val bidService: BidService) {

    @GetMapping("/admin")
    fun admin(model: Model): String {
        model.addAttribute("items", auctionDao.findAllAdminPageItems())
        return "admin"
    }

    @GetMapping("/admin/edit/{itemId}")
    fun editItem(@PathVariable itemId: String, model: Model): String =
        auctionService.getAuctionItem(itemId).fold(
            {
                model.addError("Item not found")
                admin(model)
            },
            { item ->
                model.addAttribute("item", item)
                "admin-edit"
            }
        )

    @PostMapping("/admin/edit/{itemId}")
    fun updateItem(@PathVariable itemId: String,
                   @Valid @ModelAttribute request: UpdateAuctionItemRequest,
                   bindingResult: BindingResult,
                   model: Model): String {

        if (bindingResult.hasErrors()) {
            log.info{"Validation errors"}
            bindingResult.addErrorsToModel(model)
            return editItem(itemId, model)
        }

        auctionService.updateAuctionItem(itemId, request).fold(
            { error ->
                model.addError(error.message)
            },
            { item ->
                model.addSuccess("Item updated successfully")
            }
        )

        return editItem(itemId, model)
    }

    @PostMapping("/admin/edit/{itemId}/bids/{bidId}")
    fun removeBid(@PathVariable itemId: String, @PathVariable bidId: String, model:Model): String{
        bidService.removeBid(itemId, bidId).fold(
            { error ->
                model.addError(error.message)
            },
            { _ ->
                model.addSuccess("Bid removed successfully")
            }
        )

        return editItem(itemId, model)
    }


}