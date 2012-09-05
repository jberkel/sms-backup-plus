resolvers ++= Seq(
  Resolver.url("scalasbt snapshots", new
        URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("org.scala-sbt" % "sbt-android-plugin" % "0.6.3-SNAPSHOT")
