'use strict'

// Set globals
const chai = require('chai')
const chaiAsPromised = require('chai-as-promised')

chai.should()
chai.use(chaiAsPromised)

global.expect = chai.expect

// Run Selenium while the tests run
process.env.SELENIUM_PROMISE_MANAGER = '0'

const child_process = require('child_process')
const chromedriver = require('chromedriver')
const net = require('net')

// Tries to connect() to a given port on localhost. Returns when
// connect() succeeds. Retries indefinitely.
function waitForConnect(port, done) {
  const retry = () => waitForConnect(port, done)

  const socket = new net.Socket()
  socket.on('error', () => setTimeout(retry, 50))
  socket.on('connect', () => { socket.end('Hello there\n'); done() })
  socket.connect(port)
}

// Runs web browser listening for WebDriver on port 4444
//
// Calls cb once the webdriver is listening
function runBrowser(cb) {
  const child = child_process.spawn(
    chromedriver.path,
    [
      '--port=4444',
      //'--verbose', // uncomment this for overwhelming data
    ]
  )

  // For some verbosity:
  //const logging = require('selenium-webdriver').logging
  //logging.installConsoleHandler()
  //logging.getLogger('').setLevel(logging.Level.ALL)

  child.stdout.pipe(process.stdout)
  child.stderr.pipe(process.stderr)

  const done = () => process.nextTick(() => cb(child))

  waitForConnect(4444, done)
}

let runningBrowser = null

function killRunningBrowser() {
  if (runningBrowser !== null) {
    runningBrowser.kill()
    runningBrowser = null
  }
}

before(function(done) {
  if (process.env.SAUCE_USERNAME) {
    process.nextTick(done)
  } else {
    runBrowser(browser => {
      runningBrowser = browser
      process.on('exit', killRunningBrowser) // in case Mocha exits before the after() hook is called
      process.nextTick(done)
    })
  }
})

after(killRunningBrowser)
