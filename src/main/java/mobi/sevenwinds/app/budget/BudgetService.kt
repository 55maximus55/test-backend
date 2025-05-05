package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorEntity
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
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
                this.authorId = body.authorId
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = if (param.name != null) (BudgetTable leftJoin AuthorTable)
                .select { BudgetTable.year eq param.year and AuthorTable.fullName.lowerCase().like("%${param.name}%")}
                .limit(param.limit, param.offset)
                .orderBy(Pair(BudgetTable.month, SortOrder.ASC), Pair(BudgetTable.amount, SortOrder.DESC))
            else {
                (BudgetTable leftJoin AuthorTable)
                    .select { BudgetTable.year eq param.year }
                    .limit(param.limit, param.offset)
                    .orderBy(Pair(BudgetTable.month, SortOrder.ASC), Pair(BudgetTable.amount, SortOrder.DESC))
            }
            val queryAll = BudgetTable
                .select { BudgetTable.year eq param.year }

            val total = queryAll.count()
            val data = BudgetEntity.wrapRows(query).map {
                val budget = it.toResponse()
                if (budget.authorId != null) {
                    val author = AuthorEntity.get(budget.authorId)
                    return@map BudgetAuthorResponseRecord(
                        budget.year,
                        budget.month,
                        budget.amount,
                        budget.type,
                        author.fullName,
                        author.dateTime.toLocalDateTime().toString()
                    )
                }
                return@map BudgetAuthorResponseRecord(budget.year, budget.month, budget.amount, budget.type, "", "")
            }
            val dataAll = BudgetEntity.wrapRows(queryAll).map { it.toResponse() }

            val sumByType = dataAll.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}