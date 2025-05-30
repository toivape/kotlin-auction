package com.toivape.auction

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.util.*
import kotlin.reflect.KClass

@Constraint(validatedBy = [ValidUUIDValidator::class])
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ValidUUID(
    val message: String = "Invalid UUID",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class ValidUUIDValidator : ConstraintValidator<ValidUUID, String> {
    override fun isValid(uuid: String, context: ConstraintValidatorContext?): Boolean {
        if (uuid.isNullOrBlank()) return true
        return runCatching { UUID.fromString(uuid) }.isSuccess
    }
}

