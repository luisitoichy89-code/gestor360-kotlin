package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class CodeValidationResult(
    val isValid: Boolean,
    val userId: Int? = null,
    val username: String? = null,
    val rol: String? = null,
    val message: String? = null
)
