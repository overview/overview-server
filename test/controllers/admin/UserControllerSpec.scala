package controllers.admin

import com.github.t3hnar.bcrypt._
import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.mvc.Result
import scala.concurrent.Future

import models.{User,UserRole}
import org.overviewproject.tree.orm.finders.ResultPage

class UserControllerSpec extends controllers.ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val mockStorage = mock[UserController.Storage]

    val controller = new UserController {
      override val storage = mockStorage
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
        mockStorage.findUsers(any[Int]) answers((_) => ResultPage(users, controller.PageSize, page))
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
        mockStorage.findUser(email) returns None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest when is_admin is wrong" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email))
        override def data = Seq("is_admin" -> "not true or false")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "promote a user" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email, role=UserRole.NormalUser))
        override def data = Seq("is_admin" -> "true")
        h.status(result) // finish request
        there was one(mockStorage).storeUser(beLike[User] { case (u: User) => u.role must beEqualTo(UserRole.Administrator) })
      }

      "demote a user" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email, role=UserRole.NormalUser))
        override def data = Seq("is_admin" -> "false")
        h.status(result) // finish request
        there was one(mockStorage).storeUser(beLike[User] { case (u: User) => u.role must beEqualTo(UserRole.NormalUser) })
      }

      "not change password when not given" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email, passwordHash="hash"))
        override def data = Seq("is_admin" -> "false")
        h.status(result) // finish request
        there was one(mockStorage).storeUser(beLike[User] { case (u: User) => u.passwordHash must beEqualTo("hash") })
      }

      "change password when one is given" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email, passwordHash="hash"))
        override def data = Seq("password" -> "as;dj#$xfF")
        h.status(result) // finish request
        there was one(mockStorage).storeUser(beLike[User] { case (u: User) => "as;dj#$xfF".isBcrypted(u.passwordHash) must beTrue })
      }

      "return NoContent" in new UpdateScope {
        mockStorage.findUser(email) returns Some(User(email=email))
        override def data = Seq("is_admin"-> "false")
        h.status(result) must beEqualTo(h.NO_CONTENT)
      }

      "return BadRequest for the current user" in new UpdateScope {
        override val email = request.user.email
        override def data = Seq("is_admin" -> "false")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
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
        mockStorage.findUser(email) returns None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest when deleting a user with document sets" in new DeleteScope {
        mockStorage.findUser(email) returns Some(User())
        mockStorage.countDocumentSetsForEmail(email) returns 4
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must beEqualTo("Document sets owned by user2@example.org must be removed before the user can be deleted.")
        there was no(mockStorage).deleteUser(any[User])
      }

      "delete a user" in new DeleteScope {
        val userToDelete = User()
        mockStorage.findUser(email) returns Some(userToDelete)
        mockStorage.countDocumentSetsForEmail(email) returns 0
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).deleteUser(any[User])
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
        mockStorage.findUser("user2@example.org") returns Some(User())
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockStorage).storeUser(any)
      }

      "return BadRequest when the form is not entered properly" in new CreateScope {
        override def data = Seq("email" -> "")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }

      "when successful" should {
        trait CreatedScope extends CreateScope {
          var user : Option[User] = None
          mockStorage.findUser(any) returns None
          mockStorage.storeUser(any[User]) answers { x => val u = x.asInstanceOf[User]; user = Some(u); u }
          h.status(result) // store the user
        }

        "return Ok" in new CreatedScope {
          h.status(result) must beEqualTo(h.OK)
        }

        "store the user" in new CreatedScope {
          user must beSome((u: User) => u.email must beEqualTo("user2@example.org"))
        }

        "store a valid password" in new CreatedScope {
          val hash = user.map(_.passwordHash).getOrElse("")
          ";lasd#@sdf3F".isBcrypted(hash) must beTrue
        }

        "set confirmedAt" in new CreatedScope {
          user.map(_.confirmedAt) must beSome
        }

        "set treeTooltipsEnabled=true" in new CreatedScope {
          user.map(_.treeTooltipsEnabled) must beSome(true)
        }

        "return the user" in new CreatedScope {
          val s = h.contentAsString(result)
          s must /("email" -> "user2@example.org")
        }
      }
    }
  }
}
