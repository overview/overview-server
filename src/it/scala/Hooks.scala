package steps

class Hooks extends BaseSteps {
  Before {
    Framework.setUp
  }

  Before("@worker") {
    Framework.ensureWorker
  }

  After {
    Framework.tearDown
  }
}
