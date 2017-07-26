logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.1")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.2.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.1.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.9")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.1")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
