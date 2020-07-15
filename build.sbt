name := "test1"

version := "0.1"

scalaVersion := "2.11.12"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.5"
//libraryDependencies += "org.apache.spark" %% "spark-graphx" % "2.4.5"
libraryDependencies += "org.apache.spark" %% "spark-streaming" % "2.4.5"
//libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.4.5"
//libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.5.0"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.5"
libraryDependencies += "org.apache.spark" %% "spark-sql-kafka-0-10" % "2.4.5"