package com.shrivatsav.monomail.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.core.data.licensing.LicenseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LicenseKeyViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    val licenseManager = LicenseManager(application.applicationContext)
}
