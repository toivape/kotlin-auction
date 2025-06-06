package com.toivape.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException

class NotFoundException(message:String) : RuntimeException(message)

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errorMessage = ex.bindingResult.allErrors.joinToString(", ") { it.defaultMessage.orEmpty() }
        return ResponseEntity(ErrorResponse(errorMessage), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidationException(ex: HandlerMethodValidationException): ResponseEntity<ErrorResponse> {
        val errorMessage = ex.allErrors.joinToString(", ") { it.defaultMessage.orEmpty() }
        return ResponseEntity(ErrorResponse(errorMessage), ex.statusCode)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthException(ex: AuthorizationDeniedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message), HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error(ex) { "Unhandled exception: ${ex.message}" }
        return ResponseEntity(ErrorResponse(ex.message), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}