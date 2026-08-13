package com.example.aurabrowse.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface BrowserDao {
    @Query("SELECT * FROM tabs ORDER BY position") fun tabs(): Flow<List<TabEntity>>
    @Query("SELECT * FROM tabs ORDER BY position") suspend fun tabsSnapshot(): List<TabEntity>
    @Query("SELECT * FROM tab_groups ORDER BY position") suspend fun groupsSnapshot(): List<GroupEntity>
    @Query("SELECT * FROM history WHERE url = :url AND profile_id = :profileId LIMIT 1") suspend fun historyByUrl(url: String, profileId: String): HistoryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTab(tab: TabEntity)
    @Delete suspend fun deleteTab(tab: TabEntity)
    @Query("SELECT * FROM profiles ORDER BY is_default DESC, name") fun profiles(): Flow<List<ProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: ProfileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGroup(group: GroupEntity)
    @Delete suspend fun deleteProfile(profile: ProfileEntity)
    @Delete suspend fun deleteGroup(group: GroupEntity)
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 1000") fun history(): Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(item: HistoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDownload(item: DownloadEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBookmark(item: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE profile_id = :profileId ORDER BY timestamp DESC") fun bookmarks(profileId: String): Flow<List<BookmarkEntity>>
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("DELETE FROM tabs WHERE profile_id = :profileId") suspend fun deleteTabsForProfile(profileId: String)
    @Query("DELETE FROM tab_groups WHERE profile_id = :profileId") suspend fun deleteGroupsForProfile(profileId: String)
    @Query("DELETE FROM history WHERE profile_id = :profileId") suspend fun deleteHistoryForProfile(profileId: String)
    @Query("DELETE FROM bookmarks WHERE profile_id = :profileId") suspend fun deleteBookmarksForProfile(profileId: String)
    @Query("DELETE FROM downloads WHERE profile_id = :profileId") suspend fun deleteDownloadsForProfile(profileId: String)
    @Query("SELECT * FROM cookies WHERE profile_id = :profileId") suspend fun cookiesForProfile(profileId: String): List<CookieEntity>
    @Query("SELECT DISTINCT domain FROM cookies") suspend fun cookieDomains(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCookie(cookie: CookieEntity)
    @Query("DELETE FROM cookies WHERE profile_id = :profileId") suspend fun deleteCookiesForProfile(profileId: String)
}

@Database(entities = [ProfileEntity::class, GroupEntity::class, TabEntity::class, HistoryEntity::class, BookmarkEntity::class, BookmarkFolderEntity::class, DownloadEntity::class, CookieEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao
    companion object { @Volatile private var instance: AppDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "aurabrowse.db").fallbackToDestructiveMigration().build().also { instance = it } }
    }
}
