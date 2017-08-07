'use strict'

const chai = require('chai')
chai.use(require('chai-jquery'))
chai.use(require('chai-as-promised'))
chai.use(require('sinon-chai'))

window.expect = chai.expect
window.sinon = require('sinon')

const testsContext = require.context('.', true, /Spec$/i)
testsContext.keys().forEach(testsContext)
