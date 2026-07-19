package com.shrivatsav.monomail.core.database.local

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = EmailEntity::class)
@Entity(tableName = "emails_fts")
data class EmailFtsEntity(
    val subject: String,
    val body: String,
    val fromName: String,
    val fromEmail: String,
    val toEmail: String,
    val snippet: String
)
