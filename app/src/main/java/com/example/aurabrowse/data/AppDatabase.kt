package com.example.aurabrowse.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface BrowserDao {
    @Query("SELECT * FROM tabs ORDER BY position") fun tabs(): Flow<List<TabEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveTab(tab: TabEntity)
    @Delete suspend fun deleteTab(tab: TabEntity)
    @Query("SELECT * FROM profiles ORDER BY is_default DESC, name") fun profiles(): Flow<List<ProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: ProfileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGroup(group: GroupEntity)
    @Delete suspend fun deleteProfile(profile: ProfileEntity)
    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 1000") fun history(): Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveHistory(item: HistoryEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDownload(item: DownloadEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBookmark(item: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE profile_id = :profileId ORDER BY timestamp DESC") fun bookmarks(profileId: String): Flow<List<BookmarkEntity>>
}

@Database(entities = [ProfileEntity::class, GroupEntity::class, TabEntity::class, HistoryEntity::class, BookmarkEntity::class, BookmarkFolderEntity::class, DownloadEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun browserDao(): BrowserDao
    companion object { @Volatile private var instance: AppDatabase? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "aurabrowse.db").fallbackToDestructiveMigration().build().also { instance = it } }
    }
}
