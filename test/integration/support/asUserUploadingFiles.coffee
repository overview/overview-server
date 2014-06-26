asUser = require('../support/asUser')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  pdfUpload: '/imports/pdf'

module.exports = (title) ->
  testMethods.usingPromiseChainMethods
    openFileUploadPage: ->
      @
        .get(Url.pdfUpload)
        .waitForJqueryReady()

    chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .executeFunction(-> $('.invisible-file-input').css(opacity: 1))
        .elementByCss('.invisible-file-input').sendKeys(fullPath)

    doImport: ->
      @
        .elementBy(tag: 'button', contains: 'Import documents').click()
        .waitForUrl(Url.index, 10000)

    asUser.usingTemporaryUser
      title: title

