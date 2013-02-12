package models

case class TwitterTweet(val url: String, val text: String) {
  val UsernameRegex = """//(www\.)?twitter\.com/#?([^/]+)""".r
  def username : Option[String] = for {
    UsernameRegex(_, u) <- UsernameRegex findFirstIn url
  } yield u
}
