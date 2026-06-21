package com.example.myapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Insert
    suspend fun insertCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :search || '%' ORDER BY name ASC")
    fun searchCustomers(search: String): Flow<List<CustomerEntity>>

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)
}