package com.almasawa.files.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "panels")
data class PanelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Long,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val position: Int = 0,
)

@Entity(
    tableName = "panel_items",
    foreignKeys = [ForeignKey(
        entity = PanelEntity::class,
        parentColumns = ["id"],
        childColumns = ["panelId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("panelId")],
)
data class PanelItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val panelId: Long,
    val path: String,
    val label: String,
    val isDir: Boolean,
)

data class PanelWithItems(
    @Embedded val panel: PanelEntity,
    @Relation(parentColumn = "id", entityColumn = "panelId") val items: List<PanelItemEntity>,
)

@Dao
interface PanelDao {
    @Transaction
    @Query("SELECT * FROM panels ORDER BY pinned DESC, position ASC, id ASC")
    fun observe(): Flow<List<PanelWithItems>>

    @Insert
    suspend fun insert(panel: PanelEntity): Long

    @Update
    suspend fun update(panel: PanelEntity)

    @Query("DELETE FROM panels WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun addItem(item: PanelItemEntity)

    @Query("DELETE FROM panel_items WHERE id = :id")
    suspend fun removeItem(id: Long)

    @Query("SELECT COUNT(*) FROM panel_items WHERE panelId = :panelId AND path = :path")
    suspend fun countItem(panelId: Long, path: String): Int
}

@Database(entities = [PanelEntity::class, PanelItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun panelDao(): PanelDao
}
