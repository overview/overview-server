Q = require('q')
chai = require('chai')
chaiAsPromised = require('chai-as-promised')
mochaAsPromised = require('mocha-as-promised')
wd = require('wd')

chaiAsPromised.transferPromiseness = wd.transferPromiseness

chai.should()
mochaAsPromised()
chai.use(chaiAsPromised)

global.expect = chai.expect
global.Q = Q
