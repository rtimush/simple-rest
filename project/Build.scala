import sbt._
import sbt.Keys._

object RestBuild extends Build {

  lazy val root = Project(
    id = "simple-rest",
    base = file("."),
    settings = Project.defaultSettings ++ Seq(
      scalaVersion := "2.9.2",
      scalacOptions := Seq("-deprecation", "-unchecked", "-explaintypes"),
      libraryDependencies := Seq(
        "org.scalaz" %% "scalaz-core" % "7.0.0-M3",
        "net.databinder" %% "unfiltered-netty-server" % "0.6.4",
        "org.squeryl" %% "squeryl" % "0.9.5-2",
        "com.h2database" % "h2" % "1.3.166",
        "com.fasterxml" % "jackson-module-scala" % "1.9.3")))

}
