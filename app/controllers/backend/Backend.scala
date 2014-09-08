package controllers.backend

trait Backend {
}

object Backend {
  class BackendException(t: Throwable) extends Exception(t)

  /** You tried to create an object that already exists. */
  class ConflictException(t: Throwable) extends BackendException(t)
}
