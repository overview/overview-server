object server {
  println("Welcome to the Scala wccorksheet")     //> Welcome to the Scala wccorksheet

  val mr = """^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]*)\\")$""".r
                                                  //> mr  : scala.util.matching.Regex = ^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]
                                                  //| *)\\")$

  val s = """foo=hh\\"h"""                        //> s  : java.lang.String = foo=hh\\"h
  mr.findFirstMatchIn(s).map { m =>
    m.subgroups.filter(_ != null).head
  }                                               //> res0: Option[String] = None

  val d = new scala.util.matching.Regex("""^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]*)\\")$""", "a")
                                                  //> d  : scala.util.matching.Regex = ^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]*
                                                  //| )\\")$

  val z = """^[^=]*=("|\\")?(.*)\1$""".r          //> z  : scala.util.matching.Regex = ^[^=]*=("|\\")?(.*)\1$
  z.findFirstMatchIn(s).map { m =>
    m.subgroups
  }                                               //> res1: Option[List[String]] = None
  val zz = """^[^=]*=("|.*?)(([^"]|\\")*)\1$""".r //> zz  : scala.util.matching.Regex = ^[^=]*=("|.*?)(([^"]|\\")*)\1$
  zz.findFirstMatchIn(s).map { m =>
    m.group(2)
  }                                               //> res2: Option[String] = Some(hh\\"h)
}