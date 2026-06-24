package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.luisito.gestor360.domain.repository.ILicenseRepository

class ActivationViewModelFactory(
    private val licenseRepo: ILicenseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ActivationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ActivationViewModel(licenseRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

fun ActivationViewModel.factory(licenseRepo: ILicenseRepository) = ActivationViewModelFactory(licenseRepo)
