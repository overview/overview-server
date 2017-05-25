# This file ought to be in support/testSuite.coffee, but Mocha shuns it there
child_process = require('child_process')
chromedriver = require('chromedriver')
net = require('net')

# Tries to connect() to a given port on localhost. Calls done() (and drops the
# connection) when the connect() succeeds. Retries otherwise.
waitForConnect = (port, done) ->
  retry = -> waitForConnect(port, done)

  socket = new net.Socket()
  socket.on('error', -> setTimeout(retry, 50))
  socket.on('connect', -> socket.end('Hello there\n'); done())
  socket.connect(port)
  undefined

# Runs web browser listening for WebDriver on port 4444
#
# Calls cb once the webdriver is listening
startSelenium = (cb) ->
  child = child_process.spawn(
    chromedriver.path,
    [
      '--port=4444',
      #'--verbose', # uncomment this for overwhelming data
    ],
  )

  # For some verbosity:
  #logging = require('selenium-webdriver').logging
  #logging.installConsoleHandler()
  #logging.getLogger('').setLevel(logging.Level.ALL)

  child.stdout.pipe(process.stdout)
  child.stderr.pipe(process.stderr)

  done = -> process.nextTick -> cb(child)

  waitForConnect(4444, done)

runningSelenium = null

before (done) ->
  if 'SAUCE_USERNAME' of process.env
    process.nextTick(done)
  else
    startSelenium (selenium) ->
      runningSelenium = selenium
      process.nextTick(done)

after ->
  runningSelenium?.kill()
