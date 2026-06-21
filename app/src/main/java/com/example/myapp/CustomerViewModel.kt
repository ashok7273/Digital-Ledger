package com.example.myapp

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.launch

class CustomerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CustomerRepository

    val customers: LiveData<List<CustomerEntity>>
        get() = _customers
    private val _customers = MutableLiveData<List<CustomerEntity>>()

    private var allCustomers: List<CustomerEntity> = emptyList()

    init {
        val dao = CustomerDatabase.getDatabase(application).customerDao()
        repository = CustomerRepository(dao)

        // Observe database for changes
        viewModelScope.launch {
            repository.getAllCustomers().collect {
                allCustomers = it
                _customers.postValue(it)
            }
        }
    }

    fun addCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.insertCustomer(customer)
        }
    }

    fun search(query: String) {
        // more efficient: search in Room
        viewModelScope.launch {
            repository.searchCustomers(query).collect {
                _customers.postValue(it)
            }
        }
    }

    fun restoreAll() {
        _customers.postValue(allCustomers)
    }

    suspend fun getCustomerById(id: Int): CustomerEntity? {
        return repository.getCustomerById(id)
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }
}