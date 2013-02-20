package controllers.util

object IdList {
  
  def apply(ids: String) : Seq[Long] = {
    val numberStrings = """\d+""".r findAllIn(ids)
    
    val possibleLongs = numberStrings.map(toLongIfPossible).toSeq
    
    possibleLongs.collect {case Some(aLong) => aLong}
  }
  
  private def toLongIfPossible(maybeLong: String) : Option[Long] = {
    try {
      Some(maybeLong.toLong)
    }
    catch {
      case e : Throwable => None
    }
  }

}
