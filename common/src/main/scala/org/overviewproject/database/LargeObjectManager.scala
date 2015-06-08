package org.overviewproject.database

import org.postgresql.PGConnection
import org.postgresql.largeobject.{LargeObjectManager=>PGLargeObjectManager}
import slick.dbio.{DBIOAction,Effect,NoStream,SynchronousDatabaseAction}
import slick.jdbc.JdbcBackend
import slick.util.DumpInfo

/** Provides access to Large Objects for a Postgres database.
  *
  * Usage:
  *
  * ```
  * loManager = database.largeObjectManager
  * val action = for {
  *   lo &lt;- loManager.create(LargeObjects.Mode.ReadWrite)
  *   _ &lt;- lo.write(bytes)
  * }
  * database.run(action.transactionally) // Must be transactionally
  * ```
  *
  * Or:
  *
  * ```
  * loManager = database.largeObjectManager
  * val action = for {
  *   loid &lt;- Things.filter(_.id === id).map(_.loid).result.head
  *   lo &lt;- loManager.open(loid, LargeObject.Mode.ReadWrite)
  *   _ &lt;- lo.seek(123, LargeObject.Seek.SEEK_CUR)
  *   _ &lt;- lo.write(Array(1, 2, 3).map(_.toByte))
  * }
  * database.run(action.transactionally)
  * ```
  */
class LargeObjectManager(database: Database) {
  /** Creates a large object on the database and returns its OID.
    *
    * Usually you'll want to open right after create. You can do that with
    * createAndOpen.
    */
  def create
  : DBIOAction[Long, NoStream, Effect.Write with Effect.Transactional] = {
    LargeObjectManager.CreateAction
  }

  /** Opens an existing large object.
    */
  def open(oid: Long, mode: LargeObject.Mode)
  : DBIOAction[LargeObject, NoStream, Effect.Read with Effect.Transactional] = {
    new LargeObjectManager.OpenAction(oid, mode)
  }

  /** Runs create, then opens the resulting large object.
    */
  def createAndOpen(mode: LargeObject.Mode) = {
    create.flatMap(open(_, mode))(database.executionContext)
  }

  /** Deletes an existing large object.
    *
    * We do not cleverly handle cases in which the large object does not exist.
    * That's because this is the wrong layer of abstraction. Postgres will throw
    * an error, which means the transaction cannot be used until a ROLLBACK.
    *
    * This method is named "unlink", not "delete", because "unlink" is the C API
    * name.
    */
  def unlink(oid: Long)
  : DBIOAction[Unit, NoStream, Effect.Write with Effect.Transactional] = {
    new LargeObjectManager.UnlinkAction(oid)
  }
}

object LargeObjectManager {
  private abstract class LargeObjectManagerAction[R, -E <: Effect]
    extends SynchronousDatabaseAction[R, NoStream, JdbcBackend, E]
  {
    protected def pgMode(mode: LargeObject.Mode): Int = mode match {
      case LargeObject.Mode.Read => PGLargeObjectManager.READ
      case LargeObject.Mode.ReadWrite => PGLargeObjectManager.READWRITE
      case LargeObject.Mode.Write => PGLargeObjectManager.WRITE
    }

    protected def pgConnection(context: JdbcBackend#Context): PGConnection = {
      context.connection.unwrap(classOf[PGConnection])
    }

    protected def manager(context: JdbcBackend#Context): PGLargeObjectManager = {
      pgConnection(context).getLargeObjectAPI
    }
  }

  private object CreateAction
    extends LargeObjectManagerAction[Long, Effect.Write with Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObjectManager.CreateAction")
    override def run(context: JdbcBackend#Context): Long = manager(context).createLO
  }

  private class OpenAction(oid: Long, mode: LargeObject.Mode)
    extends LargeObjectManagerAction[LargeObject, Effect.Read with Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObjectManager.OpenAction", s"$oid, ${pgMode(mode)}")
    override def run(context: JdbcBackend#Context): LargeObject = {
      val pgLargeObject = manager(context).open(oid, pgMode(mode))
      new LargeObject(pgLargeObject)
    }
  }

  private class UnlinkAction(oid: Long)
    extends LargeObjectManagerAction[Unit, Effect.Write with Effect.Transactional]
  {
    override def getDumpInfo = DumpInfo("LargeObjectManager.UnlinkAction", oid.toString)
    override def run(context: JdbcBackend#Context): Unit = manager(context).unlink(oid)
  }
}
