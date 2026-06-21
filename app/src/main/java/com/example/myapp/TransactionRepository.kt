package com.example.myapp

class TransactionRepository(private val dao: TransactionDao) {
    fun getTransactionsForCustomer(customerId: Int) = dao.getTransactionsForCustomer(customerId)
    suspend fun insertTransaction(transaction: TransactionEntity) = dao.insertTransaction(transaction)
}