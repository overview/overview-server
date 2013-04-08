package steps

class Hooks extends BaseSteps {
  Before {
    Framework.setUp
  }

  After {
    Framework.tearDown
  }
}
