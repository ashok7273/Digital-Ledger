package com.example.myapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND isEditHistory = 0 ORDER BY id DESC")
    fun getTransactionsForCustomer(customerId: Int): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE isEditHistory=1 AND originalTxnId = :txnId ORDER BY date ASC")
    suspend fun getModificationHistory(txnId: Int): List<TransactionEntity>

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY id ASC")
    suspend fun getAllTransactionsForCustomer(customerId: Int): List<TransactionEntity>

    @Query("UPDATE customers SET balance = :balance WHERE id = :id")
    suspend fun updateCustomerBalance(id: Int, balance: Double)

    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND isEditHistory = 0 ORDER BY id DESC LIMIT 5")
    suspend fun getRecentTransactionsForCustomer(customerId: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId AND isEditHistory=0 ORDER BY id DESC")
    suspend fun getTransactionsForCustomerRaw(customerId: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    suspend fun getTransactionById(transactionId: Int): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE isEditHistory=0 ORDER BY id DESC")
    suspend fun getAllTransactionsRaw(): List<TransactionEntity>
}