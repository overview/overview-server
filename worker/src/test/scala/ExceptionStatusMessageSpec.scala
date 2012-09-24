package overview.util

import org.specs2.mutable.Specification

class ExceptionStatusMessageSpec extends Specification {

  "ExceptionStatusMessage" should {

    "return out_of_memory for OutOfMemoryError" in {
      val outOfMemory = new java.lang.OutOfMemoryError()

      ExceptionStatusMessage(outOfMemory) must be equalTo ("out_of_memory")

    }

    "return worker_error for all other errors" in {
      val error = new Exception("random error")

      ExceptionStatusMessage(error) must be equalTo("worker_error")
    }
  }
}
