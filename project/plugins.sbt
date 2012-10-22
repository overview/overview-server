logLevel := Level.Warn

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Templemore repository" at "http://templemore.co.uk/repo/"

resolvers += Resolver.url("community", url("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)

addSbtPlugin("templemore" % "xsbt-cucumber-plugin" % "0.5.0")

addSbtPlugin("play" % "sbt-plugin" % "2.0.3")

addSbtPlugin("me.lessis" % "coffeescripted-sbt" % "0.2.3")
