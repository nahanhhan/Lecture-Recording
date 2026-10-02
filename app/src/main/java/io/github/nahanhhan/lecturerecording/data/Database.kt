package io.github.nahanhhan.lecturerecording.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lessons")
data class LessonEntity(@PrimaryKey val id: String, val title: String, val course: String,
    val createdAt: Long, val status: String = "recording", val samples: Long = 0,
    val revision: Int = 1, val photoSequence: Long = 0, val modelId: String = "aed", val error: String = "")

@Entity(tableName = "chunks", indices = [Index("lessonId")])
data class ChunkEntity(@PrimaryKey val id: String, val lessonId: String, val path: String,
    val startSample: Long, val sampleCount: Long = 0)

@Entity(tableName = "segments", indices = [Index("lessonId")])
data class SegmentEntity(@PrimaryKey val id: String, val lessonId: String,
    val startMs: Long, val endMs: Long, val audioPath: String, val text: String = "",
    val status: String = "pending", val error: String = "")

@Entity(tableName = "photos", indices = [Index("lessonId")])
data class PhotoEntity(@PrimaryKey val id: String, val lessonId: String, val filename: String,
    val audioTimeMs: Long, val capturedAtEpochMs: Long, val sequence: Long, val selected: Boolean = true)

@Entity(tableName = "jobs", indices = [Index("lessonId")])
data class JobEntity(@PrimaryKey val id: String, val lessonId: String, val revision: Int,
    val snapshotJson: String, val status: String = "waiting", val error: String = "", val createdAt: Long)

@Entity(tableName = "note_batches", primaryKeys = ["jobId", "batchId", "revision"])
data class NoteBatchEntity(val jobId: String, val batchId: String, val revision: Int,
    val notesJson: String, val callId: String, val assistantJson: String,
    val receiptJson: String, val confirmed: Boolean = false)

@Entity(tableName = "edited_notes")
data class EditedNoteEntity(@PrimaryKey val lessonId: String, val markdown: String)

@Dao
interface LectureDao {
    @Query("SELECT * FROM lessons ORDER BY createdAt DESC") fun observeLessons(): Flow<List<LessonEntity>>
    @Query("SELECT * FROM lessons WHERE id=:id") fun observeLesson(id: String): Flow<LessonEntity?>
    @Query("SELECT * FROM lessons WHERE id=:id") suspend fun lesson(id: String): LessonEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLesson(lesson: LessonEntity)
    @Query("UPDATE lessons SET samples=:samples WHERE id=:id") suspend fun setSamples(id: String, samples: Long)
    @Query("UPDATE lessons SET status=:status,error=:error WHERE id=:id") suspend fun setStatus(id: String, status: String, error: String = "")
    @Query("UPDATE lessons SET revision=revision+1 WHERE id=:id") suspend fun revise(id: String)
    @Query("UPDATE lessons SET photoSequence=:sequence WHERE id=:id") suspend fun setSequence(id: String, sequence: Long)
    @Query("UPDATE lessons SET status='interrupted',error='上次录音已中断，已保存的资料可继续使用' WHERE status IN ('recording','paused','processing')") suspend fun interruptOldRecordings()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChunk(chunk: ChunkEntity)
    @Query("SELECT * FROM chunks WHERE lessonId=:lessonId ORDER BY startSample") suspend fun chunks(lessonId: String): List<ChunkEntity>
    @Query("SELECT * FROM chunks") suspend fun allChunks(): List<ChunkEntity>
    @Query("UPDATE chunks SET sampleCount=:count WHERE id=:id") suspend fun setChunkSamples(id: String, count: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSegment(segment: SegmentEntity)
    @Query("SELECT * FROM segments WHERE lessonId=:lessonId ORDER BY startMs") fun observeSegments(lessonId: String): Flow<List<SegmentEntity>>
    @Query("SELECT * FROM segments WHERE lessonId=:lessonId ORDER BY startMs") suspend fun segments(lessonId: String): List<SegmentEntity>
    @Query("SELECT * FROM segments WHERE lessonId=:lessonId AND status IN ('pending','error') ORDER BY startMs") suspend fun unfinishedSegments(lessonId: String): List<SegmentEntity>
    @Query("UPDATE segments SET text=:text,status='ready',error='' WHERE id=:id") suspend fun finishSegment(id: String, text: String)
    @Query("UPDATE segments SET status='error',error=:error WHERE id=:id") suspend fun failSegment(id: String, error: String)
    @Query("UPDATE segments SET text=:text WHERE id=:id") suspend fun editSegment(id: String, text: String)
    @Insert suspend fun putPhoto(photo: PhotoEntity)
    @Query("SELECT * FROM photos WHERE lessonId=:lessonId ORDER BY audioTimeMs,sequence") fun observePhotos(lessonId: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photos WHERE lessonId=:lessonId ORDER BY audioTimeMs,sequence") suspend fun photos(lessonId: String): List<PhotoEntity>
    @Query("UPDATE photos SET selected=:selected WHERE id=:id") suspend fun selectPhoto(id: String, selected: Boolean)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putJob(job: JobEntity)
    @Query("SELECT * FROM jobs WHERE lessonId=:lessonId ORDER BY createdAt DESC") fun observeJobs(lessonId: String): Flow<List<JobEntity>>
    @Query("SELECT * FROM jobs WHERE lessonId=:lessonId ORDER BY createdAt DESC") suspend fun jobs(lessonId: String): List<JobEntity>
    @Query("SELECT * FROM jobs WHERE id=:id") suspend fun job(id: String): JobEntity?
    @Query("UPDATE jobs SET status=:status,error=:error WHERE id=:id") suspend fun jobStatus(id: String, status: String, error: String = "")
    @Query("UPDATE jobs SET status='waiting',error='整理任务已中断，可手动继续' WHERE status='running'") suspend fun interruptOldJobs()
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBatch(batch: NoteBatchEntity): Long
    @Query("SELECT * FROM note_batches WHERE jobId=:jobId AND batchId=:batchId AND revision=:revision") suspend fun batch(jobId: String, batchId: String, revision: Int): NoteBatchEntity?
    @Query("SELECT * FROM note_batches WHERE jobId=:jobId ORDER BY batchId") suspend fun batches(jobId: String): List<NoteBatchEntity>
    @Query("SELECT * FROM note_batches WHERE jobId=:jobId ORDER BY batchId") fun observeBatches(jobId: String): Flow<List<NoteBatchEntity>>
    @Query("UPDATE note_batches SET confirmed=1 WHERE jobId=:jobId AND batchId=:batchId AND revision=:revision") suspend fun confirmBatch(jobId: String, batchId: String, revision: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEditedNote(note: EditedNoteEntity)
    @Query("SELECT * FROM edited_notes WHERE lessonId=:lessonId") fun observeEditedNote(lessonId: String): Flow<EditedNoteEntity?>
    @Query("SELECT * FROM edited_notes WHERE lessonId=:lessonId") suspend fun editedNote(lessonId: String): EditedNoteEntity?
    @Query("DELETE FROM edited_notes WHERE lessonId=:lessonId") suspend fun clearEditedNote(lessonId: String)
}

@Database(entities = [LessonEntity::class, ChunkEntity::class, SegmentEntity::class, PhotoEntity::class,
    JobEntity::class, NoteBatchEntity::class, EditedNoteEntity::class], version = 1, exportSchema = false)
abstract class LectureDatabase : RoomDatabase() { abstract fun dao(): LectureDao }
