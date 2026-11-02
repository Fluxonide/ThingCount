package com.ranit.thingcount.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ranit.thingcount.data.drive.GoogleDriveSyncManager
import com.ranit.thingcount.data.repository.CounterRepository

class CounterViewModelFactory(
    private val repository: CounterRepository,
    private val syncManager: GoogleDriveSyncManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CounterViewModel::class.java)) {
            return CounterViewModel(repository, syncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
