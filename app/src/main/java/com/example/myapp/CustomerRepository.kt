package com.example.myapp

import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val dao: CustomerDao) {
    fun getAllCustomers(): Flow<List<CustomerEntity>> = dao.getAllCustomers()
    suspend fun insertCustomer(customer: CustomerEntity) = dao.insertCustomer(customer)
    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = dao.searchCustomers(query)
    suspend fun getCustomerById(id: Int) = dao.getCustomerById(id)
    suspend fun deleteCustomer(customer: CustomerEntity) = dao.deleteCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = dao.updateCustomer(customer)
}