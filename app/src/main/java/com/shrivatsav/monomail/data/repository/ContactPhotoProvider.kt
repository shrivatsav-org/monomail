package com.shrivatsav.monomail.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactPhotoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getPhotoUri(email: String): Uri? = null
}
