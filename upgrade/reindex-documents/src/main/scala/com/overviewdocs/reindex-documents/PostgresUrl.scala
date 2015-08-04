package com.overviewdocs.upgrade.reindex_documents

// We can't just pass a String to Postgres, because our own config files use
// Play's special syntax, not the standard JDBC one.
case class PostgresUrl(
  host: String,
  port: Option[Int],
  dbname: String,
  args: Map[String,String]
) {
  def toJdbcUrl = {
    Seq(
      "jdbc:postgresql://",
      host,
      port.map(":" + _).getOrElse(""),
      "/",
      dbname,
      "?",
      args.toSeq.map { t: (String,String) => Seq(t._1, t._2).mkString("=") }.mkString("&")
    ).mkString
  }
}

object PostgresUrl {
  implicit val postgresUrlRead: scopt.Read[PostgresUrl] = scopt.Read.reads { s: String =>
    val uri = new java.net.URI(s) // throws exception if bad

    def err(s: String) = throw new IllegalArgumentException(s)
    def oneQueryParameter(keyEqualsValue: String): (String,String) = {
      val parts = keyEqualsValue.split("=", 2)
      if (parts.length != 2) err("Invalid query parameter '" + keyEqualsValue + "'")
      (parts(0), parts(1))
    }

    if (uri.getScheme != "postgres" && uri.getScheme != "postgresql") {
      err("Postgres URL must have scheme 'postgres'")
    }

    val port = if (uri.getPort == -1) None else Some(uri.getPort)
    val dbname = uri.getPath.substring(1)

    val authOnlyArgs: Map[String,String] = if (Option(uri.getUserInfo).isDefined) {
      val userInfoParts = uri.getUserInfo.split(":", 2)
      if (userInfoParts.length == 1) {
        Map("user" -> userInfoParts(0))
      } else {
        Map("user" -> userInfoParts(0), "password" -> userInfoParts(1))
      }
    } else Map()

    val queryArgs: Map[String,String] = Option(uri.getQuery)
      .map(_.split("&").map(oneQueryParameter _).toMap)
      .getOrElse(Map())

    val args: Map[String,String] = authOnlyArgs ++ queryArgs

    PostgresUrl(uri.getHost, port, dbname, args)
  }
}
