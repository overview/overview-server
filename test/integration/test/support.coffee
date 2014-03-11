# This file ought to be in support/testSuite.coffee, but Mocha shuns it there
child_process = require('child_process')
jar = require('selenium-server-standalone-jar')
phantomjs = require('phantomjs')
net = require('net')

java = 'java'

# Tries to connect() to a given port on localhost. Calls done() (and drops the
# connection) when the connect() succeeds. Retries otherwise.
waitForConnect = (port, done) ->
  retry = -> waitForConnect(port, done)

  socket = new net.Socket()
  socket.on('error', -> setTimeout(retry, 50))
  socket.on('connect', -> socket.end(); done())
  socket.connect(port)
  undefined

# Runs child process for selenium-server-standalone
#
# Calls the callback with the child process once Selenium starts listening
# on port 4444.
startSelenium = (cb) ->
  child = child_process.spawn(
    java,
    [
      '-Dselenium.LOGGER.level=WARNING',
      '-jar', jar.path,
      '-role', 'hub'
    ]
  )

  child.stdout.pipe(process.stdout)
  child.stderr.pipe(process.stderr)

  done = -> process.nextTick -> cb(child)

  waitForConnect(4444, done)

# Runs child processes for phantomjs with GhostDriver
#
# See https://github.com/detro/ghostdriver
#
# Calls the callback with the child process.
startPhantomjs = (port, cb) ->
  child = child_process.spawn(
    phantomjs.path,
    [
      "--webdriver=#{port}"
      '--webdriver-loglevel=WARN'
      '--webdriver-selenium-grid-hub=http://localhost:4444'
    ]
  )

  child.stdout.pipe(process.stdout)
  child.stderr.pipe(process.stderr)

  # There's a race condition. PhantomJS starts listening before Selenium can
  # route to it. I saw an error once in ~30 calls. 250ms should be plenty.
  done = -> setTimeout((-> cb(child)), 250)

  waitForConnect(9011, done)

before (done) ->
  startSelenium (selenium) ->
    process.on('exit', -> selenium.kill())
    # We need more than one PhantomJS. Each PhantomJS instance allows one require('browser').create() chain
    startPhantomjs 9011, (phantomjs1) ->
      process.on('exit', -> phantomjs1.kill())
      startPhantomjs 9012, (phantomjs2) ->
        process.on('exit', -> phantomjs2.kill())
        process.nextTick(done)
