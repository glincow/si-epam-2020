package sample

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{from_json, udf, window}
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._

object ByGroupStateJob {
  def restrictionUSALatLong(lat : Double, long : Double) : Boolean =
    ((19.50139 <= lat) & (lat <= 64.85694)) & ((-161.75583 <= long) & (long <= -68.01197))

  def main(args: Array[String]) {
    val spark = SparkSession
      .builder()
      .appName("ByNumberGuests")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val jsonSchema = new StructType()
      .add("venue",
        new StructType()
          .add("venue_name", StringType)
          .add("lon", DoubleType)
          .add("lat", DoubleType)
          .add("venue_id", LongType))
      .add("visibility", StringType)
      .add("response", StringType)
      .add("guests", LongType)
      .add("member",
        new StructType()
          .add("member_id", LongType)
          .add("photo", StringType)
          .add("member_name", StringType))
      .add("rsvp_id", LongType)
      .add("mtime", LongType)
      .add("event",
        new StructType()
          .add("event_name", StringType)
          .add("event_id", StringType)
          .add("time", LongType)
          .add("event_url", StringType))
      .add("group",
        new StructType()
          .add("group_city", StringType)
          .add("group_country", StringType)
          .add("group_id", LongType)
          .add("group_lat", DoubleType)
          .add("group_long", DoubleType)
          .add("group_name", StringType)
          .add("group_state", StringType)
          .add("group_topics", DataTypes.createArrayType(
            new StructType()
              .add("topicName", StringType)
              .add("urlkey", StringType)))
          .add("group_urlname", StringType))

    val streamingInputDF = spark.readStream.format("kafka")
      .option("kafka.bootstrap.servers", "kafkaserver:9092")
      .option("subscribe", "data_test")
      .load()

    val personStringDF = streamingInputDF.selectExpr("timestamp", "CAST(value AS STRING)")

    val restrictionUSALatLongUDF = udf(restrictionUSALatLong _)

    val streamingSelectDF = personStringDF
      .select($"timestamp", from_json($"value", jsonSchema).as("data")).select("timestamp","data.*")
      .where(restrictionUSALatLongUDF($"venue.lat", $"venue.lon"))
      .withWatermark("timestamp", "2 minutes")
      .groupBy(
        window($"timestamp", "10 minutes", "5 minutes"),
        $"group.group_state"
      )
      .count()
      .select("window.start", "window.end", "group_state", "count")


    streamingSelectDF.printSchema()

    streamingSelectDF.writeStream
      .trigger(Trigger.ProcessingTime("5 minutes"))
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()
  }
}
