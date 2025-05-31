package com.toivape.auction

import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuctionControllerTest(@Autowired private val mvc: MockMvc) {

    @WithMockUser(username = "test-user@toivape.com", roles = ["USER"])
    @Test
    fun `Show front page with auction items`() {
        mvc.get("/")
            .andExpect {
                status { isOk() }
                content { string(Matchers.containsString("Computer accessories")) }
            }
    }

    @WithAnonymousUser()
    @Test
    fun `Front page requires authentication`() {
        mvc.get("/")
            .andDo { print() }
            .andExpect {
                status { is3xxRedirection() }
         }
    }
}