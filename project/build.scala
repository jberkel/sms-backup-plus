import sbt._
import Keys._
import AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq(
    version := "1.4.5-SNAPSHOT",
    versionCode in Android := 1406,
    organization := "com.zegoggles"
  )

  val androidSettings =
    settings ++
    Seq(
      platformName := "android-14"
    )

  val androidProjectSettings =
    androidSettings ++
    AndroidProject.androidSettings ++
    AndroidMarketPublish.settings ++
//    AndroidManifestGenerator.settings ++
    PlainJavaProject.settings
}

object AndroidBuild extends Build {
  val coreDependencies = Seq(
  )

  val providedDependencies = Seq(
    "com.google.android" % "android" % "2.3.3" % "provided",
    "org.apache.httpcomponents" % "httpcore" % "4.0.1" % "provided",
    "org.apache.httpcomponents" % "httpclient" % "4.0.3" % "provided",
    "org.json" % "json" % "20090211" % "provided",
    "commons-logging" % "commons-logging" % "1.1.1" % "provided",
    "commons-codec" % "commons-codec" % "1.5" % "provided"
  )

  val testDependencies = Seq()

  val repos = Seq(
    MavenRepository("acra release repository", "http://acra.googlecode.com/svn/repository/releases")
  )

  lazy val soundcloud_android = Project (
    "sms-backup-plus",
    file("."),
    settings = General.androidProjectSettings ++ Seq (
      keyalias in Android := "jberkel",
      libraryDependencies ++= coreDependencies ++ providedDependencies ++ testDependencies,
      resolvers ++= repos
    ) ++ AndroidInstall.settings
  )
}
