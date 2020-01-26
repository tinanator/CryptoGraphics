
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import javafx.application.Application
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.apache.http.NameValuePair
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import java.io.IOException
import java.net.URISyntaxException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap


class Coin {
     val id : String = ""
     var name : String = ""
     var priceUsd : String = ""
     var col : Color = Color.BLACK

 }

class Point {
    val priceUsd : String = ""
    val time : String = ""
}

class Coins<Coin> {
    val coin = Coin()
}

class CoinGraph : Application() {

    @Throws(URISyntaxException::class, IOException::class)
    fun makeAPICall(uri: String, parameters: List<NameValuePair>): String {
        var response_content = ""

        val query = URIBuilder(uri)
        query.addParameters(parameters)

        val client = HttpClients.createDefault()
        val request = HttpGet(query.build())
        val response = client.execute(request)

        try {
            System.out.println(response.getStatusLine())
            val entity = response.getEntity()
            response_content = EntityUtils.toString(entity)
            EntityUtils.consume(entity)
        } finally {

        }
        return response_content
    }

    private fun fromUnixToDate(p : Point) : String {
        val newDate = Date(p.time.toLong())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val formattedDate = dateFormat.format(newDate);
        return formattedDate
    }

    private fun drawGraph(points : MutableList<Point>, lineChart:LineChart<String, Number>) : LineChart<String, Number> {
        val series = XYChart.Series<String, Number>()
        for (p in points) {
            val formattedDate : String = fromUnixToDate(p)
            series.data.add(XYChart.Data(formattedDate, p.priceUsd.toDouble()))
        }
        //if (lineChart.opacity == 0) {}
        lineChart.data.removeAt(0)
        lineChart.data[0] = series
        return lineChart
    }

    private fun makeListOfCoinsFromJson(baseURL : String) : List<Coin> {
        val parameters = ArrayList<NameValuePair>()
        val jsonFile = makeAPICall(baseURL, parameters)
        val gson = Gson()
        val allCoinsJson = gson.fromJson(jsonFile, JsonObject::class.java)["data"]

        val moneyFormat = DecimalFormat("$0.00")

        val collectionType = object : TypeToken<List<Coin>>() {}.type
        val coins: List<Coin> = gson.fromJson(allCoinsJson, collectionType)
        for (c in coins) {
            c.priceUsd = moneyFormat.format(c.priceUsd.toBigDecimal())
        }
        return coins
    }

    private fun setParameters (start : Date, end : Date, interval : String,
                               parameters : ArrayList<NameValuePair>) : ArrayList<NameValuePair> {

        setParameters(interval, parameters)

        val startMillSec: Long = start.time
        val endMillSec: Long = end.time

        parameters.add(BasicNameValuePair("start", startMillSec.toString()))
        parameters.add(BasicNameValuePair("end", endMillSec.toString()))
        return parameters
    }

    private fun setParameters (interval : String, parameters : ArrayList<NameValuePair>) {
        parameters.add(BasicNameValuePair("interval", interval))
    }

    private  fun makeListOfPoints (pricesInIntervalJsn : String) : MutableList<Point> {
        val gson = Gson()
        val jsn =  gson.fromJson(pricesInIntervalJsn, JsonObject::class.java)["data"]
        val collectionType = object : TypeToken<MutableList<Point>>() {}.type
        return gson.fromJson(jsn, collectionType)
    }
    
    override fun start(primaryStage : Stage) {

        val selectedCoins : MutableMap<String, MutableList<Point>> = HashMap()
        val xAxis = CategoryAxis()
        val yAxis = NumberAxis()
        xAxis.label = "interval";
        val lineChart:LineChart<String, Number> = LineChart(xAxis, yAxis)

        val bp = BorderPane()
        val baseURL : String = "https://api.coincap.io/v2/assets"

        val parameters = ArrayList<NameValuePair>()
        var coinId = "bitcoin"

        try {
            val coins : List<Coin> = makeListOfCoinsFromJson(baseURL)

            var start : Date = Date(2019 - 1900, 11, 1, 0, 0, 0)
            var end : Date = Date(2020 - 1900, 1,1,0,0,0)

            val table = object  : TableView<Coin>(FXCollections.observableArrayList(coins)) {
                init {
                    val column1 = TableColumn<Coin, String>("id")
                    val column2 = TableColumn<Coin, String>("name")
                    val column3 = TableColumn<Coin, String>("priceUsd")
                    val column4 = TableColumn<Coin, ColorPicker>("ColorPicker")
                    column1.cellValueFactory = PropertyValueFactory<Coin, String>("id")
                    column2.cellValueFactory = PropertyValueFactory<Coin, String>("name")
                    column3.cellValueFactory = PropertyValueFactory<Coin, String>("priceUsd")
                    //column4.cellValueFactory = PropertyValueFactory<Coin, ColorPicker>()
                    columns.addAll(column2, column3, column4)
                }
            }
            bp.bottom = table
            bp.top = lineChart

            table.setOnMousePressed {
                if (it.clickCount == 2) {
                    coinId = coins[table.selectionModel.focusedIndex].id
                    parameters.clear()
                    val pricesInInterval = makeAPICall("$baseURL/$coinId/history?", setParameters(start, end, "d1", parameters))
                    selectedCoins[coinId] = makeListOfPoints(pricesInInterval)
                    val series = XYChart.Series<String, Number>()
                    series.name = coinId
                    for (p in selectedCoins[coinId]!!) {
                        val formattedDate : String = fromUnixToDate(p)
                        series.data.add(XYChart.Data(formattedDate, p.priceUsd.toDouble()))
                    }
                    lineChart.data.add(series)
                    bp.top = lineChart
                }
            }

            val startYearLabel : Label = Label("Start Date Year:")
            val startYear : TextField = TextField("2015")
            startYear.setMaxSize(100.0, 10.0)
            val startMonthLabel : Label = Label("Start Date Month:")
            val startMonth : TextField = TextField("11")
            startMonth.setMaxSize(100.0, 10.0)
            val startDayLabel : Label = Label("Start Date Day:")
            val startDay : TextField = TextField("28")
            startDay.setMaxSize(100.0, 10.0)


            val endYearLabel : Label = Label("End Date Year:")
            val endYear = TextField("2016")
            endYear.setMaxSize(100.0, 10.0)
            val endMonthLabel : Label = Label("End Date Month:")
            val endMonth : TextField = TextField("2")
            endMonth.setMaxSize(100.0, 10.0)
            val endDayLabel : Label = Label("End Date Day:")
            val endDay : TextField = TextField("3")
            endDay.setMaxSize(100.0, 10.0)

            val acceptDates = Button("OK")

            acceptDates.setOnAction {
                start = Date(startYear.text.toInt() - 1900, startMonth.text.toInt(), startDay.text.toInt() )
                end = Date(endYear.text.toInt() - 1900, endMonth.text.toInt(), endDay.text.toInt() )
                parameters.clear()
               // val pricesInInterval = makeAPICall("$baseURL/$coinId/history?", setParameters(start, end, "d1", parameters))
                lineChart.data.clear()
                for (pair in selectedCoins) {
                    val key = pair.key
                    val value = pair.value
                    pair.value.clear()
                    coinId = key
                    parameters.clear()
                    val pricesInInterval = makeAPICall("$baseURL/$coinId/history?", setParameters(start, end, "d1", parameters))
                    pair.setValue(makeListOfPoints(pricesInInterval))

                    val series = XYChart.Series<String, Number>()
                    series.name = coinId
                    for (p in pair.value) {
                        val formattedDate : String = fromUnixToDate(p)
                        series.data.add(XYChart.Data(formattedDate, p.priceUsd.toDouble()))

                    }
                    lineChart.data.add(series)
                }
                bp.top = lineChart
            }

            val startDateBox = HBox (startYearLabel, startYear, startMonthLabel, startMonth, startDayLabel, startDay)
            val endDateBox = HBox(endYearLabel, endYear, endMonthLabel, endMonth, endDayLabel, endDay)
            val deleteBtn = Button("delete")
            val dateBox = VBox(startDateBox, endDateBox, HBox(acceptDates, deleteBtn))



            deleteBtn.setOnAction {
                coinId = coins[table.selectionModel.focusedIndex].id
                selectedCoins.remove(coinId)
               lineChart.data.clear()
                for (pair in selectedCoins) {
                    val key = pair.key
                    val value = pair.value
                    pair.value.clear()
                    coinId = key
                    parameters.clear()
                    val pricesInInterval = makeAPICall("$baseURL/$coinId/history?", setParameters(start, end, "d1", parameters))
                    pair.setValue(makeListOfPoints(pricesInInterval))

                    val series = XYChart.Series<String, Number>()
                    series.name = coinId
                    for (p in pair.value) {
                        val formattedDate : String = fromUnixToDate(p)
                        series.data.add(XYChart.Data(formattedDate, p.priceUsd.toDouble()))
                    }
                    lineChart.data.add(series)
                }
                bp.top = lineChart

            }

            bp.center = dateBox

            val scene : Scene = Scene(bp, 1000.0, 700.0)

            primaryStage.scene = scene
            primaryStage.title = "Title";

            primaryStage.show()

        } catch (e: IOException) {
            println("Error: cannont access content - $e")
        } catch (e: URISyntaxException) {
            println("Error: Invalid URL $e")
        }


    }
     companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(CoinGraph::class.java)
        }
    }
}