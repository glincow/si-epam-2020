package studyTasks

import java.util.Properties

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{explode, from_json, udf, window}
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._

import scala.io.Source

object RSVPAnalyseType2 {
  def approximateBoundaries(lat : Double, long : Double) : Boolean =
    ((29.61 <= lat) & (lat <= 49.03)) & ((-124.09 <= long) & (long <= -67.33))

  def main(args: Array[String]) {
    val url = getClass.getResource("application.properties")
    val properties: Properties = new Properties()
    val source = Source.fromURL(url)
    properties.load(source.bufferedReader())

    val spark: SparkSession = SparkSession
      .builder.master("local")
      .appName(properties.getProperty("spark.appName"))
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val jsonSchema = new StructType()
      .add("venue", new StructType()
        .add("venue_name", StringType)
        .add("lon", DoubleType)
        .add("lat", DoubleType)
        .add("venue_id", LongType))
      .add("visibility", StringType)
      .add("response", StringType)
      .add("guests", LongType)
      .add("member", new StructType()
        .add("member_id", LongType)
        .add("photo", StringType)
        .add("member_name", StringType))
      .add("rsvp_id", LongType)
      .add("mtime", LongType)
      .add("event", new StructType()
        .add("event_name", StringType)
        .add("event_id", StringType)
        .add("time", LongType)
        .add("event_url", StringType))
      .add("group", new StructType()
        .add("group_city", StringType)
        .add("group_country", StringType)
        .add("group_id", LongType)
        .add("group_lat", DoubleType)
        .add("group_long", DoubleType)
        .add("group_name", StringType)
        .add("group_state", StringType)
        .add("group_topics", DataTypes.createArrayType(new StructType()
          .add("topicName", StringType)
          .add("urlkey", StringType)))
        .add("group_urlname", StringType))

    val keyWordsArray = Array("devops", "software", "computing", "opensource",
      "software", "develop", "programming", "java", "web", "linux",
      "microservices", "agile", "gcc")

    def programmingTopicsDetecting(urlkeys: Seq[String]): Boolean = {
      for {
        url <- urlkeys
        key <- keyWordsArray
        if url.contains(key)
      } return true
      false
    }

    def keywordDetecting(urlkey: String): Boolean = {
      for {
        key <- keyWordsArray
        if urlkey.contains(key)
      } return true
      false
    }

    val streamingInputDF = spark.readStream.format("kafka")
      .option("kafka.bootstrap.servers", properties.getProperty("kafka.bootstrapAddress"))
      .option("subscribe", properties.getProperty("kafka.topicName"))
      .option("includeTimestamp", value = true)
      .load()

    val personStringDF = streamingInputDF.selectExpr("timestamp", "CAST(value AS STRING)")

    val approximateBoundariesUDF = udf(approximateBoundaries _)
    val programmingTopicsDetectingUDF = udf(programmingTopicsDetecting _)
    val keywordDetectingUDF = udf(keywordDetecting _)

    val streamingSelectDF = personStringDF
      .select($"timestamp", from_json($"value", jsonSchema).as("data"))
      .where(approximateBoundariesUDF($"data.venue.lat", $"data.venue.lon"))
      .where(programmingTopicsDetectingUDF($"data.group.group_topics.urlkey"))
      .select($"timestamp", explode($"data.group.group_topics.urlkey").as("keyword"))
      .where(keywordDetectingUDF($"keyword"))
      .withWatermark("timestamp", "2 minutes")
      .groupBy(
        window($"timestamp", "10 minutes", "5 minutes"),
        $"keyword"
      )
      .count()
      .select("window.start", "window.end", "keyword", "count")

    streamingSelectDF.printSchema()
    streamingSelectDF.writeStream
      .trigger(Trigger.ProcessingTime("5 minutes"))
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()
  }
}
