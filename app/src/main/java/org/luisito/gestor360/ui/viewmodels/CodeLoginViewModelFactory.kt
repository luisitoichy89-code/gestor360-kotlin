package org.luisito.gestor360.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CodeLoginViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CodeLoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CodeLoginViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
