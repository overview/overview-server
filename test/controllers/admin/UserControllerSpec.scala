package controllers.admin

import com.github.t3hnar.bcrypt._
import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.backend.UserBackend
import models.User
import models.pagination.{Page,PageInfo,PageRequest}
import org.overviewproject.database.exceptions.Conflict
import org.overviewproject.models.UserRole
import org.overviewproject.tree.orm.finders.ResultPage

class UserControllerSpec extends controllers.ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockBackend = smartMock[UserBackend]

    mockBackend.updateIsAdmin(any, any) returns Future.successful(())
    mockBackend.updatePasswordHash(any, any) returns Future.successful(())
    mockBackend.destroy(any) returns Future.successful(())

    val controller = new UserController {
      override val backend = mockBackend
    }

    def result : Future[Result]
  }

  "admin.UserController" should {
    "index" should {
      trait IndexScope extends BaseScope {
        def request = fakeAuthorizedRequest
        override def result = controller.index()(request)
      }

      "return Ok" in new IndexScope {
        h.status(result) must beEqualTo(h.OK)
      }

      """render <div class="app" data-admin-email="email"></div>""" in new IndexScope {
        h.contentAsString(result) must contain(s"""<div class="app" data-admin-email="${request.user.email}"></div>""")
      }
    }

    "indexJson" should {
      // XXX This is mostly a test of the view...
      trait IndexJsonScope extends BaseScope {
        val page = 1
        def users : Seq[User]
        mockBackend.indexPage(any[PageRequest]) answers((_) => Future(Page(users, PageInfo(PageRequest(0, 50), 100))))
        override def result = controller.indexJson(page)(fakeAuthorizedRequest)
      }

      "render JSON users" in new IndexJsonScope {
        override def users = Seq(
          User(id=2, email="user@example.org"),
          User(id=3, email="user2@example.org")
        )

        h.contentAsString(result) must /("users") /#(1) /("email" -> "user2@example.org")
      }

      "render is_admin" in new IndexJsonScope {
        override def users = Seq(
          User(role=UserRole.NormalUser),
          User(role=UserRole.Administrator)
        )

        val s = h.contentAsString(result)

        s must /("users") /#(0) /("is_admin" -> false)
        s must /("users") /#(1) /("is_admin" -> true)
      }

      "render timestamps as UTC ISO8601" in new IndexJsonScope {
        val date = new java.sql.Timestamp(1392730766123L)
        val dateString = "2014-02-18T13:39:26.123Z"
        override def users = Seq(User(
          confirmationSentAt=Some(date),
          confirmedAt=Some(date),
          lastActivityAt=Some(date)
        ))

        val s = h.contentAsString(result)

        s must */("confirmation_sent_at" -> dateString)
        s must */("confirmed_at" -> dateString)
        s must */("last_activity_at" -> dateString)
      }

      "render timestamps as null" in new IndexJsonScope {
        override def users = Seq(User(
          confirmationSentAt=None,
          confirmedAt=None,
          lastActivityAt=None
        ))

        val s = h.contentAsString(result)

        s must contain(""""confirmation_sent_at":null""")
        s must contain(""""confirmed_at":null""")
        s must contain(""""last_activity_at":null""")
      }

      "render pagination info" in new IndexJsonScope {
        override def users = Range(0, 100).map(i => User(id=i, email=s"user${i}@example.org"))

        val s = h.contentAsString(result)

        // specs2 breakage...
        s must contain(""""page":1""")
        s must contain(s""""pageSize":${controller.PageSize}""")
        s must contain(""""total":100""")
      }
    }

    "update" should {
      trait UpdateScope extends BaseScope {
        val email = "user2@example.org"
        def data: Seq[(String,String)] = Seq()
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(data: _*)
        override def result = controller.update(email)(request)
      }

      "return NotFound when user does not exist" in new UpdateScope {
        mockBackend.showByEmail(email) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "not change role when is_admin is neither 'true' nor 'false'" in new UpdateScope {
        mockBackend.showByEmail(email) returns Future.successful(Some(User(email=email)))
        override def data = Seq("is_admin" -> "not true or false")
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was no(mockBackend).updateIsAdmin(any, any)
      }

      "promote a user" in new UpdateScope {
        val user = User(id=123L, email=email, role=UserRole.NormalUser)
        mockBackend.showByEmail(email) returns Future.successful(Some(user))
        override def data = Seq("is_admin" -> "true")
        h.status(result) must beEqualTo(h.NO_CONTENT) // finish request
        there was one(mockBackend).updateIsAdmin(123L, true)
      }

      "demote a user" in new UpdateScope {
        val user = User(id=123L, email=email, role=UserRole.Administrator)
        mockBackend.showByEmail(email) returns Future.successful(Some(user))
        override def data = Seq("is_admin" -> "false")
        h.status(result) must beEqualTo(h.NO_CONTENT) // finish request
        there was one(mockBackend).updateIsAdmin(123L, false)
      }

      "not change password when not given" in new UpdateScope {
        val user = User(id=123L, email=email, passwordHash="hash")
        mockBackend.showByEmail(email) returns Future.successful(Some(user))
        override def data = Seq("is_admin" -> "false")
        h.status(result) must beEqualTo(h.NO_CONTENT) // finish request
        there was no(mockBackend).updatePasswordHash(any, any)
      }

      "change password when one is given" in new UpdateScope {
        val user = User(id=123L, email=email, passwordHash="hash")
        mockBackend.showByEmail(email) returns Future.successful(Some(user))
        override def data = Seq("password" -> "as;dj#$xfF")
        h.status(result) must beEqualTo(h.NO_CONTENT) // finish request
        there was one(mockBackend).updatePasswordHash(any, beLike[String] { case (s: String) =>
          "as;dj#$xfF".isBcrypted(s) must beTrue
        })
      }

      "return BadRequest for the current user" in new UpdateScope {
        override val email = request.user.email
        override def data = Seq("is_admin" -> "false")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockBackend).updatePasswordHash(any, any)
        there was no(mockBackend).updateIsAdmin(any, any)
      }
    }

    "delete" should {
      trait DeleteScope extends BaseScope {
        val email = "user2@example.org"
        def request = fakeAuthorizedRequest
        override def result = controller.delete(email)(request)
      }

      "return BadRequest for the current user" in new DeleteScope {
        override val email = request.user.email
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "return NotFound for a non-user" in new DeleteScope {
        mockBackend.showByEmail(email) returns Future.successful(None)
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "delete a user" in new DeleteScope {
        val userToDelete = User(id=123L)
        mockBackend.showByEmail(email) returns Future.successful(Some(userToDelete))
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockBackend).destroy(123L)
      }
    }

    "create" should {
      trait CreateScope extends BaseScope {
        def data : Seq[(String,String)] = Seq(
          "email" -> "user2@example.org",
          "password" -> ";lasd#@sdf3F"
        )
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(data: _*)
        override def result = controller.create()(request)
      }

      "return BadRequest when the user already exists" in new CreateScope {
        mockBackend.create(any) returns Future.failed(new Conflict(new Throwable()))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "return BadRequest when the form is not entered properly" in new CreateScope {
        override def data = Seq("email" -> "")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "when successful" should {
        trait CreatedScope extends CreateScope {
          var stored: Option[User.CreateAttributes] = _
          mockBackend.create(any[User.CreateAttributes]) answers { x =>
            val attrs = x.asInstanceOf[User.CreateAttributes]
            stored = Some(attrs)
            Future.successful(User(email=attrs.email))
          }
          h.status(result) // store the user
        }

        "return Ok" in new CreatedScope {
          h.status(result) must beEqualTo(h.OK)
        }

        "store the user" in new CreatedScope {
          stored must beSome
          stored.map(_.email) must beSome("user2@example.org")
        }

        "store a valid password" in new CreatedScope {
          val hash = stored.map(_.passwordHash).getOrElse("")
          ";lasd#@sdf3F".isBcrypted(hash) must beTrue
        }

        "set confirmedAt" in new CreatedScope {
          stored.map(_.confirmedAt) must beSome
        }

        "set treeTooltipsEnabled=true" in new CreatedScope {
          stored.map(_.treeTooltipsEnabled) must beSome(true)
        }

        "return the user" in new CreatedScope {
          val s = h.contentAsString(result)
          s must /("email" -> "user2@example.org")
        }
      }
    }
  }
}
