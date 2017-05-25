chai = require('chai')
chaiAsPromised = require('chai-as-promised')

chai.should()
chai.use(chaiAsPromised)

global.expect = chai.expect
