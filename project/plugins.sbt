logLevel := Level.Warn

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// https://github.com/coursier/coursier#sbt-plugin for a big speedup
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.1")

addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.2.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.6.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.4")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
