# Android Draft Synchronization Implementation

## Overview
This implementation adds automatic draft synchronization between the Android client and the server, along with displaying server drafts in the Drafts tab.

## Problem Statement
The mobile client previously only displayed local Room drafts. This implementation:
1. Fetches and displays server drafts from `GET /api/posts/mine/drafts`
2. Automatically syncs local drafts when internet is available
3. Triggers sync on login, network restoration, and periodically in background
4. Displays both server and local drafts with sync state indicators

## Changes Made

### 1. Repository Layer

#### PostRepository.kt
- Added `getServerDrafts(): Result<List<PostCard>>` method to interface

#### PostRepositoryImpl.kt
- Implemented `getServerDrafts()` to fetch drafts from server endpoint
- Enhanced `syncDrafts()` with comprehensive logging for debugging
- Logs include:
  - Draft sync start/completion
  - Individual draft sync attempts with clientId
  - Server response codes on failure
  - Exception details

### 2. ViewModel Layer

#### PostViewModel.kt
- Updated `PostsUiState` data class:
  - Added `serverDrafts: List<PostCard> = emptyList()`
- Modified `loadPosts()`:
  - Fetches server drafts alongside local drafts
  - Updates UI state with both lists
- Updated `refreshDrafts()`:
  - Fetches both local and server drafts
  - Updates both in UI state simultaneously
- Added `loadServerDrafts()` helper method

### 3. UI Layer

#### DraftsFragment.kt
- Complete redesign of draft rendering:
  - Displays two sections: "Server Drafts" and "Local Drafts"
  - Server drafts shown as read-only cards
  - Local drafts show sync state indicators:
    - ✓ = SYNCED (successfully synced to server)
    - ⟳ = IN_SYNC (currently syncing)
    - ⚠ = FAILED (sync failed)
    - ⏸ = Offline (pending sync, no network)
    - ⋯ = PENDING (waiting to sync)
- Added `addSectionHeader()`, `addServerDraftCard()`, `addLocalDraftCard()` methods
- Local drafts remain clickable for editing
- Offline state passed to display appropriate indicators

#### New Layout: item_section_header.xml
- Simple TextView layout for section headers
- Styled with bold, uppercase text
- Consistent padding for visual hierarchy

#### strings.xml
- Added `server_drafts_header`: "Черновики на сервере"
- Added `local_drafts_header`: "Локальные черновики"

### 4. Network Monitoring

#### NetworkMonitor.kt (New)
- Utility class for observing network connectivity
- Uses `ConnectivityManager.NetworkCallback` API
- Returns a `Flow<Boolean>` for reactive connectivity updates
- Monitors `NET_CAPABILITY_INTERNET` and `NET_CAPABILITY_VALIDATED`
- Provides `isNetworkAvailable()` for one-time checks
- Includes comprehensive logging of network state changes

#### MainActivity.kt
- Added network connectivity observer in `onCreate()`
- On network restoration, triggers `DraftSyncScheduler.triggerImmediateSync()`
- Lifecycle-aware (only observes when STARTED)

### 5. Background Sync (Existing, Verified)

#### CulinaryBlogApp.kt
- Already schedules periodic sync on app start
- Calls `DraftSyncScheduler.schedulePeriodicSync(this)` in `onCreate()`

#### DraftSyncScheduler.kt
- `schedulePeriodicSync()`: Runs every 6 hours with 15-minute flex interval
- `triggerImmediateSync()`: Triggers one-time sync immediately
- Both use network constraints (CONNECTED)
- Exponential backoff on failure

#### DraftSyncWorker.kt
- Fetches current user ID from `AuthRepository`
- Calls `postRepository.syncDrafts(userId)`
- Returns `Result.retry()` on network/server errors
- Returns `Result.failure()` on other errors
- Returns `Result.success()` on successful sync

### 6. Authorization

#### NetworkModule.kt (Existing)
- `AuthInterceptor` already adds `Authorization: Bearer <token>` header
- Token retrieved from `TokenStorage`
- Applied to all API requests including `/api/posts/mine/drafts`
- Logger redacts Authorization header for security

## Automatic Synchronization Triggers

1. **App Start**: `CulinaryBlogApp.onCreate()` schedules periodic WorkManager task
2. **User Login**: `MainActivity.onResume()` → `setCurrentUser()` → `refreshDrafts(sync=true)`
3. **Network Restoration**: `NetworkMonitor` detects connection → `triggerImmediateSync()`
4. **Periodic Background**: WorkManager runs every 6 hours when network available
5. **Manual Refresh**: Pull-to-refresh on Drafts tab calls `refreshDrafts(sync=true)`

## Offline Handling

- When offline, only local drafts are displayed
- Local drafts show offline indicator (⏸)
- No error messages for expected offline behavior
- Server drafts remain cached in UI state until next online fetch
- Sync attempts are queued and execute when network becomes available

## Error Handling & Debugging

### Logging Points
1. `NetworkMonitor`: Network state changes
2. `PostRepositoryImpl.syncDrafts()`: 
   - Number of pending drafts
   - Individual sync attempts with clientId
   - Success/failure per draft
   - Final sync result
3. `PostRepositoryImpl.getServerDrafts()`:
   - Fetch attempt
   - Response body validation
   - Number of drafts fetched
   - Errors with status codes
4. `MainActivity`: Network connectivity changes
5. `DraftSyncWorker`: Sync start, user validation, results

### Error States
- **401 Unauthorized**: Logged in repository, sync marked as FAILED
- **Network Error**: Sync marked as FAILED, will retry on next trigger
- **Empty Response**: Logged as warning, returns empty list
- **Exception**: Logged with stack trace, sync marked as FAILED

## Testing Recommendations

### Manual Testing
1. **Server Drafts Display**:
   - Log in with user who has server drafts
   - Navigate to Drafts tab
   - Verify "Черновики на сервере" section appears
   - Verify drafts match server data

2. **Local Draft Sync**:
   - Create local draft offline
   - Connect to network
   - Wait ~10 seconds or pull-to-refresh
   - Verify sync indicator changes from ⋯ to ⟳ to ✓
   - Check logcat for sync logs

3. **Offline Mode**:
   - Disconnect network
   - Navigate to Drafts tab
   - Verify local drafts show ⏸ indicator
   - Verify no error toast/dialog

4. **Network Restoration**:
   - Create local draft offline
   - Connect network
   - Check logcat for "Network connected, triggering draft sync"
   - Verify sync completes within ~30 seconds

5. **Authorization**:
   - Use network interceptor logs
   - Verify `Authorization: Bearer` header present on `/api/posts/mine/drafts`
   - Verify 401 responses are logged appropriately

### LogCat Filters
```
tag:DraftSyncWorker | tag:PostRepositoryImpl | tag:NetworkMonitor | tag:MainActivity
```

## Files Modified

1. `PostRepository.kt` - Added interface method
2. `PostRepositoryImpl.kt` - Implemented server drafts fetch and enhanced logging
3. `PostViewModel.kt` - Updated state and fetch logic
4. `DraftsFragment.kt` - Redesigned UI for two sections
5. `MainActivity.kt` - Added network monitoring
6. `NetworkMonitor.kt` - New utility class
7. `item_section_header.xml` - New layout
8. `strings.xml` - Added new strings
9. `.gitignore` - Added *.class and META-INF/ patterns

## Dependencies
No new dependencies added. Uses existing:
- WorkManager (already present)
- Hilt for DI (already configured)
- Retrofit/OkHttp for networking (already configured)
- Kotlin Coroutines & Flow (already present)

## Known Limitations
1. Sync state indicators are simple text symbols (not animated)
2. No progress indicator during sync (happens in background)
3. No explicit "sync now" button (only pull-to-refresh)

## Future Enhancements
1. Animated sync indicators
2. Explicit "Sync All" button
3. Push notifications on successful sync
4. Conflict resolution if same draft modified locally and on server
