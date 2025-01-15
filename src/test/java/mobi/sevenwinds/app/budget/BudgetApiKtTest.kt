package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import io.restassured.parsing.Parser
import mobi.sevenwinds.app.author.AuthorRequest
import mobi.sevenwinds.app.author.AuthorResponse
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction { BudgetTable.deleteAll() }
    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRequest(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRequest(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addRecord(BudgetRequest(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 5, 400, BudgetType.Приход))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
                Assert.assertEquals(400, response.items[2].amount)
                Assert.assertEquals(100, response.items[3].amount)
                Assert.assertEquals(50, response.items[4].amount)
            }
    }

    @Test
    fun testStatsSortOrderWithAuthorFilter() {
        val manAuthorId = createAuthor(AuthorRequest("Котлин Котлин Котлинович")).id
        val womanAuthorId = createAuthor(AuthorRequest("Джава Джава Джавовна")).id

        addRecord(BudgetRequest(2020, 5, 100, BudgetType.Приход, manAuthorId))
        addRecord(BudgetRequest(2020, 1, 5, BudgetType.Приход, womanAuthorId))
        addRecord(BudgetRequest(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRequest(2020, 1, 30, BudgetType.Приход, womanAuthorId))
        addRecord(BudgetRequest(2020, 5, 400, BudgetType.Приход))

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0&authorName=джава")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)
                Assert.assertEquals(2, response.items.size)
                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
            }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRequest(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRequest(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    @Test
    fun testAddBudgetRecordAuthorNotFound() {
        val nonExistentId = 10000
        RestAssured.given()
            .contentType("application/json")
            .jsonBody(BudgetRequest(2020, 5, 5, BudgetType.Приход, nonExistentId))
            .post("/budget/add")
            .then().statusCode(404)
    }

    private fun addRecord(request: BudgetRequest) {
        RestAssured.given()
            .jsonBody(request)
            .post("/budget/add")
            .toResponse<BudgetResponse>().let { response ->
                Assert.assertEquals(request.amount, response.amount)
                Assert.assertEquals(request.month, response.month)
                Assert.assertEquals(request.year, response.year)
                Assert.assertEquals(request.type, response.type)

                if (request.authorId != null) {
                    Assert.assertNotNull(response.author)
                    Assert.assertEquals(request.authorId, response.author?.id)
                } else {
                    Assert.assertNull(response.author)
                }
            }
    }

    private fun createAuthor(request: AuthorRequest): AuthorResponse {
        RestAssured.defaultParser = Parser.JSON
        return RestAssured.given()
            .jsonBody(request)
            .post("/author/add")
            .toResponse()
    }
}