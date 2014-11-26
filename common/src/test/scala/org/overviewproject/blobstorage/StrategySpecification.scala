package org.overviewproject.blobstorage

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope


class StrategySpecification
  extends Specification
  with StrategySpecHelper
  with Mockito
{

  trait BaseScope extends Scope {
  }
}
