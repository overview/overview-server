package controllers.backend

import java.util.{Date,UUID}
import java.sql.{SQLException,Timestamp}

import models.User
import models.tables.Users
import org.overviewproject.database.exceptions
import org.overviewproject.models.UserRole

class DbUserBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbUserBackend with org.overviewproject.database.DatabaseProvider

    def insertUser(id: Long, email: String, passwordHash: String = "", role: UserRole.Value = UserRole.NormalUser): User = {
      import org.overviewproject.database.Slick.simple._
      val user = User(id=id, email=email, passwordHash=passwordHash, role=role)
      (Users returning Users).+=(user)(session)
    }

    def findUser(id: Long): Option[User] = {
      import org.overviewproject.database.Slick.simple._
      Users.filter(_.id === id).firstOption(session)
    }
  }

  "#showByEmail" should {
    trait ShowByEmailScope extends BaseScope {
      val user = insertUser(123L, "user-123@example.org")
    }

    "show a User" in new ShowByEmailScope {
      await(backend.showByEmail("user-123@example.org")) must beSome(user.copy(passwordHash="                                                            "))
    }

    "return None for a non-User" in new ShowByEmailScope {
      await(backend.showByEmail("nonexistent-user@example.org")) must beNone
    }
  }

  "#updateIsAdmin" should {
    trait UpdateIsAdminScope extends BaseScope {
      val user = insertUser(123L, "user-123@example.org", role=UserRole.NormalUser)
    }

    "promote a user" in new UpdateIsAdminScope {
      await(backend.updateIsAdmin(123L, true))
      findUser(123L).map(_.role) must beSome(UserRole.Administrator)
    }

    "demote a user" in new UpdateIsAdminScope {
      await(backend.updateIsAdmin(123L, true))
      await(backend.updateIsAdmin(123L, false))
      findUser(123L).map(_.role) must beSome(UserRole.NormalUser)
    }

    "not affect another user" in new UpdateIsAdminScope {
      await(backend.updateIsAdmin(124L, true))
      findUser(123L).map(_.role) must beSome(UserRole.NormalUser)
    }
  }

  "#updatePasswordHash" should {
    trait UpdatePasswordHashScope extends BaseScope {
      val user = insertUser(123L, "user-123@example.org", passwordHash="hash1")
    }

    "set the hash" in new UpdatePasswordHashScope {
      await(backend.updatePasswordHash(123L, "hash2"))
      findUser(123L).map(_.passwordHash.trim) must beSome("hash2")
    }

    "not set other users' hashes" in new UpdatePasswordHashScope {
      await(backend.updatePasswordHash(124L, "hash2"))
      findUser(123L).map(_.passwordHash.trim) must beSome("hash1")
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val attributes = User.CreateAttributes("user-123@example.org", "password-hash")
    }

    "return a User" in new CreateScope {
      backend.create(attributes) must beLike[User] { case user =>
        user.email must beEqualTo("user-123@example.org")
        user.passwordHash must beMatching("""^password-hash\s+""".r)
      }.await
    }

    "write the User to the database" in new CreateScope {
      val returnedUser = await(backend.create(attributes))
      val dbUser = findUser(returnedUser.id)
      dbUser must beSome(returnedUser)
    }

    "throw Conflict when the user already exists" in new CreateScope {
      await(backend.create(attributes)) // create it once
      await(backend.create(attributes)) must throwA[exceptions.Conflict]
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

      connection.setAutoCommit(false)
      val loManager = pgConnection.getLargeObjectAPI
      val oid = loManager.createLO
      val uploadedFile = factory.uploadedFile()
      val upload = factory.upload(userId=user.id, uploadedFileId=uploadedFile.id, contentsOid=oid)
      connection.commit()
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
