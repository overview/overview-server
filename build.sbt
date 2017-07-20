val rootDirectory = sys.props("user.dir")

organization := "com.overviewdocs"
version := "1.0.0-SNAPSHOT"
scalaVersion := "2.12.2"

resolvers ++= Seq(
  Resolver.url("Typesafe repository", "http://repo.typesafe.com/typesafe/releases/"),
  Resolver.url("jbig2 repository", "http://jbig2-imageio.googlecode.com/svn/maven-repository"),
  Resolver.url("Oracle Released Java Packages", "http://download.oracle.com/maven"),
  Resolver.url("scalaz-bintray", "https://dl.bintray.com/scalaz/releases"),
)

val baseJavaOpts = Seq(
  "-Duser.timezone=UTC",
)

val baseSettings = Seq(

)
