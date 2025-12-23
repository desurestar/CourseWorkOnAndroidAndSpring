# Draft Synchronization Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            User Actions                                  │
│  • App Start  • Login  • Network Restore  • Pull-to-Refresh  • Periodic │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Trigger Points                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ 1. CulinaryBlogApp.onCreate() → schedulePeriodicSync()                  │
│ 2. MainActivity.onResume() → setCurrentUser() → refreshDrafts(sync)     │
│ 3. NetworkMonitor → triggerImmediateSync()                              │
│ 4. DraftsFragment.refreshContent() → refreshDrafts(sync)                │
│ 5. WorkManager → DraftSyncWorker (every 6h)                             │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      PostViewModel                                       │
├─────────────────────────────────────────────────────────────────────────┤
│ • refreshDrafts(sync: Boolean)                                           │
│   ├─ if sync: syncDrafts(userId)  ─────────┐                           │
│   ├─ loadDrafts(userId) ──────────┐        │                            │
│   └─ loadServerDrafts() ──────┐   │        │                            │
│                               │   │        │                            │
│ PostsUiState:                 │   │        │                            │
│  • serverDrafts: List<PostCard>   │        │                            │
│  • drafts: List<PostDraft>        │        │                            │
│  • offline: Boolean               │        │                            │
└───────────────────────────────────┼────────┼────────────────────────────┘
                                    │        │        │
                                    ▼        ▼        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    PostRepository / PostRepositoryImpl                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  getServerDrafts()                getDrafts()           syncDrafts()    │
│      │                                 │                      │          │
│      │                                 │                      │          │
│      ▼                                 ▼                      ▼          │
│  ┌────────────┐                  ┌──────────┐          ┌────────────┐  │
│  │   PostApi  │                  │ DraftDao │          │  PostApi   │  │
│  │  (Retrofit)│                  │  (Room)  │          │ (Retrofit) │  │
│  └─────┬──────┘                  └────┬─────┘          └─────┬──────┘  │
│        │                              │                       │          │
│        │                              │                       │          │
└────────┼──────────────────────────────┼───────────────────────┼──────────┘
         │                              │                       │
         ▼                              ▼                       ▼
┌────────────────┐            ┌────────────────┐      ┌────────────────┐
│  Server API    │            │   Local DB     │      │   Server API   │
│ GET /posts/    │            │   (Room)       │      │ POST /posts    │
│  mine/drafts   │            │                │      │ (create draft) │
└────────────────┘            └────────────────┘      └────────────────┘
         │                              │                       │
         │                              │                       │
         └──────────────┬───────────────┴───────────────────────┘
                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         DraftsFragment                                   │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐        │
│  │ Header: "ЧЕРНОВИКИ НА СЕРВЕРЕ"                             │        │
│  ├────────────────────────────────────────────────────────────┤        │
│  │ [Server Draft 1] (read-only)                               │        │
│  │ [Server Draft 2] (read-only)                               │        │
│  └────────────────────────────────────────────────────────────┘        │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────┐        │
│  │ Header: "ЛОКАЛЬНЫЕ ЧЕРНОВИКИ"                              │        │
│  ├────────────────────────────────────────────────────────────┤        │
│  │ [Local Draft 1] ✓ (synced, editable)                       │        │
│  │ [Local Draft 2] ⟳ (syncing, editable)                      │        │
│  │ [Local Draft 3] ⚠ (failed, editable)                       │        │
│  │ [Local Draft 4] ⏸ (offline, editable)                      │        │
│  │ [Local Draft 5] ⋯ (pending, editable)                      │        │
│  └────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    Background Sync (WorkManager)                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  DraftSyncScheduler                                                      │
│    ├─ schedulePeriodicSync() → every 6h with network constraint         │
│    └─ triggerImmediateSync() → one-time immediate                       │
│                          │                                               │
│                          ▼                                               │
│              ┌─────────────────────────┐                                │
│              │   DraftSyncWorker       │                                │
│              ├─────────────────────────┤                                │
│              │ 1. Get currentUserId    │                                │
│              │ 2. Call syncDrafts()    │                                │
│              │ 3. Handle result        │                                │
│              └─────────────────────────┘                                │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      Network Monitoring                                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  NetworkMonitor (in MainActivity)                                        │
│    • Observes connectivity changes                                      │
│    • Flow<Boolean> emits on network state change                        │
│    • On true (connected): triggers immediate sync                       │
│                                                                          │
│  ConnectivityManager.NetworkCallback                                    │
│    • onAvailable() → emit true                                          │
│    • onLost() → emit false                                              │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                      Authorization                                       │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  NetworkModule.provideAuthInterceptor()                                 │
│    • Intercepts all Retrofit requests                                   │
│    • Adds: Authorization: Bearer <token>                                │
│    • Token from TokenStorage                                            │
│    • Applied to: GET /posts/mine/drafts, POST /posts                   │
│                                                                          │
│  HttpLoggingInterceptor                                                 │
│    • Redacts Authorization header in logs                               │
│    • Logs request/response bodies in DEBUG mode                         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘

Sync State Machine:
──────────────────
PENDING → IN_SYNC → SYNCED (success)
        ↓
        FAILED (retry on next trigger)

Offline → PENDING (when network restored)
```
