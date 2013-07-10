package steps

import org.fluentlenium.core.filter.FilterConstructor.withText

class CsvImportSteps extends BaseSteps {
  When("""^I perform a CSV upload with the file "([^"]*)"$""") { (filename: String) =>
    CsvImportSteps.uploadFile(filename)
  }

  When("""^I wait for a CSV upload with the file "([^"]*)" to complete$""") { (filename: String) =>
    CsvImportSteps.uploadFile(filename)
    CommonSteps.waitForJobsToComplete
    CommonSteps.waitForAjaxToComplete
    CommonSteps.waitForAnimationsToComplete
  }

  Then("""^I should see a CSV preview$""") { () =>
    CommonSteps.waitForFileReadersToComplete
    val form = browser.findFirst("form.document-set-upload")
    val preview = form.findFirst(".preview")
    Option(preview) must beSome
    preview.getText must contain("text") // "text" is a column header
  }

  Then("""^I should not see a CSV preview$""") { () =>
    val form = browser.findFirst("form.document-set-upload")
    val preview = form.findFirst(".preview")
    preview.isDisplayed must beFalse
  }
}

object CsvImportSteps {

  def uploadFile(filename: String) = {
    CommonSteps.clickElement("a", "Import your documents")
    CommonSteps.clickElement("a", "Import from a CSV file")
    CommonSteps.waitForAnimationsToComplete
    CommonSteps.chooseFile(filename)
    CommonSteps.waitForFileReadersToComplete
    CommonSteps.clickElement("button", "Upload")
    CommonSteps.waitForAjaxToComplete
    // There's a chance this waitForAjaxToComplete will race: the upload
    // AJAX will finish, then the worker-polling Ajax will begin, *then* this
    // method will return. That's acceptable, since we can't wait for redirect.

  }
}
