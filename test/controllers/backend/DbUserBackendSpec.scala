package controllers.backend

import java.util.{Date,UUID}
import java.sql.{SQLException,Timestamp}

import models.User
import models.tables.Users

class DbUserBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbUserBackend

    def insertUser(id: Long, email: String): User = {
      import org.overviewproject.database.Slick.simple._
      val ret = User(id=id, email=email)
      Users.insertInvoker.insert(ret)(session)
      ret
    }

    def findUser(id: Long): Option[User] = {
      import org.overviewproject.database.Slick.simple._
      Users.filter(_.id === id).firstOption(session)
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val user = insertUser(123L, "user-123@example.org")
    }

    "destroy a normal user" in new DestroyScope {
      await(backend.destroy(user.id))
      findUser(user.id) must beNone
    }

    "destroy a user's uploads" in new DestroyScope {
      import org.overviewproject.database.Slick.simple._
      import org.overviewproject.models.tables.{UploadedFiles,Uploads}

      val loManager = pgConnection.getLargeObjectAPI
      val oid = loManager.createLO
      val uploadedFile = factory.uploadedFile()
      val upload = factory.upload(userId=user.id, uploadedFileId=uploadedFile.id, contentsOid=oid)
      await(backend.destroy(user.id))
      Uploads.filter(_.id === upload.id).length.run(session) must beEqualTo(0)
      UploadedFiles.filter(_.id === uploadedFile.id).length.run(session) must beEqualTo(1)
      loManager.open(oid) must throwA[SQLException]
    }

    "destroy a user's sessions" in new DestroyScope {
      import org.overviewproject.database.Slick.simple._
      import models.tables.Sessions
      import models.Session

      Sessions.+=(Session(user.id, "127.0.0.1"))(session)
      await(backend.destroy(user.id))
      Sessions.filter(_.userId === user.id).length.run(session) must beEqualTo(0)
    }

    "not destroy a nonexistent user" in new DestroyScope {
      await(backend.destroy(122L))
      findUser(user.id) must beSome
    }
  }

  "#updateLastActivity" should {
    trait UpdateLastActivityScope extends BaseScope {
      val user = insertUser(123L, "user@example.org")
    }

    "change lastActivityIp" in new UpdateLastActivityScope {
      await(backend.updateLastActivity(user.id, "192.168.0.1", new Timestamp(1425318194284L)))
      findUser(user.id).flatMap(_.lastActivityIp) must beSome("192.168.0.1")
    }

    "change lastActivityAt" in new UpdateLastActivityScope {
      await(backend.updateLastActivity(user.id, "192.168.0.1", new Timestamp(1425318194284L)))
      findUser(user.id).flatMap(_.lastActivityAt) must beSome(new Timestamp(1425318194284L))
    }
  }
}
