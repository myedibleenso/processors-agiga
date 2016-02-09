name := "processors-agiga"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.1",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.clulab" %% "processors" % "5.8.0",
  "org.clulab" %% "processors" % "5.8.0" classifier "models",
  "edu.jhu.agiga" % "agiga" % "1.4",
  "org.apache.commons" % "commons-compress" % "1.10"
)