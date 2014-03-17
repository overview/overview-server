Q = require('q')
chai = require('chai')
chaiAsPromised = require('chai-as-promised')
wd = require('wd')

chaiAsPromised.transferPromiseness = wd.transferPromiseness

chai.should()
chai.use(chaiAsPromised)

global.expect = chai.expect
global.Q = Q
