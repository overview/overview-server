package org.overviewproject.database

import com.github.tminglei.slickpg._
import play.api.libs.json.{ JsObject, Json }
import slick.driver.{ JdbcTypesComponent, PostgresDriver }
import org.overviewproject.postgres.InetAddress
import org.overviewproject.models.{ DocumentSetCreationJobState, DocumentSetCreationJobType, UserRole }
import org.overviewproject.models.DocumentDisplayMethod

trait MyPostgresDriver extends PostgresDriver
  with PgArraySupport
  with PgNetSupport
  with PgEnumSupport {
  override val simple = new SimpleQLPlus {}
  override val api = new APIPlus {}

  trait OverviewColumnTypeImplicits extends JdbcTypesComponent { driver: PostgresDriver =>
  }

  trait CommonImplicitsPlus extends CommonImplicits
    with ArrayImplicits
    with NetImplicits
    with SimpleArrayPlainImplicits {
    implicit val jsonTextColumnType = MappedColumnType.base[JsObject, String](
      Json.stringify,
      Json.parse(_).as[JsObject])

    implicit val jsonTextOptionColumnType = jsonTextColumnType.optionType

    implicit val ipColumnType = MappedColumnType.base[InetAddress, InetString](
      (a: InetAddress) => InetString(a.getHostAddress),
      (s: InetString) => InetAddress.getByName(s.address))

    implicit val userRoleColumnType = MappedColumnType.base[UserRole.Value, Int](_.id, UserRole(_))

    implicit val jobTypeColumnType = MappedColumnType.base[DocumentSetCreationJobType.Value, Int](
      _.id,
      DocumentSetCreationJobType.apply)

    implicit val stateColumnType = MappedColumnType.base[DocumentSetCreationJobState.Value, Int](
      _.id,
      DocumentSetCreationJobState.apply)

    implicit val documentDisplayMethodTypeMapper =
      createEnumJdbcType("DocumentDisplayMethod", DocumentDisplayMethod)

    implicit val documentDisplayMethodListTypeMapper =
      createEnumListJdbcType("documentDisplayMethod", DocumentDisplayMethod)

    implicit val documentDisplayMethodColumnExtensionMethodsBuilder =
      createEnumColumnExtensionMethodsBuilder(DocumentDisplayMethod)

    implicit val documentDisplayMethodOptionColumnExtensionMethodsBuilder =
      createEnumOptionColumnExtensionMethodsBuilder(DocumentDisplayMethod)

  }

  trait APIPlus extends API with CommonImplicitsPlus
  trait SimpleQLPlus extends SimpleQL with CommonImplicitsPlus
}

/**
 * Our database driver.
 *
 * Usage:
 *
 *   import org.overviewproject.database.Slick.simple._
 *   ... do stuff like at http://slick.typesafe.com/doc/2.0.2
 */
object Slick extends MyPostgresDriver
