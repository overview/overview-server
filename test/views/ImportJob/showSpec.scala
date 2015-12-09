package views.json.ImportJob

import java.time.Instant
import org.specs2.mock.Mockito

import com.overviewdocs.models.ImportJob

class showSpec extends views.ViewSpecification with Mockito {
  trait BaseScope extends JsonViewSpecificationScope {
    val importJob = smartMock[ImportJob]
    importJob.documentSetId returns 1L
    importJob.progress returns Some(0.24)
    importJob.description returns Some(("foo", Seq()))
    importJob.estimatedCompletionTime returns Some(Instant.ofEpochMilli(1234567L))

    override def result = show(importJob)
  }

  "show" should {
    "show documentSetId" in new BaseScope {
      json must /("documentSetId" -> 1)
    }

    "show progress when it exists" in new BaseScope {
      json must /("progress" -> 0.24)
    }

    "show null progress" in new BaseScope {
      importJob.progress returns None
      json must contain(""""progress":null""")
    }

    "show description when it exists" in new BaseScope {
      json must /("description" -> "foo")
    }

    "show null description" in new BaseScope {
      importJob.description returns None
      json must contain(""""description":null""")
    }

    "show completion time" in new BaseScope {
      json must /("estimatedCompletionTime" -> 1234567)
    }

    "show null completion time" in new BaseScope {
      importJob.estimatedCompletionTime returns None
      json must contain(""""estimatedCompletionTime":null""")
    }
  }
}
