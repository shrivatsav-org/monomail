package com.shrivatsav.monomail.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactPhotoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<String, Uri?>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            cache.clear()
        }
    }

    init {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
        }
    }

    fun getPhotoUri(email: String): Uri? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return cache.getOrPut(email) { queryContactPhoto(email) }
    }

    private fun queryContactPhoto(email: String): Uri? {
        val cr = context.contentResolver
        val projection = arrayOf(ContactsContract.Data.CONTACT_ID)
        val selection = "${ContactsContract.CommonDataKinds.Email.ADDRESS} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(email, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)

        cr.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactId = cursor.getLong(0)
                return Uri.withAppendedPath(
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                    ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
                )
            }
        }
        return null
    }
}
