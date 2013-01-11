package org.overviewproject.http


trait BasicAuth {

 this: DocumentAtURL =>

  val username: String
  val password: String
}
