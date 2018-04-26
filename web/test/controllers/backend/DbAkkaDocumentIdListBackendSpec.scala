package controllers.backend

import akka.stream.scaladsl.Sink
import org.specs2.mock.Mockito
import scala.concurrent.duration.Duration

import com.overviewdocs.models.DocumentIdList
import com.overviewdocs.messages.{DocumentSetCommands,Progress}
import test.helpers.InAppSpecification

class DbAkkaDocumentIdListBackendSpec extends DbBackendSpecification with InAppSpecification with Mockito {
  sequential

  trait BaseScope extends DbBackendScope {
    val remoteActorSystemModule = new MockRemoteActorSystemModule(app.actorSystem)
    implicit val actorSystem = remoteActorSystemModule.actorSystem
    implicit val timeout = remoteActorSystemModule.defaultTimeout
    val brokerProbe = remoteActorSystemModule.mockBroker

    val backend = new DbAkkaDocumentIdListBackend(database, remoteActorSystemModule)
    backend.idleTimeout = timeout.duration
  }

  "DbAkkaDocumentIdListBackend" should {
    "show" should {
      "ignore the message broker" in new BaseScope {
        await(backend.show(1, "foo"))
        brokerProbe.expectNoMessage(Duration.Zero)
      }

      "return None if the DocumentIdList does not exist" in new BaseScope {
        await(backend.show(1, "foo")) must beNone
      }

      "return a DocumentIdList" in new BaseScope {
        import database.api._
        factory.documentSet(2L)
        blockingDatabase.runUnit(sqlu"""
          INSERT INTO document_id_list (id, document_set_id, field_name, document_32bit_ids)
          VALUES (1, 2, 'foo', '{0,2,1}')
        """)
        await(backend.show(2, "foo")) must beSome(DocumentIdList(1L, 2, "foo", Vector(0,2,1)))
      }

      "filter by documentSetId" in new BaseScope {
        import database.api._
        factory.documentSet(2L)
        blockingDatabase.runUnit(sqlu"""
          INSERT INTO document_id_list (id, document_set_id, field_name, document_32bit_ids)
          VALUES (1, 2, 'foo', '{0,2,1}')
        """)
        await(backend.show(1, "foo")) must beNone
      }

      "filter by fieldName" in new BaseScope {
        import database.api._
        factory.documentSet(2L)
        blockingDatabase.runUnit(sqlu"""
          INSERT INTO document_id_list (id, document_set_id, field_name, document_32bit_ids)
          VALUES (1, 2, 'foo', '{0,2,1}')
        """)
        await(backend.show(2, "bar")) must beNone
      }
    }

    "createIfMissing" should {
      "send a message to the message broker" in new BaseScope {
        factory.documentSet(1L)

        // Materialize the Source. Don't await() the result, because the
        // source won't end until there's a Progress.SortDone
        val future = backend.createIfMissing(1, "bar").runWith(Sink.ignore)
        brokerProbe.expectMsg(DocumentSetCommands.SortField(1, "bar"))

        // Now finish the Source.
        brokerProbe.sender.tell(Progress.SortDone, brokerProbe.ref)
        await(future)
      }

      "give the correct Source values" in new BaseScope {
        factory.documentSet(1L)
        backend.maxNProgressEventsInBuffer = 4

        // Materialize the Source. Don't await() the result, because the
        // source won't end until there's a Progress.SortDone
        val future = backend.createIfMissing(1, "bar").runWith(Sink.seq)
        brokerProbe.expectMsg(DocumentSetCommands.SortField(1, "bar"))

        brokerProbe.sender.tell(Progress.Sorting(0.1), brokerProbe.ref)
        brokerProbe.sender.tell(Progress.Sorting(0.3), brokerProbe.ref)
        brokerProbe.sender.tell(Progress.Sorting(0.8), brokerProbe.ref)
        brokerProbe.sender.tell(Progress.SortDone, brokerProbe.ref)
        await(future) must beEqualTo(Vector(Progress.Sorting(0.1), Progress.Sorting(0.3), Progress.Sorting(0.8)))
      }

      "not send a message if the Array already exists" in new BaseScope {
        import database.api._
        factory.documentSet(2L)
        blockingDatabase.runUnit(sqlu"""
          INSERT INTO document_id_list (id, document_set_id, field_name, document_32bit_ids)
          VALUES (1, 2, 'foo', '{0,2,1}')
        """)
        await(backend.createIfMissing(2, "foo").runWith(Sink.ignore))
        brokerProbe.expectNoMessage(Duration.Zero)
      }

      "crash on timeout" in new BaseScope {
        factory.documentSet(2L)
        backend.idleTimeout = scala.concurrent.duration.Duration.Zero
        await(backend.createIfMissing(2, "foo").runWith(Sink.ignore)) must throwA[java.util.concurrent.TimeoutException]
      }
    }
  }
}
