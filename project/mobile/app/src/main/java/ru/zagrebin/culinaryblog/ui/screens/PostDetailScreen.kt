package ru.zagrebin.culinaryblog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.viewmodel.PostViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    viewModel: PostViewModel = hiltViewModel()
    ) {
    val uiState by viewModel.uiState.collectAsState()
    val post = uiState.posts.firstOrNull { it.id == postId }
    val displayPost = post ?: samplePost(postId)
    val comments = remember { sampleComments() }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        displayPost.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (uiState.isLoading && post == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }
            AuthorBlock(displayPost)
            Spacer(modifier = Modifier.height(16.dp))
            if (!displayPost.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = displayPost.coverUrl,
                    contentDescription = displayPost.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (!displayPost.excerpt.isNullOrBlank()) {
                Text(
                    text = displayPost.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = buildContentText(displayPost),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            CommentSection(comments)
        }
    }
}

@Composable
private fun AuthorBlock(post: PostCard) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.authorName ?: "Автор",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = post.publishedAt ?: "Сегодня",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "${post.likesCount}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ArticlesScreen(onOpenPost: (Long) -> Unit) {
    val sampleArticles = remember {
        listOf(
            PostCard(
                id = 501,
                title = "Как организовать кухню мечты",
                excerpt = "Подборка советов по хранению, свету и технике, которая экономит время.",
                coverUrl = "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=900&q=60",
                authorId = null,
                likesCount = 124,
                cookingTimeMinutes = null,
                calories = null,
                authorName = "Анна Куликова",
                publishedAt = "3 часа назад",
                tags = setOf("лайфхаки", "кухня"),
                viewsCount = 1800
            ),
            PostCard(
                id = 502,
                title = "5 ошибок при выпечке хлеба",
                excerpt = "Рассказываем, почему хлеб не поднимается и как сделать ароматную корочку.",
                coverUrl = "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=900&q=60",
                authorId = null,
                likesCount = 98,
                cookingTimeMinutes = null,
                calories = null,
                authorName = "Дмитрий Пекарь",
                publishedAt = "Вчера",
                tags = setOf("выпечка", "хлеб"),
                viewsCount = 1420
            )
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        items(sampleArticles, key = { it.id }) { article ->
            PostCardItem(post = article) { onOpenPost(article.id) }
        }
    }
}

@Composable
fun CreatePostScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Создать новый пост", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Выберите формат, чтобы поделиться рецептом или полезной статьёй.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PostTypeCard(
                title = "Рецепт",
                description = "Фото, шаги, ингредиенты",
                icon = Icons.Outlined.ReceiptLong
            )
            PostTypeCard(
                title = "Статья",
                description = "Советы, обзоры и заметки",
                icon = Icons.Outlined.Create
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        FilledTonalButton(
            onClick = {},
            enabled = false,
            colors = ButtonDefaults.filledTonalButtonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Редактор появится позднее")
        }
    }
}

@Composable
fun MessengerStubScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Send,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Мессенджер в разработке", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Пока доступен только просмотр, позже здесь появятся чаты и звонки.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ProfileScreen() {
    val profileSections = remember {
        listOf(
            ProfileSection("Подписки", listOf("Шеф Алина", "BBQ мастер Руслан", "healthyfood_ru")),
            ProfileSection("Подписчики", listOf("Мария", "Алексей", "foodie_roma", "Оля")),
            ProfileSection("Мои посты", listOf("Паста с лососем", "Воздушные сырники", "Тыквенный латте")),
            ProfileSection("Понравившиеся", listOf("Томленые ребра", "Глинтвейн без алкоголя"))
        )
    }
    var selectedSection by remember { mutableStateOf<ProfileSection?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Алексей Иванов", style = MaterialTheme.typography.titleMedium)
                    Text("@chef_ivanov", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Домашний шеф, люблю азиатскую кухню и экспериментирую со специями.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(onClick = { /*edit profile*/ }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Редактировать профиль")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            profileSections.forEach { section ->
                ProfileStatCard(
                    title = section.title,
                    value = section.items.size.toString(),
                    onClick = { selectedSection = section },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Активность", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivityChip("Недавно публиковали рецепт")
                    ActivityChip("5 комментариев за неделю")
                }
                ActivityChip("12 сохранённых подборок")
            }
        }
    }

    selectedSection?.let { section ->
        AlertDialog(
            onDismissRequest = { selectedSection = null },
            title = { Text(section.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    section.items.forEach { item ->
                        Text("• $item")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSection = null }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun ProfileStatCard(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ActivityChip(text: String) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PostTypeCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ElevatedCard(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommentSection(comments: List<CommentUi>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Комментарии", style = MaterialTheme.typography.titleMedium)
        comments.forEach { comment ->
            CommentItem(comment = comment)
        }
    }
}

@Composable
private fun CommentItem(comment: CommentUi, depth: Int = 0) {
    var showAllReplies by rememberSaveable(comment.id) { mutableStateOf(false) }
    val repliesToShow = if (showAllReplies) comment.replies else comment.replies.take(3)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = (depth * 12).dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(comment.author, fontWeight = FontWeight.SemiBold)
                Text(
                    comment.timestamp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(comment.message, style = MaterialTheme.typography.bodyMedium)
        repliesToShow.forEach { reply ->
            CommentItem(comment = reply, depth = depth + 1)
        }
        if (comment.replies.size > 3 && !showAllReplies) {
            TextButton(onClick = { showAllReplies = true }) {
                Text("Просмотреть все")
            }
        }
    }
}

private fun buildContentText(post: PostCard): String {
    val base = """
        ${post.title}

        ${post.excerpt ?: "Этот пост пока без краткого описания, но ниже собран полный текст."}

        Подготовьте продукты заранее, чтобы процесс прошёл без суеты. Используйте свежие ингредиенты и уделите внимание базовым техникам: аккуратно нарезайте, соблюдайте температуру и не забывайте о времени на отдых блюда.

        Добавьте свои специи или замените ингредиенты на те, что есть под рукой — в конце приведены советы по вариациям. Не бойтесь экспериментировать: так рождаются любимые рецепты.

        Подавайте красиво: тёплую тарелку, зелень, немного оливкового масла или семян сделают блюдо ресторанным.
    """.trimIndent()
    return base
}

private fun samplePost(id: Long) = PostCard(
    id = id,
    title = "Домашний рецепт #$id",
    excerpt = "Нежный рецепт, который можно приготовить за один вечер без сложных шагов.",
    coverUrl = "https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=1000&q=60",
    authorId = null,
    likesCount = 245,
    cookingTimeMinutes = 45,
    calories = 380,
    authorName = "Кулинар Мария",
    publishedAt = "Сегодня",
    tags = setOf("домашнее", "ужин"),
    viewsCount = 2300
)

private fun sampleComments(): List<CommentUi> = listOf(
    CommentUi(
        id = 1,
        author = "Мария",
        message = "Приготовила по этому рецепту — получилось очень нежно! Добавила немного розмарина.",
        timestamp = "2 часа назад",
        replies = listOf(
            CommentUi(id = 11, author = "Автор", message = "Спасибо! Розмарин отлично подходит 💚", timestamp = "1 час назад"),
            CommentUi(id = 12, author = "Илья", message = "Как думаете, можно заменить сливки на кокосовые?", timestamp = "58 минут назад"),
            CommentUi(id = 13, author = "Мария", message = "Да, я делала на кокосовых — вкус интересный!", timestamp = "47 минут назад"),
            CommentUi(id = 14, author = "Автор", message = "Главное не переборщить, чтобы не было слишком сладко.", timestamp = "35 минут назад")
        )
    ),
    CommentUi(
        id = 2,
        author = "Светлана",
        message = "Подскажите, сколько хранится в холодильнике?",
        timestamp = "Вчера",
        replies = listOf(
            CommentUi(id = 21, author = "Автор", message = "Лучше съесть за 2 дня, иначе теряется текстура.", timestamp = "Вчера")
        )
    ),
    CommentUi(
        id = 3,
        author = "Кирилл",
        message = "Делал на даче, гости остались в восторге. Спасибо за подробное описание!",
        timestamp = "Неделю назад"
    )
)

private data class CommentUi(
    val id: Long,
    val author: String,
    val message: String,
    val timestamp: String,
    val replies: List<CommentUi> = emptyList()
)

private data class ProfileSection(
    val title: String,
    val items: List<String>
)
