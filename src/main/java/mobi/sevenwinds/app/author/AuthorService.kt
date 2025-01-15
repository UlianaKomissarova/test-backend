package mobi.sevenwinds.app.author

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

object AuthorService {
    suspend fun addAuthor(body: AuthorRequest): AuthorResponse = withContext(Dispatchers.IO) {
        transaction {
            require(body.fullName.isNotBlank()) { "Имя автора не может быть пустым" }

            val entity = AuthorEntity.new {
                this.fullName = body.fullName
                this.createdAt = DateTime.now()
            }

            return@transaction entity.toResponse()
        }
    }
}