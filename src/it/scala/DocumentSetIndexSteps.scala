package steps

import models.OverviewDatabase
import models.orm.DocumentSetUser
import models.orm.stores.DocumentSetUserStore
import controllers.routes
import org.overviewproject.tree.Ownership

class DocumentSetIndexSteps extends BaseSteps {
  When("""^I browse to the document set index$"""){ () =>
    browser.goTo(routes.DocumentSetController.index(0).url)
  }

  Then("""^I should see the document set "([^"]*)"$"""){ (title:String) =>
    browser.$(".document-sets li h2").getText must contain(title)
  }
}
