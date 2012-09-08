package overview.http


trait BasicAuth {

 this: DocumentAtURL =>

  val username: String
  val password: String
}
