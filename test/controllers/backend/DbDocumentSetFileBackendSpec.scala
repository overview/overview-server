package controllers.backend

class DbDocumentSetFileBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbDocumentSetFileBackend with org.overviewproject.database.DatabaseProvider
    def hex2sha(hex: String): Array[Byte] = {
      hex
        .sliding(2, 2)
        .map(Integer.parseInt(_, 16).toByte)
        .toArray
    }
  }

  "DbDocumentSetFileBackend" should {
    "#existsByIdAndSha1" should {
      trait ExistsByIdAndSha1Scope extends BaseScope {
        val sha1 = hex2sha("e8d9464f01bf27aae2b938653747dcbadc1bde6d")
        val documentSet = factory.documentSet()
      }

      "return true when a file belongs to a document set" in new ExistsByIdAndSha1Scope {
        val file = factory.file(contentsSha1=Some(sha1))
        val doc = factory.document(documentSetId=documentSet.id, fileId=Some(file.id))
        backend.existsByIdAndSha1(documentSet.id, sha1) must beEqualTo(true).await
      }

      "return false when the file belongs to a different document set" in new ExistsByIdAndSha1Scope {
        val documentSet2 = factory.documentSet()
        val file = factory.file(contentsSha1=Some(sha1))
        val doc = factory.document(documentSetId=documentSet2.id, fileId=Some(file.id))
        backend.existsByIdAndSha1(documentSet.id, sha1) must beEqualTo(false).await
      }

      "return false when the File does not exist" in new ExistsByIdAndSha1Scope {
        backend.existsByIdAndSha1(documentSet.id, sha1) must beEqualTo(false).await
      }

      "return false when the DocumentSet does not exist" in new ExistsByIdAndSha1Scope {
        val file = factory.file(contentsSha1=Some(sha1)) // the file *does* exist
        backend.existsByIdAndSha1(documentSet.id + 1L, sha1) must beEqualTo(false).await
      }
    }
  }
}
