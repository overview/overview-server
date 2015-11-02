package controllers.backend

import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.FileGroups

class DbFileGroupBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbFileGroupBackend {}

    def findFileGroups(userEmail: String, apiToken: Option[String], completed: Boolean): Seq[FileGroup] = {
      import database.api._
      blockingDatabase.seq(
        FileGroups
          .filter(_.userEmail === userEmail)
          .filter(g => (g.apiToken.isEmpty && apiToken.isEmpty) || (g.apiToken.isDefined && g.apiToken === apiToken))
          .filter(g => (!g.addToDocumentSetId.isEmpty) === completed)
      )
    }
  }

  "#findOrCreate" should {
    trait CreateScope extends BaseScope {
      val attributes = FileGroup.CreateAttributes("user@example.org", None)
      def findOrCreate: FileGroup = await(backend.findOrCreate(attributes))
    }

    "create a new file group" in new CreateScope {
      val fileGroup = findOrCreate
      fileGroup.userEmail must beEqualTo("user@example.org")
      fileGroup.apiToken must beNone
      fileGroup.addToDocumentSetId must beNone
      val dbFileGroup = findFileGroups("user@example.org", None, false).headOption
      dbFileGroup must beSome
    }

    "find an existing file group" in new CreateScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val fileGroup = findOrCreate
      val dbFileGroups = findFileGroups("user@example.org", None, false)
      dbFileGroups.length must beEqualTo(1)
      dbFileGroups.head must beEqualTo(fileGroup)
    }

    "skip a deleted file group" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=true)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip an existing file group when it is completed" in new CreateScope {
      val documentSet = factory.documentSet()
      val existing = factory.fileGroup(
        userEmail="user@example.org",
        apiToken=None,
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("fr"),
        splitDocuments=Some(true),
        nFiles=Some(1),
        nBytes=Some(2L),
        nFilesProcessed=Some(0),
        nBytesProcessed=Some(0L)
      )
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup from a different user" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user1@example.org", apiToken=None)
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with apiToken=null when called with non-null" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("foo"))
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with apiToken<>null when called with null" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "skip a FileGroup with a different apiToken" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("bar"))
      findOrCreate.id must not(beEqualTo(existing.id))
    }

    "find a FileGroup with the same apiToken" in new CreateScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      override val attributes = FileGroup.CreateAttributes("user@example.org", Some("foo"))
      findOrCreate.id must beEqualTo(existing.id)
    }
  }

  "#find" should {
    trait FindScope extends BaseScope {
      def find(userEmail: String, apiToken: Option[String]): Option[FileGroup] = await(backend.find(userEmail, apiToken))
    }

    "find an existing file group" in new FindScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      find("user@example.org", None) must beSome(existing)
    }

    "skip a deleted file group" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=true)
      find("user@example.org", None) must beNone
    }

    "skip an existing file group when it is completed" in new FindScope {
      val documentSet = factory.documentSet()
      val existing = factory.fileGroup(
        userEmail="user@example.org",
        apiToken=None,
        addToDocumentSetId=Some(documentSet.id),
        lang=Some("fr"),
        splitDocuments=Some(true),
        nFiles=Some(1),
        nBytes=Some(2L),
        nFilesProcessed=Some(0),
        nBytesProcessed=Some(0L)
      )
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup from a different user" in new FindScope {
      val existing = factory.fileGroup(userEmail="user1@example.org", apiToken=None)
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup with apiToken=null when called with non-null" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=None)
      find("user@example.org", Some("foo")) must beNone
    }

    "skip a FileGroup with apiToken<>null when called with null" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      find("user@example.org", None) must beNone
    }

    "skip a FileGroup with a different apiToken" in new FindScope {
      factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      find("user@example.org", Some("bar")) must beNone
    }

    "find a FileGroup with the same apiToken" in new FindScope {
      val existing = factory.fileGroup(userEmail="user@example.org", apiToken=Some("foo"))
      find("user@example.org", Some("foo")) must beSome(existing)
    }
  }

  "#addToDocumentSet" should {
    trait AddToDocumentSetScope extends BaseScope {
      def addToDocumentSet(id: Long, documentSetId: Long, lang: String, splitDocuments: Boolean): Option[FileGroup] = {
        await(backend.addToDocumentSet(id, documentSetId, lang, splitDocuments))
      }
    }

    "update a FileGroup" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val documentSet = factory.documentSet()
      addToDocumentSet(fileGroup.id, documentSet.id, "fr", true)

      val savedList = findFileGroups("user@example.org", None, true)
      savedList.length must beEqualTo(1)
      val saved = savedList.head
      saved.id must beEqualTo(fileGroup.id)
      saved.addToDocumentSetId must beSome(documentSet.id)
      saved.lang must beSome("fr")
      saved.splitDocuments must beSome(true)
      saved.estimatedCompletionTime must beNone
    }

    "return the FileGroup it updates in the database" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val documentSet = factory.documentSet()
      val returned = addToDocumentSet(fileGroup.id, documentSet.id, "fr", true)
      returned must beEqualTo(findFileGroups("user@example.org", None, true).headOption)
    }

    "set nFiles and nBytes from GroupedFileUploads" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val documentSet = factory.documentSet()
      factory.groupedFileUpload(fileGroupId=fileGroup.id, size=1000L)
      factory.groupedFileUpload(fileGroupId=fileGroup.id, size=234L)
      addToDocumentSet(fileGroup.id, documentSet.id, "fr", true)

      val saved = findFileGroups("user@example.org", None, true).head
      saved.nFiles must beSome(2)
      saved.nBytes must beSome(1234L)
      saved.nFilesProcessed must beSome(0)
      saved.nBytesProcessed must beSome(0L)
    }

    "set nFiles=0 and nBytes=0" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val documentSet = factory.documentSet()
      addToDocumentSet(fileGroup.id, documentSet.id, "fr", true)

      val saved = findFileGroups("user@example.org", None, true).head
      saved.nFiles must beSome(0)
      saved.nBytes must beSome(0L)
      saved.nFilesProcessed must beSome(0)
      saved.nBytesProcessed must beSome(0L)
    }

    "skip a missing FileGroup" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None)
      val documentSet = factory.documentSet()
      addToDocumentSet(fileGroup.id + 1, documentSet.id, "fr", true)
      findFileGroups("user@example.org", None, false) must beEqualTo(Seq(fileGroup))
    }

    "skip a deleted FileGroup" in new AddToDocumentSetScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=true)
      val documentSet = factory.documentSet()
      addToDocumentSet(fileGroup.id, documentSet.id, "fr", true)
      findFileGroups("user@example.org", None, false) must beEqualTo(Seq(fileGroup))
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      def destroy(id: Long): Unit = await(backend.destroy(id))
    }

    "destroy a FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=false)
      destroy(fileGroup.id)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup.copy(deleted=true))
    }

    "skip a missing FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=false)
      destroy(fileGroup.id + 1)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }

    "skip a deleted FileGroup" in new DestroyScope {
      val fileGroup = factory.fileGroup(userEmail="user@example.org", apiToken=None, deleted=true)
      destroy(fileGroup.id)
      findFileGroups("user@example.org", None, false).headOption must beSome(fileGroup)
    }
  }
}
