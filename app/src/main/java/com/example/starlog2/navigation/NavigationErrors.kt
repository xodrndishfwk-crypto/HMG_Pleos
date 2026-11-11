package com.example.starlog2.navigation

import com.example.starlog2.contracts.AppError

internal fun navigationError(
    code: String,
    requestId: String? = null,
    retryable: Boolean = false,
): AppError = AppError(
    code = code,
    messageKey = "navigation.${code.lowercase()}",
    retryable = retryable,
    requestId = requestId,
)
