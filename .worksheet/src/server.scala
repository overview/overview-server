object server {;import org.scalaide.worksheet.runtime.library.WorksheetSupport._; def main(args: Array[String])=$execute{;$skip(61); 
  println("Welcome to the Scala wccorksheet");$skip(70); 

  val mr = """^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]*)\\")$""".r;System.out.println("""mr  : scala.util.matching.Regex = """ + $show(mr ));$skip(28); 

  val s = """foo=hh\\"h""";System.out.println("""s  : java.lang.String = """ + $show(s ));$skip(79); val res$0 = 
  mr.findFirstMatchIn(s).map { m =>
    m.subgroups.filter(_ != null).head
  };System.out.println("""res0: Option[String] = """ + $show(res$0));$skip(103); 

  val d = new scala.util.matching.Regex("""^[^=]*=(?:([^"\\]*)|"([^"\\]*)"|\\"([^"\\]*)\\")$""", "a");System.out.println("""d  : scala.util.matching.Regex = """ + $show(d ));$skip(42); 

  val z = """^[^=]*=("|\\")?(.*)\1$""".r;System.out.println("""z  : scala.util.matching.Regex = """ + $show(z ));$skip(55); val res$1 = 
  z.findFirstMatchIn(s).map { m =>
    m.subgroups
  };System.out.println("""res1: Option[List[String]] = """ + $show(res$1));$skip(50); 
  val zz = """^[^=]*=("|.*?)(([^"]|\\")*)\1$""".r;System.out.println("""zz  : scala.util.matching.Regex = """ + $show(zz ));$skip(55); val res$2 = 
  zz.findFirstMatchIn(s).map { m =>
    m.group(2)
  };System.out.println("""res2: Option[String] = """ + $show(res$2))}
}