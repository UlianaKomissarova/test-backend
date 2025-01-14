package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable
                .select { BudgetTable.year eq param.year }
                .orderBy(BudgetTable.month to SortOrder.ASC)
                .orderBy(BudgetTable.amount to SortOrder.DESC)

            val total = query.count()

            val allItems = BudgetEntity
                .wrapRows(query)
                .map { it.toResponse() }

            val sumByType = allItems
                .groupBy { it.type.name }
                .mapValues { it.value.sumOf { v -> v.amount } }

            val paginatedItems = allItems
                .drop(param.offset)
                .take(param.limit)

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = paginatedItems
            )
        }
    }
}