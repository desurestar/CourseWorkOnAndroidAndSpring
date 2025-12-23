# Offline Draft with Image Sync Implementation

## Overview
This document describes the complete offline-first draft functionality including local image storage and automatic sync when network becomes available.

## Architecture

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Actions                              │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CreatePostFragment                             │
│  - Captures user input (text, images)                            │
│  - Detects if images are local URIs (content://, file://)        │
│  - Stores local URIs separately when saving drafts offline       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PostRepository                                 │
│  - saveDraft(request, coverLocalUri, stepsImagesJson)            │
│  - Stores both remote URLs and local URIs                        │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Room Database (DraftEntity)                    │
│  - clientId: UUID for idempotency                                │
│  - coverUrl: Remote URL (if already uploaded)                    │
│  - coverLocalUri: Local URI (content:// or file://)              │
│  - stepsImagesJson: JSON map of step index to local URI          │
│  - syncState: PENDING, IN_SYNC, SYNCED, FAILED                   │
└─────────────────────────────────────────────────────────────────┘
                               │
        ┌──────────────────────┴──────────────────────┐
        │  Network Available?                         │
        └──────────────────────┬──────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    WorkManager                                    │
│  - Triggers DraftSyncWorker with network constraint              │
│  - Runs periodically (every 6 hours) or on-demand                │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    DraftSyncWorker                                │
│  - Calls PostRepository.syncDrafts(userId)                       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│            PostRepository.syncDrafts()                            │
│                                                                   │
│  For each pending draft:                                         │
│  1. Check for local images (coverLocalUri, stepsImagesJson)      │
│  2. Upload local images to /api/uploads/{type}                   │
│  3. Get remote URLs from upload response                         │
│  4. Update PostCreateRequest with remote URLs                    │
│  5. Send POST /api/posts with clientId                           │
│  6. Server checks for existing post by clientId                  │
│  7. Update or create post (idempotent)                           │
│  8. Mark draft as SYNCED with serverId                           │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Server (Spring Boot)                           │
│  - Receives post with clientId                                   │
│  - Checks DB for existing post (author_id, client_id)            │
│  - Updates existing or creates new (no duplicates)               │
└─────────────────────────────────────────────────────────────────┘
```

## Database Schema

### DraftEntity (Room)

```kotlin
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String,              // UUID for idempotency
    val serverId: Long? = null,        // Post ID after sync
    val postType: String,
    val status: String,
    val title: String,
    val excerpt: String,
    val content: String,
    val coverUrl: String?,             // Remote URL (http/https)
    val coverLocalUri: String?,        // Local URI (content://, file://)
    val cookingTimeMinutes: Int?,
    val calories: Int?,
    val authorId: Long,
    val tagIds: String,
    val ingredientsJson: String,
    val stepsJson: String,
    val stepsImagesJson: String?,      // {"0": "content://...", "2": "file://..."}
    val syncState: String = "PENDING", // PENDING, IN_SYNC, SYNCED, FAILED
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAttempt: Long? = null
)
```

### Migrations

#### Migration 1 → 2
- Added clientId, serverId, syncState, lastSyncAttempt
- Generated UUIDs for existing drafts

#### Migration 2 → 3
- Added coverLocalUri for offline cover images
- Added stepsImagesJson for offline step images

## API Reference

### Repository Methods

#### saveDraft
```kotlin
suspend fun saveDraft(
    request: PostCreateRequest,
    coverLocalUri: String? = null,
    stepsImagesJson: String? = null
): Result<PostDraft>
```
Saves a draft locally with optional local image URIs.

**Parameters:**
- `request`: Post data including title, content, steps, etc.
- `coverLocalUri`: Local URI for cover image (e.g., "content://media/external/images/123")
- `stepsImagesJson`: JSON map of step index to local URI (e.g., `{"0": "content://...", "2": "file://..."}`)

**Returns:** Result with saved PostDraft or error

#### updateDraft
```kotlin
suspend fun updateDraft(
    id: Long,
    request: PostCreateRequest,
    coverLocalUri: String? = null,
    stepsImagesJson: String? = null
): Result<PostDraft>
```
Updates an existing draft with new data and optional local image URIs.

#### syncDrafts
```kotlin
suspend fun syncDrafts(authorId: Long): Result<Int>
```
Syncs all pending drafts for a user. Called by WorkManager.

**Process:**
1. Fetch drafts with syncState = PENDING or FAILED
2. For each draft:
   - Upload local images if present
   - Update request with remote URLs
   - POST to /api/posts with clientId
   - Mark as SYNCED or FAILED

**Returns:** Number of successfully synced drafts

### Server Endpoints

#### POST /api/posts
```json
{
  "clientId": "uuid-string",
  "postType": "recipe",
  "status": "draft",
  "title": "My Recipe",
  "excerpt": "Short description",
  "content": "Full content",
  "coverUrl": "http://server.com/images/cover.jpg",
  "cookingTimeMinutes": 30,
  "calories": 450,
  "authorId": 1,
  "tagIds": [1, 2, 3],
  "ingredients": [...],
  "steps": [
    {
      "order": 1,
      "description": "Step description",
      "imageUrl": "http://server.com/images/step1.jpg"
    }
  ]
}
```

**Idempotent:** Uses `(author_id, client_id)` unique constraint. If post exists, updates it; otherwise creates new.

#### POST /api/uploads/{type}
```
Content-Type: multipart/form-data
file: <binary data>
```

**Response:**
```json
{
  "url": "http://server.com/uploads/cover/abc123.jpg"
}
```

## Sync States

| State | Description | Next States |
|-------|-------------|-------------|
| PENDING | Draft created locally, waiting for sync | IN_SYNC |
| IN_SYNC | Currently being synchronized | SYNCED, FAILED |
| SYNCED | Successfully synced to server | - |
| FAILED | Sync failed, will retry | IN_SYNC |

## Usage Examples

### Scenario 1: Create Draft Offline

```kotlin
// User creates post offline
val request = PostCreateRequest(
    postType = "recipe",
    status = "draft",
    title = "Pasta Carbonara",
    excerpt = "Classic Italian pasta",
    content = "Full recipe content...",
    coverUrl = null, // Will be uploaded later
    // ... other fields
)

// Save with local image URI
val coverUri = "content://media/external/images/1000000123"
val result = repository.saveDraft(
    request = request,
    coverLocalUri = coverUri,
    stepsImagesJson = null
)

// Draft saved locally with PENDING state
// clientId generated automatically (UUID)
```

### Scenario 2: Auto Sync When Online

```kotlin
// WorkManager triggers sync when network available
class DraftSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentUserId() ?: return Result.success()
        val result = postRepository.syncDrafts(userId)
        return result.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
```

### Scenario 3: Image Upload During Sync

```kotlin
// Inside PostRepositoryImpl.syncDrafts()
if (entity.coverLocalUri != null && !entity.coverLocalUri.startsWith("http")) {
    // Upload local image
    val uri = Uri.parse(entity.coverLocalUri)
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    
    val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
    val part = MultipartBody.Part.createFormData("file", "cover.jpg", requestBody)
    val resp = api.upload("cover", part)
    
    if (resp.isSuccessful) {
        coverUrl = resp.body()?.url // e.g., "http://server.com/uploads/cover/abc.jpg"
    }
}

// Update request with remote URL
val requestWithImages = draft.request.copy(
    coverUrl = coverUrl,
    steps = updatedSteps // with remote step image URLs
)

// Send to server
api.createPost(requestWithImages)
```

## Configuration

### WorkManager Setup

```kotlin
// In CulinaryBlogApp.kt
class CulinaryBlogApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        DraftSyncScheduler.schedulePeriodicSync(this)
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

### Sync Schedule

```kotlin
// In DraftSyncScheduler.kt
fun schedulePeriodicSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    
    val request = PeriodicWorkRequestBuilder<DraftSyncWorker>(
        6, TimeUnit.HOURS // Sync every 6 hours
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            DraftSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
}
```

## Authentication

### JWT Storage

JWT tokens are stored using EncryptedSharedPreferences for security:

```kotlin
// In TokenStorage.kt
private val prefs = EncryptedSharedPreferences.create(
    context,
    "auth_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

### Offline Login

- JWT token stored locally allows user to appear logged in offline
- Protected API calls deferred until network available
- User ID stored separately for draft sync worker

## Error Handling

### Network Errors
```kotlin
try {
    uploadLocalImage(uri)
} catch (e: UnknownHostException) {
    // No network, keep local URI
    Log.w(TAG, "No network, will retry later")
    Result.failure(e)
} catch (e: SocketTimeoutException) {
    // Timeout, will retry
    Result.retry()
}
```

### Upload Failures
- If image upload fails, draft sync marked as FAILED
- Original local URI preserved in database
- Will retry on next sync attempt

### Duplicate Prevention
- Server uses `(author_id, client_id)` unique constraint
- Multiple sync attempts update same post
- No duplicates created

## Testing

### Unit Tests

```kotlin
@Test
fun `syncDrafts uploads local images before creating post`() = runTest {
    // Given: Draft with local image URI
    val draft = PostCreateRequest(...)
    repository.saveDraft(draft, coverLocalUri = "content://...")
    
    // When: Sync is triggered
    repository.syncDrafts(userId)
    
    // Then: Image upload API called
    verify(api).upload(eq("cover"), any())
    
    // And: Post created with remote URL
    verify(api).createPost(argThat { coverUrl.startsWith("http") })
}

@Test
fun `saveDraft stores local URI separately from remote URL`() = runTest {
    val request = PostCreateRequest(coverUrl = null, ...)
    val localUri = "content://media/external/images/123"
    
    val result = repository.saveDraft(request, coverLocalUri = localUri)
    
    assertTrue(result.isSuccess)
    val draft = result.getOrNull()!!
    assertEquals(localUri, draft.coverLocalUri)
    assertNull(draft.request.coverUrl)
}
```

### Integration Tests

```kotlin
@Test
fun `complete offline to online flow`() = runTest {
    // 1. Create draft offline
    setNetworkAvailable(false)
    val draft = repository.saveDraft(request, coverLocalUri = localUri)
    assertEquals("PENDING", draft.getOrNull()?.syncState)
    
    // 2. Enable network
    setNetworkAvailable(true)
    
    // 3. Trigger sync
    val worker = TestListenableWorkerBuilder<DraftSyncWorker>(context).build()
    val result = worker.doWork()
    
    // 4. Verify synced
    assertEquals(ListenableWorker.Result.success(), result)
    val updated = repository.getDraft(draft.getOrNull()!!.id)
    assertEquals("SYNCED", updated.getOrNull()?.syncState)
}
```

## Performance Considerations

### Image Size
- Consider compressing images before storing locally
- Set reasonable file size limits (e.g., max 5MB per image)
- Use JPEG with quality 80-90 for good balance

### Batch Sync
- Current implementation syncs one draft at a time
- Future: Consider batch upload for multiple drafts
- Use WorkManager's input data for progress tracking

### Storage Cleanup
- Clean up local image files after successful sync
- Consider expiration policy for old drafts (e.g., 30 days)
- Provide manual "Clear synced drafts" option

## Security

### Image Access
- Local URIs require runtime permissions (READ_EXTERNAL_STORAGE)
- ContentProvider permissions handled by system
- Temporary files created securely in app cache

### JWT Handling
- Tokens encrypted with EncryptedSharedPreferences
- Master key backed by Android Keystore
- Automatic key rotation on app update

### API Security
- All endpoints require Bearer token authentication
- HTTPS enforced in production
- CSRF protection on state-changing operations

## Known Limitations

1. **No Conflict Resolution**: If draft edited on multiple devices, last sync wins
2. **No Partial Sync**: If image upload fails, entire draft sync fails
3. **No Progress UI**: User doesn't see upload progress for images
4. **No Manual Sync Button**: Cannot trigger sync on-demand (future enhancement)
5. **Context Injection**: PostRepositoryImpl requires Application Context for file access

## Future Enhancements

1. **Progress Indicators**: Show image upload progress in UI
2. **Manual Sync**: Add button to trigger immediate sync
3. **Conflict Resolution**: Detect and resolve concurrent edits
4. **Batch Uploads**: Upload multiple images in single request
5. **Selective Sync**: Choose which drafts to sync
6. **Sync Statistics**: Track success/failure rates
7. **Image Caching**: Cache uploaded images for faster reuse
8. **Draft Expiration**: Auto-delete old synced drafts

## Troubleshooting

### Draft Not Syncing
1. Check network connectivity
2. Verify user is logged in (`authRepository.getCurrentUserId()`)
3. Check sync state in database (should be PENDING or FAILED)
4. Look for errors in logcat with tag "PostRepositoryImpl"
5. Verify WorkManager is running (`WorkManager.getInstance().getWorkInfosForUniqueWork()`)

### Images Not Uploading
1. Check URI permissions (content:// requires provider access)
2. Verify file exists at URI path
3. Check server upload endpoint is accessible
4. Look for upload errors in logs
5. Test with smaller image file

### Duplicate Posts Created
1. Verify clientId is being set correctly
2. Check server unique constraint on (author_id, client_id)
3. Ensure clientId not changing between sync attempts
4. Check for race conditions in concurrent syncs

## References

- [Android WorkManager Documentation](https://developer.android.com/topic/libraries/architecture/workmanager)
- [Room Database Guide](https://developer.android.com/training/data-storage/room)
- [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Content Providers](https://developer.android.com/guide/topics/providers/content-providers)

## Changelog

### Version 3 (Current)
- Added coverLocalUri field for offline cover images
- Added stepsImagesJson field for offline step images
- Implemented image upload in syncDrafts()
- Added ApplicationContext injection to PostRepositoryImpl

### Version 2
- Added clientId, serverId, syncState fields
- Implemented idempotent sync with server
- Added DraftSyncWorker with WorkManager

### Version 1
- Basic draft storage in Room database
- Local draft list and edit functionality
