package org.luisito.gestor360

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LicenciaRepository
import org.luisito.gestor360.ui.viewmodels.MainViewModel

class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val licenciaRepository: LicenciaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(authRepository, licenciaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
