package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.TagItem

interface AdminRepository {
    suspend fun getPosts(page: Int = 1, search: String? = null): Result<PaginatedResult<AdminPost>>
    suspend fun updatePostStatus(postId: Long, status: String): Result<Unit>
    suspend fun deletePost(postId: Long): Result<Unit>

    suspend fun getTags(search: String? = null): Result<List<TagItem>>
    suspend fun createTag(name: String, slug: String?, color: String?): Result<TagItem>
    suspend fun deleteTag(id: Long): Result<Unit>

    suspend fun getIngredients(search: String? = null): Result<List<IngredientItem>>
    suspend fun createIngredient(name: String): Result<IngredientItem>
    suspend fun deleteIngredient(id: Long): Result<Unit>

    suspend fun getUsers(page: Int = 1, search: String? = null): Result<PaginatedResult<AdminUser>>
    suspend fun updateUserRole(userId: Long, role: String): Result<AdminUser>
    suspend fun deleteUser(userId: Long): Result<Unit>
}
