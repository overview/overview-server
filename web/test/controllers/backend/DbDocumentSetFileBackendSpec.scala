package controllers.backend

class DbDocumentSetFileBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbBackendScope {
    val backend = new DbDocumentSetFileBackend(database)
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
        val file = factory.file(contentsSha1=sha1)
        val doc = factory.document(documentSetId=documentSet.id, fileId=Some(file.id))
        await(backend.existsByIdAndSha1(documentSet.id, sha1)) must beEqualTo(true)
      }

      "return false when the file belongs to a different document set" in new ExistsByIdAndSha1Scope {
        val documentSet2 = factory.documentSet()
        val file = factory.file(contentsSha1=sha1)
        val doc = factory.document(documentSetId=documentSet2.id, fileId=Some(file.id))
        await(backend.existsByIdAndSha1(documentSet.id, sha1)) must beEqualTo(false)
      }

      "return true when a file2 belongs to a document set" in new ExistsByIdAndSha1Scope {
        val file2 = factory.file2(blobSha1=sha1)
        factory.documentSetFile2(documentSetId=documentSet.id, file2Id=file2.id)
        await(backend.existsByIdAndSha1(documentSet.id, sha1)) must beEqualTo(true)
      }

      "return false when the file2 belongs to a different document set" in new ExistsByIdAndSha1Scope {
        val documentSet2 = factory.documentSet()
        val file2 = factory.file2(blobSha1=sha1)
        factory.documentSetFile2(documentSetId=documentSet2.id, file2Id=file2.id)
        await(backend.existsByIdAndSha1(documentSet.id, sha1)) must beEqualTo(false)
      }

      "return false when the File does not exist" in new ExistsByIdAndSha1Scope {
        await(backend.existsByIdAndSha1(documentSet.id, sha1)) must beEqualTo(false)
      }

      "return false when the DocumentSet does not exist" in new ExistsByIdAndSha1Scope {
        val file = factory.file(contentsSha1=sha1) // the file *does* exist
        await(backend.existsByIdAndSha1(documentSet.id + 1L, sha1)) must beEqualTo(false)
      }
    }

    "#existsForRoot" should {
      trait ExistsForRootScope extends BaseScope {
        val documentSet = factory.documentSet()
      }

      "return true when a file2 belongs to a document set" in new ExistsForRootScope {
        val file2 = factory.file2()
        factory.documentSetFile2(documentSetId=documentSet.id, file2Id=file2.id)
        await(backend.existsForRoot(documentSet.id, file2.id)) must beEqualTo(true)
      }

      "return false when the file2 belongs to a different document set" in new ExistsForRootScope {
        val documentSet2 = factory.documentSet()
        val file2 = factory.file2()
        factory.documentSetFile2(documentSetId=documentSet2.id, file2Id=file2.id)
        await(backend.existsForRoot(documentSet.id, file2.id)) must beEqualTo(false)
      }

      "return true with a non-root file2" in new ExistsForRootScope {
        val root = factory.file2()
        val child = factory.file2(rootFile2Id=Some(root.id))
        factory.documentSetFile2(documentSetId=documentSet.id, file2Id=root.id)
        await(backend.existsForRoot(documentSet.id, child.id)) must beEqualTo(true)
      }

      "return false when a non-root file2 root belongs to a different document set" in new ExistsForRootScope {
        val documentSet2 = factory.documentSet()
        val root = factory.file2()
        val child = factory.file2(rootFile2Id=Some(root.id))
        factory.documentSetFile2(documentSetId=documentSet2.id, file2Id=root.id)
        await(backend.existsForRoot(documentSet.id, child.id)) must beEqualTo(false)
      }

      "return false when the File2 does not exist" in new ExistsForRootScope {
        await(backend.existsForRoot(documentSet.id, 123L)) must beEqualTo(false)
      }

      "return false when the DocumentSet does not exist" in new ExistsForRootScope {
        val file2 = factory.file2() // the file *does* exist
        await(backend.existsForRoot(documentSet.id + 1L, file2.id)) must beEqualTo(false)
      }
    }
  }
}
