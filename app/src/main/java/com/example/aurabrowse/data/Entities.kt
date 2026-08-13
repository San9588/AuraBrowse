package com.example.aurabrowse.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(@PrimaryKey val id: String, val name: String, val color: Int, val icon: String, @ColumnInfo(name = "is_default") val isDefault: Boolean)

@Entity(tableName = "tab_groups")
data class GroupEntity(@PrimaryKey val id: String, val name: String, val color: Int, @ColumnInfo(name = "profile_id") val profileId: String, @ColumnInfo(name = "is_collapsed") val isCollapsed: Boolean, val position: Int)

@Entity(tableName = "tabs")
data class TabEntity(@PrimaryKey val id: String, val url: String, val title: String, @ColumnInfo(name = "profile_id") val profileId: String = "default", @ColumnInfo(name = "group_id") val groupId: String? = null, val position: Int = 0, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "history")
data class HistoryEntity(@PrimaryKey val id: String, val url: String, val title: String, val timestamp: Long, @ColumnInfo(name = "profile_id") val profileId: String = "default", val visitCount: Int = 1)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(@PrimaryKey val id: String, val url: String, val title: String, @ColumnInfo(name = "folder_id") val folderId: String?, @ColumnInfo(name = "profile_id") val profileId: String, val timestamp: Long)

@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(@PrimaryKey val id: String, val name: String, @ColumnInfo(name = "parent_id") val parentId: String?, @ColumnInfo(name = "profile_id") val profileId: String)

@Entity(tableName = "downloads")
data class DownloadEntity(@PrimaryKey val id: String, val url: String, val filename: String, val mimeType: String, val size: Long, val timestamp: Long, @ColumnInfo(name = "profile_id") val profileId: String = "default")

@Entity(tableName = "cookies", primaryKeys = ["profile_id", "domain"])
data class CookieEntity(
    @ColumnInfo(name = "profile_id") val profileId: String,
    val domain: String,
    val value: String
)
