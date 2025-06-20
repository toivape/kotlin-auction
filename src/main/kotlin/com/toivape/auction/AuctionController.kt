package com.toivape.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

private val log = KotlinLogging.logger {}

@Controller
class AuctionController(val dao: AuctionDao) {

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/")
    fun index(model: Model): String {
        log.info { "Rendering index page" }
        model.addAttribute("items", dao.findFrontPageItems())
        return "index"
    }
}