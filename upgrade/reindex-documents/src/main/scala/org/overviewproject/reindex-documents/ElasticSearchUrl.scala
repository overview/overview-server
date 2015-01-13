package org.overviewproject.upgrade.reindex_documents

case class ElasticSearchUrl(host: String, port: Int) {
  override def toString = Seq(host, port.toString).mkString(":")
}

object ElasticSearchUrl {
  implicit val elasticSearchUrlRead: scopt.Read[ElasticSearchUrl] = scopt.Read.reads { s: String =>
    def err(s: String) = throw new IllegalArgumentException(s)
    val Pattern = """^([a-zA-Z][\.\w]+\w):(\d+)$""".r
    s match {
      case Pattern(host, portString) => {
        val i = portString.toInt // may throw
        if (i < 1 || i > 65535) err("ElasticSearch port number is out of range: " + portString)
        ElasticSearchUrl(host, i)
      }
      case _ => err("ElasticSearch URL must look like hostname:port")
    }
  }
}
