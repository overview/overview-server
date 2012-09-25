package overview.util

object ExceptionStatusMessage {

  private val OutOfMemory = "out_of_memory"
  private val UnknownError = "worker_error"

  def apply(t: Throwable): String = t  match {
    case e: java.lang.OutOfMemoryError => OutOfMemory
    case _ => UnknownError
  }
}
