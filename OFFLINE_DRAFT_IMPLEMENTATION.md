# Offline-First Draft Synchronization Implementation

## Overview
This implementation adds offline-first draft functionality to the culinary blog application, allowing users to create and edit drafts without internet connectivity and automatically sync them when network becomes available.

## Server Changes (Java/Spring Boot)

### 1. Database Schema
- **Migration**: `V3__add_client_id_to_posts.sql`
  - Added `client_id VARCHAR(36)` column to `posts` table
  - Added unique constraint on `(author_id, client_id)` to prevent duplicates
  - Added index for efficient lookups

### 2. Entity Updates
- **Post.java**: Added `clientId` field to store client UUID for idempotent sync

### 3. DTO Updates
- **PostCreateDto.java**: Added `clientId` field to accept UUID from mobile clients

### 4. Service Layer
- **PostServiceImpl.java**: 
  - Implemented idempotent upsert logic in `create()` method
  - Checks for existing posts with same `clientId` before creating new ones
  - Updates existing post if found, preventing duplicates
- **PostAssembler.java**: 
  - Added `updateCollections()` helper method for updating tags, ingredients, and steps
  - Saves `clientId` when creating posts

### 5. Repository Layer  
- **PostRepository.java**:
  - Added `findByAuthorIdAndClientId()` for duplicate detection
  - Added `findByAuthorIdAndStatus()` for fetching user's drafts

### 6. Controller Layer
- **PostController.java**: 
  - Added `GET /api/posts/mine/drafts` endpoint
  - Returns paginated list of authenticated user's draft posts

## Android Changes (Kotlin)

### 1. Database Schema
- **DraftEntity.kt**: 
  - Added `clientId` (UUID) for server sync
  - Added `serverId` (nullable Long) to track synced post ID
  - Added `syncState` (PENDING/IN_SYNC/SYNCED/FAILED)
  - Added `lastSyncAttempt` timestamp

- **CulinaryDatabase.kt**: 
  - Incremented version to 2
  - Added migration for new fields with UUID generation

### 2. Data Access
- **DraftDao.kt**:
  - Added `getByAuthorAndSyncState()` for filtering drafts
  - Added `updateSyncState()` for tracking sync progress
  - Added `markSynced()` for recording successful sync

### 3. Model Updates
- **PostCreateRequest**: Added `clientId` field
- **PostDraft**: Added `syncState` and `serverId` fields

### 4. Mappers
- **Mappers.kt**: Updated to handle new fields including clientId generation

### 5. Repository Layer
- **PostRepositoryImpl.kt**:
  - Updated `syncDrafts()` to use clientId for idempotency
  - Filters only PENDING/FAILED drafts for sync
  - Updates sync state throughout the process
  - Handles server responses and marks drafts as SYNCED

### 6. WorkManager Integration
- **DraftSyncWorker.kt**: 
  - Background worker that syncs pending drafts
  - Requires network connectivity
  - Uses Hilt for dependency injection
  - Implements retry logic on failures

- **DraftSyncScheduler.kt**:
  - Helper for scheduling periodic sync (every 6 hours)
  - Can trigger immediate sync on-demand
  - Configures network constraints

### 7. Authentication
- **TokenStorage.kt**: 
  - Extended to save/retrieve user ID
  - Used by DraftSyncWorker to identify current user

- **AuthRepository.kt**: 
  - Added `getCurrentUserId()` method
  - Injected TokenStorage dependency

### 8. Application Setup
- **CulinaryBlogApp.kt**:
  - Implements `Configuration.Provider` for custom WorkManager setup
  - Schedules periodic sync on app startup
  - Uses HiltWorkerFactory for DI

- **AndroidManifest.xml**: 
  - Disabled default WorkManager initializer to use Hilt

### 9. API Integration
- **PostApi.kt**: Added `getMyDrafts()` endpoint for fetching user's drafts from server

## Key Features

### 1. Offline Draft Creation
- Users can create/edit drafts without internet
- All data saved locally in Room database
- Each draft assigned unique UUID (clientId)

### 2. Automatic Synchronization
- WorkManager runs every 6 hours when network available
- Syncs only PENDING or FAILED drafts
- Updates sync state throughout process

### 3. Idempotent Sync
- Server uses clientId + authorId unique constraint
- Repeated sync attempts update existing post instead of creating duplicates
- Client tracks serverId after successful sync

### 4. Sync States
- **PENDING**: Draft created, waiting for sync
- **IN_SYNC**: Currently being synchronized
- **SYNCED**: Successfully synced to server
- **FAILED**: Sync failed, will retry

### 5. Network Awareness
- WorkManager only runs when network connected
- Exponential backoff on failures
- Periodic retries for failed drafts

## Usage Flow

1. **User Creates Draft Offline**:
   - Draft saved to local database with PENDING state
   - Unique clientId (UUID) generated

2. **Network Becomes Available**:
   - WorkManager triggers DraftSyncWorker
   - Worker fetches PENDING/FAILED drafts for current user

3. **Sync Process**:
   - State updated to IN_SYNC
   - Draft sent to server with clientId
   - Server checks for existing post with same clientId
   - If found: updates existing post
   - If not: creates new post
   - Client receives server post ID

4. **Sync Success**:
   - Local draft marked as SYNCED
   - serverId saved for reference
   - Draft can be safely kept or deleted

5. **Sync Failure**:
   - State set to FAILED
   - lastSyncAttempt timestamp recorded
   - Will retry on next worker run

## Testing Scenarios

### Scenario 1: Offline Draft Creation
1. Turn off network
2. Create new recipe draft
3. Verify saved locally with PENDING state
4. Verify clientId assigned

### Scenario 2: Online Sync
1. Turn on network
2. Wait for WorkManager (or trigger manually)
3. Verify draft synced
4. Verify state changed to SYNCED
5. Verify no duplicates on server

### Scenario 3: Duplicate Prevention
1. Create draft with network on (gets synced)
2. Turn off network
3. Modify same draft
4. Turn on network
5. Verify sync updates existing post
6. Verify no duplicate created

### Scenario 4: Failed Sync Retry
1. Create draft
2. Temporarily break network (or server)
3. Verify state becomes FAILED
4. Fix network
5. Verify automatic retry succeeds

### Scenario 5: Multiple Drafts
1. Create 3 drafts offline
2. Turn on network  
3. Verify all sync successfully
4. Verify correct order maintained

## API Endpoints

### Server Endpoints

#### Create/Update Post (Idempotent)
```
POST /api/posts
Content-Type: application/json
Authorization: Bearer <token>

{
  "clientId": "uuid-here",
  "postType": "recipe",
  "status": "draft",
  "title": "...",
  "excerpt": "...",
  "content": "...",
  ...
}
```

#### Get My Drafts
```
GET /api/posts/mine/drafts?page=1&page_size=20
Authorization: Bearer <token>

Response:
{
  "results": [...],
  "next": "..."
}
```

## Dependencies Added

### Android (build.gradle.kts)
```kotlin
// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("androidx.hilt:hilt-work:1.1.0")
kapt("androidx.hilt:hilt-compiler:1.1.0")
```

## Build Instructions

### Server
```bash
cd project/server
./mvnw clean install -DskipTests
```

### Android
```bash
cd project/mobile
./gradlew assembleDebug
```

## Future Enhancements

1. **Conflict Resolution**: Handle cases where post edited on multiple devices
2. **Partial Sync**: Sync only changed fields rather than full post
3. **Batch Sync**: Optimize by syncing multiple drafts in single request
4. **Sync Status UI**: Show sync progress in drafts list
5. **Manual Sync**: Allow user to trigger sync via button
6. **Sync Statistics**: Track sync success/failure rates
7. **Image Sync**: Handle offline image uploads and sync
8. **Deletion Sync**: Sync draft deletions to server

## Notes

- AGP version set to 8.3.0 for compatibility (may need adjustment based on environment)
- Room database migration tested for version 1 to 2
- WorkManager configured with Hilt for dependency injection
- All sync operations happen on background thread (Dispatchers.IO)
- Server migration V3 is idempotent and safe to re-run
