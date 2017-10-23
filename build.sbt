name := "HippoManager"

version := "0.2"

scalaVersion := "2.11.8"

resolvers += Resolver.bintrayRepo("cakesolutions", "maven")

enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.3",
  "com.typesafe.akka" %% "akka-remote" % "2.5.3",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.3",
  "org.iq80.leveldb" % "leveldb" % "0.7",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-http" % "10.0.9",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.9",
  "com.typesafe.akka" %% "akka-cluster" % "2.5.3",
  "com.typesafe.akka" %% "akka-distributed-data" % "2.5.3",
  "net.cakesolutions" %% "scala-kafka-client" % "0.9.0.0",
  "net.cakesolutions" %% "scala-kafka-client-akka" % "0.9.0.0",
  "io.spray" %%  "spray-json" % "1.3.3"
)