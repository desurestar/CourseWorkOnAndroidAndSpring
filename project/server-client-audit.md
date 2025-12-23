# Аудит связей между сервером и клиентом

## Какой клиент
- Android-приложение на **Kotlin** с Hilt, Retrofit и Room (пакет `project/mobile/app/src/main/java/ru/zagrebin/culinaryblog`).
- Базовый URL задаётся в `NetworkModule.BASE_URL = "http://192.168.4.103:8080/api/"`.
- Экранная логика: список/лента (`MainActivity` + `PostViewModel`), деталка (`PostDetailActivity`), создание/редактирование (`CreatePostActivity`/`CreatePostFragment` + `CreatePostViewModel`), профиль (`ProfileActivity`/`ProfileFragment`/`PublicProfileFragment`), черновики, а также административные экраны (`AdminPanelActivity` + фрагменты `Admin*`).

## Эндпоинты, которые реально использует клиент
| Контроллер/метод | HTTP + URL | Где используется в клиенте | Назначение |
| --- | --- | --- | --- |
| `PostController.listPublished` | GET `/api/posts` + query `page,page_size,post_type,cooking_time_min/max,calories_min/max,tags` | `PostRepositoryImpl.getPublishedPosts()` → `PostViewModel` (лента, профиль, публичный профиль) | Лента опубликованных постов с фильтрами |
| `PostController.getFull` | GET `/api/posts/{id}` (+ optional `currentUserId`) | `PostRepositoryImpl.getPost()` → `PostDetailActivity` | Детальная карточка поста |
| `PostController.create` | POST `/api/posts` | `PostRepositoryImpl.createPost()` → `CreatePostViewModel`/`CreatePostFragment` | Создание поста |
| `PostController.update` | PUT `/api/posts/{id}` | `PostRepositoryImpl.updatePost()` → `CreatePostViewModel` (редактирование) | Обновление поста |
| `PostController.delete` | DELETE `/api/posts/{id}` | `PostRepositoryImpl.deletePost()` → `CreatePostViewModel`/профиль | Удаление поста |
| `PostController.like` | POST `/api/posts/{id}/like` | `PostRepositoryImpl.like()` → `PostDetailActivity`/карточки | Ставит лайк |
| `PostController.unlike` | DELETE `/api/posts/{id}/like` | `PostRepositoryImpl.unlike()` → те же экраны | Снимает лайк |
| `TagController.list` | GET `/api/tags` | `PostRepositoryImpl.getTags()` → выбор тегов в `CreatePostFragment`; `AdminRepositoryImpl.getTags()` → `AdminTagsFragment` | Справочник тегов/поиск |
| `IngredientController.list` | GET `/api/ingredients` | `PostRepositoryImpl.getIngredients()` → `CreatePostFragment`; `AdminRepositoryImpl.getIngredients()` → `AdminIngredientsFragment` | Справочник ингредиентов/поиск |
| `UploadController.upload` | POST `/api/uploads/{type}` (multipart) | `PostRepositoryImpl.uploadImage()` → `CreatePostFragment` (обложка/шаги); `ProfileRepositoryImpl.uploadAvatar()` | Загрузка файлов |
| `AuthController.register` | POST `/api/auth/register` | `AuthRepository.register()` → `AuthViewModel` (экран регистрации) | Регистрация |
| `AuthController.login` | POST `/api/auth/login` | `AuthRepository.login()` → `AuthViewModel` (логин) | Аутентификация |
| `AuthController.me` | GET `/api/auth/me` | `ProfileRepositoryImpl.getProfile()` → `ProfileActivity`/`ProfileFragment` | Профиль текущего пользователя |
| `AuthController.updateMe` | PUT `/api/auth/me` | `ProfileRepositoryImpl.updateProfile()` → `ProfileActivity` | Обновление профиля |
| `UserController.getUser` | GET `/api/users/{id}` | `ProfileRepositoryImpl.getUserProfile()` → `PublicProfileFragment` | Просмотр чужого профиля |
| `UserController.subscribe` | POST `/api/users/{id}/subscribe` | `ProfileRepositoryImpl.subscribe()` → `PublicProfileFragment`/`PostDetailActivity` | Подписка на автора |
| `UserController.unsubscribe` | DELETE `/api/users/{id}/subscribe` | `ProfileRepositoryImpl.unsubscribe()` → те же экраны | Отписка |
| `UserController.getStatus` | GET `/api/users/{id}/subscription` | `ProfileRepositoryImpl.getSubscription()` → `PublicProfileFragment`/`PostDetailActivity` | Статус подписки |
| `UserController.getFollowers` | GET `/api/users/{id}/followers` | `ProfileRepositoryImpl.getFollowers()` → `PublicProfileFragment` (список подписчиков) | Подписчики |
| `UserController.getFollowing` | GET `/api/users/{id}/following` | `ProfileRepositoryImpl.getFollowing()` → `PublicProfileFragment` (список подписок) | Подписки |
| `AdminController.listPosts` | GET `/api/admin/posts` | `AdminRepositoryImpl.getPosts()` → `AdminPostsFragment` | Модерация постов |
| `AdminController.updatePostStatus` | PUT `/api/admin/posts/{id}/status` | `AdminRepositoryImpl.updatePostStatus()` → `AdminPostsFragment` | Изменение статуса поста |
| `AdminController.deletePost` | DELETE `/api/admin/posts/{id}` | `AdminRepositoryImpl.deletePost()` → `AdminPostsFragment` | Удаление поста (админ) |
| `AdminController.listIngredients` | GET `/api/admin/ingredients` | `AdminRepositoryImpl.getIngredients()` → `AdminIngredientsFragment` | Справочник ингредиентов (админ) |
| `AdminController.createIngredient` | POST `/api/admin/ingredients` | `AdminRepositoryImpl.createIngredient()` → `AdminIngredientsFragment` | Создание ингредиента |
| `AdminController.deleteIngredient` | DELETE `/api/admin/ingredients/{id}` | `AdminRepositoryImpl.deleteIngredient()` → `AdminIngredientsFragment` | Удаление ингредиента |
| `AdminController.listTags` | GET `/api/admin/tags` | `AdminRepositoryImpl.getTags()` → `AdminTagsFragment` | Справочник тегов (админ) |
| `AdminController.createTag` | POST `/api/admin/tags` | `AdminRepositoryImpl.createTag()` → `AdminTagsFragment` | Создание тега |
| `AdminController.deleteTag` | DELETE `/api/admin/tags/{id}` | `AdminRepositoryImpl.deleteTag()` → `AdminTagsFragment` | Удаление тега |
| `AdminController.listUsers` | GET `/api/admin/users` | `AdminRepositoryImpl.getUsers()` → `AdminUsersFragment` | Список пользователей (админ) |
| `AdminController.updateUserRole` | PUT `/api/admin/users/{id}/role` | `AdminRepositoryImpl.updateUserRole()` → `AdminUsersFragment` | Смена роли |
| `AdminController.deleteUser` | DELETE `/api/admin/users/{id}` | `AdminRepositoryImpl.deleteUser()` → `AdminUsersFragment` | Удаление пользователя |
| Статические файлы `/media/**` | GET `/media/**` | Все экраны через `resolveUrl` в мапперах DTO | Доставка обложек, аватаров, шагов рецептов |

### Эндпоинты, не используемые клиентом (кандидаты на удаление/внутренние)
- В контроллерах публичных/админских эндпоинтов лишних методов нет — все маршруты дергает мобильное приложение.
- В сервисном слое есть незадействованные элементы: `PostService.getAllPublishedPosts()` и `PostRepository.findIdsByStatusOrderByCreatedAtDesc(...)` не вызываются контроллерами; `PostSummaryDto` не используется нигде; `LikeService.countLikes()` не вызывается. Можно убрать/пометить как internal util.
- В мобильном коде класс `data.remote.dto.PostDto` не используется ни одним API/репозиторием — можно удалить, чтобы не путать его с рабочими DTO.

## Предлагаемые упрощения и удаления
- **DTO/контракты**:  
  - Убрать из `PostCreateDto`/`PostCreateRequest` поле `authorId` — бэкенд уже берет автора из `UserPrincipal`. Это снизит риск подмены автора.  
  - Оставить два основных DTO для вывода: `PostCardDto` (списки) и `PostFullDto` (деталка). `PostSummaryDto` можно удалить.  
  - Выравнять булевые флаги в JSON: хранить поля как `liked` и `subscribed` (без префикса `is`) и удостовериться, что Lombok/record генерирует их именно так. Это упростит сериализацию в Kotlin-модели (`liked: Boolean`, `subscribed: Boolean`).
- **Дубли**: админские справочники тегов/ингредиентов дублируют публичные. Можно оставить публичные GET, а админским POST/DELETE явно префиксировать как internal (уже под `/api/admin/**`).
- **Мертвый код**: удалить `PostService.getAllPublishedPosts`, `PostRepository.findIdsByStatusOrderByCreatedAtDesc`, `PostSummaryDto`, `LikeService.countLikes` и их имплементации, если они не используются после проверки.

## Приведение типов к дженерикам / best practices
- Поиск по пакету `ru.zagrebin` не выявил raw-типов (`List`, `Set`, `Page` без параметров). Все основные коллекции уже параметризованы.  
- Рекомендация: при удалении `getAllPublishedPosts` также убрать возврат необобщённого `List` в старом методе, чтобы не плодить новые raw-типы, и придерживаться сигнатур вида `Page<PostCardDto>`/`List<PostCardDto>`.

## Упрощение Security/CORS
- Фактический клиент — Android-приложение, для него CORS не нужен. Разрешить только необходимые origins через свойство `app.cors.allowed-origins`; по умолчанию можно оставить `http://localhost:3000` и `http://192.168.4.103:8080` (или `10.0.2.2`) для отладки, остальное убрать.
- Явно зафиксировать в `SecurityConfig` правило `requestMatchers("/api/admin/**").hasRole("ADMIN")` (сейчас ограничение обеспечивается аннотацией `@PreAuthorize` в контроллере, но дублирование на уровне http-мэчинга упростит чтение).
- Оставить открытыми только `GET /api/posts/**`, `GET /api/tags/**`, `GET /api/ingredients/**`, `/media/**`, `/api/auth/**`; все остальные маршруты — только при наличии JWT.
