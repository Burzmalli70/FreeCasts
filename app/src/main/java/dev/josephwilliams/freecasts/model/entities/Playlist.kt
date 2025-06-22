package dev.josephwilliams.freecasts.model.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    @ColumnInfo(name = "creation_date")
    val creationDate: Long = System.currentTimeMillis()
)
