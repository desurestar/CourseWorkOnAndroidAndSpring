package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.PostCard

class OfflineCacheException(val cached: List<PostCard>) : Exception("OFFLINE_CACHE")
