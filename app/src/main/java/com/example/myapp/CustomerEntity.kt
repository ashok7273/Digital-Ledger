package com.example.myapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mobile: String? = null,
    val address: String? = null,
    val email: String? = null,
    val aadhar: String? = null,
    val gstNo: String? = null,
    val accountCreated: String? = null,  // store date as string
    val balance: Double = 0.0,
    val profileImagePath: String? = null
)