# This file ought to be in support/testSuite.coffee, but Mocha shuns it there
child_process = require('child_process')
jar = require('selenium-server-standalone-jar')
phantomjs = require('phantomjs')
net = require('net')
request = require('request')

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

waitForStringInUrl = (string, url, done) ->
  retry = -> waitForStringInUrl(string, url, done)

  request url, (error, response, body) ->
    #console.log("Inspecting #{url} for #{string}")
    if error?
      throw error

    if body?.indexOf(string) != -1
      #console.log("Found #{string}")
      done()
    else
      setTimeout(retry, 100)

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

  done = -> process.nextTick -> cb(child)

  waitForStringInUrl("http://127.0.0.1:#{port}", "http://localhost:4444/grid/console", done)

before (done) ->
  startSelenium (selenium) ->
    process.on('exit', -> selenium.kill())
    # We need more than one PhantomJS. Each PhantomJS instance allows one require('browser').create() chain
    startPhantomjs 9011, (phantomjs1) ->
      process.on('exit', -> phantomjs1.kill())
      startPhantomjs 9012, (phantomjs2) ->
        process.on('exit', -> phantomjs2.kill())
        process.nextTick(done)
