package com.ranit.thingcount.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.ranit.thingcount.BuildConfig
import com.ranit.thingcount.data.drive.GoogleDriveSyncManager
import com.ranit.thingcount.data.drive.SyncResult
import com.ranit.thingcount.data.model.Counter
import com.ranit.thingcount.data.model.CounterHistory
import com.ranit.thingcount.data.repository.CounterRepository
import com.ranit.thingcount.data.update.AppUpdateManager
import com.ranit.thingcount.data.update.UpdateInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CounterViewModel(
    private val repository: CounterRepository,
    private val syncManager: GoogleDriveSyncManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(
        syncManager.getLastSignedInAccount()
    )
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    // In-App Updates state
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    val allTags: StateFlow<List<String>> = repository.allTags.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val counters: StateFlow<List<Counter>> = combine(
        repository.activeCounters,
        _searchQuery,
        _selectedTag
    ) { counterList, query, tag ->
        counterList.filter { counter ->
            val matchesQuery = query.isBlank() || counter.title.contains(query, ignoreCase = true)
            val matchesTag = tag == null || counter.tag.equals(tag, ignoreCase = true)
            matchesQuery && matchesTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Automatically check for updates on startup if configured
        if (BuildConfig.GITHUB_REPO_OWNER.isNotBlank()) {
            checkForUpdates()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun increment(counterId: String) {
        viewModelScope.launch {
            repository.increment(counterId)
        }
    }

    fun decrement(counterId: String) {
        viewModelScope.launch {
            repository.decrement(counterId)
        }
    }

    fun reset(counterId: String) {
        viewModelScope.launch {
            repository.reset(counterId)
        }
    }

    fun toggleLock(counterId: String) {
        viewModelScope.launch {
            repository.toggleLock(counterId)
        }
    }

    fun saveCounter(counter: Counter) {
        viewModelScope.launch {
            repository.saveCounter(counter)
        }
    }

    fun deleteCounter(counterId: String) {
        viewModelScope.launch {
            repository.deleteCounter(counterId)
        }
    }

    fun getCounter(id: String): Flow<Counter?> = repository.getCounter(id)

    fun getHistory(counterId: String): Flow<List<CounterHistory>> =
        repository.getHistory(counterId)

    fun setSignedInAccount(account: GoogleSignInAccount?) {
        _signedInAccount.value = account
        if (account != null) {
            syncWithDrive()
        }
    }

    fun syncWithDrive() {
        val account = _signedInAccount.value ?: return
        viewModelScope.launch {
            _syncResult.value = SyncResult.InProgress
            val result = syncManager.syncWithDrive(account)
            _syncResult.value = result
        }
    }

    suspend fun exportJson(): String {
        return syncManager.exportJson()
    }

    suspend fun importJson(json: String): Boolean {
        return syncManager.importJson(json)
    }

    fun checkForUpdates(
        owner: String = BuildConfig.GITHUB_REPO_OWNER,
        repo: String = BuildConfig.GITHUB_REPO_NAME
    ) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateInfo.value = AppUpdateManager.checkLatestRelease(owner, repo)
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }
}
