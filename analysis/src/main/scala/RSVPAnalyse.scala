import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.from_json
import org.apache.spark.sql.types.{DataTypes, DoubleType, LongType, StringType, StructType}

object RSVPAnalyse {
  def main(args: Array[String]): Unit = {
    val appName = "RSVP Analyse"
    val bootstrapAddress = "kafkaserver:9092"
    val topicName = "data_test"

    val spark: SparkSession = SparkSession
      .builder.master("local")
      .appName(appName)
      .getOrCreate()

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
      .option("kafka.bootstrap.servers", bootstrapAddress)
      .option("subscribe", topicName)
      .load()

    val personStringDF = streamingInputDF.selectExpr("CAST(value AS STRING)")
    personStringDF.printSchema()

    val streamingSelectDF = personStringDF
      .select(from_json($"value", jsonSchema)
        .as("data")).select("data.*")

    streamingSelectDF.printSchema()

    streamingSelectDF.writeStream
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()
  }

}
