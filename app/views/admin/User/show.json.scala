package views.json.admin.User

import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.{Json, JsValue}

import models.orm.{User, UserRole}

object show {
  private val dateFormatter = ISODateTimeFormat.dateTime()

  private def formatDate(date: java.util.Date) = dateFormatter.print(new DateTime(date))

  def apply(user: User) = Json.obj(
    "id" -> user.id,
    "email" -> user.email,
    "is_admin" -> Json.toJson(user.role == UserRole.Administrator),
    "confirmation_sent_at" -> user.confirmationSentAt.map(formatDate),
    "confirmed_at" -> user.confirmedAt.map(formatDate),
    "last_activity_at" -> user.lastActivityAt.map(formatDate)
  )
}
