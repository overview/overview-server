package steps

import models.OverviewDatabase
import org.overviewproject.tree.orm.DocumentSetUser
import models.orm.stores.DocumentSetUserStore
import controllers.routes
import org.overviewproject.tree.Ownership

class DocumentSetIndexSteps extends BaseSteps {
  When("""^I browse to the document set index$"""){ () =>
    browser.goTo(routes.DocumentSetController.index(0).url)
  }
}
