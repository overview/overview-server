selenium = require('selenium-standalone')

# This ought to be in support/testSuite.coffee, but Mocha shuns it there
seleniumserver = null

before ->
  seleniumServer = selenium(stdio: 'pipe')
  seleniumServer.stdout.on('data', process.stdout.write.bind(process.stdout))

after ->
  seleniumServer?.kill()
