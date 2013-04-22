package steps

import org.fluentlenium.core.filter.FilterConstructor.withText

class CsvImportSteps extends BaseSteps {
  When("""^I perform a CSV upload with the file "([^"]*)"$"""){ (arg0:String) =>
    CommonSteps.clickElement("a", "CSV upload")
    CommonSteps.waitForAnimationsToComplete
    CommonSteps.chooseFile("CsvUpload.csv")
    CommonSteps.waitForFileReadersToComplete
    CommonSteps.clickElement("button", "Upload")
    CommonSteps.waitForAjaxToComplete
    // There's a chance this waitForAjaxToComplete will race: the upload
    // AJAX will finish, then the worker-polling Ajax will begin, *then* this
    // method will return. That's acceptable, since we can't wait for redirect.
  }

  Then("""^I should see a CSV preview$"""){ () =>
    CommonSteps.waitForFileReadersToComplete
    val form = browser.findFirst("form.document-set-upload")
    val preview = form.findFirst(".preview")
    Option(preview) must beSome
    preview.getText must contain("text") // "text" is a column header
  }

  Then("""^I should not see a CSV preview$"""){ () =>
    val form = browser.findFirst("form.document-set-upload")
    val preview = form.findFirst(".preview")
    preview.isDisplayed must beFalse
  }
}
