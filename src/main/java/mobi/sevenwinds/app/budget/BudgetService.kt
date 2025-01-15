package mobi.sevenwinds.app.budget

import io.ktor.features.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRequest): BudgetResponse = withContext(Dispatchers.IO) {
        transaction {
            val author = body.authorId?.let { authorId ->
                AuthorEntity.findById(authorId) ?: throw NotFoundException("Автор с id $authorId не найден")
            }

            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = author
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = buildFilteredOrderedQuery(param)

            val total = query.count()

            val allItems = BudgetEntity
                .wrapRows(query)
                .map { it.toResponse() }

            val sumByType = allItems
                .groupBy { it.type.name }
                .mapValues { it.value.sumOf { v -> v.amount } }

            val paginatedItems = BudgetEntity
                .wrapRows(query.limit(param.limit, param.offset))
                .map { it.toResponse() }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = paginatedItems
            )
        }
    }

    private fun buildFilteredOrderedQuery(param: BudgetYearParam): Query {
        return BudgetTable
            .join(AuthorTable, JoinType.LEFT, onColumn = BudgetTable.author)
            .select { BudgetTable.year eq param.year }
            .apply {
                param.authorName?.let {
                    andWhere { AuthorTable.fullName.lowerCase() like "%${it.toLowerCase()}%" }
                }
            }
            .orderBy(BudgetTable.month to SortOrder.ASC)
            .orderBy(BudgetTable.amount to SortOrder.DESC)
    }
}