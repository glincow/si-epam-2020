package studyTasks

import org.apache.spark.sql.SparkSession

object AdditionalTasks {
  def main(args: Array[String]) {
    val spark: SparkSession = SparkSession
      .builder.master("local")
      .appName("Scala Spark Example")
      .getOrCreate()

    val flightData2015 = spark
      .read
      .option("inferSchema", true)
      .option("header", true)
      .csv("src/main/resources/2015-summary.csv")

    flightData2015.createOrReplaceTempView("flight_data_2015")

    val sqlWay = spark.sql("""
           SELECT DEST_COUNTRY_NAME, count(1)
           FROM flight_data_2015
           GROUP BY DEST_COUNTRY_NAME
           """)

    val dataFrameWay = flightData2015
      .groupBy("DEST_COUNTRY_NAME")
      .count()
    sqlWay.explain
    dataFrameWay.explain
  }
}
