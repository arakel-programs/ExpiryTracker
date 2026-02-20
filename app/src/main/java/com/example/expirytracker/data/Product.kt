package com.example.expirytracker.data

data class Product(
    val id: Long,
    val name: String,
    val batchDateMillis: Long,   // NEW FIELD
    val qtyInitial: Int,
    val qtyCurrent: Int,
    val expiresAtMillis: Long,
    val status: String = "ACTIVE"
)
