asUser = require('./asUser')
testMethods = require('./testMethods')
wd = require('wd')

Url =
  index: '/documentsets'

module.exports = (csvPath) ->
  testMethods.usingPromiseChainMethods
    goToFirstDocumentSet: ->
      @
        .get(Url.index)
        .waitForElementByCss('.document-sets h3 a, .document-sets h6 a').click()
        .waitForElementByCss('canvas')

    wds_openCsvUploadPage: ->
      @
        .get(Url.index)
        .waitForFunctionToReturnTrueInBrowser(-> $?.fn?.dropdown? && $.isReady)
        .elementBy(tag: 'a', contains: 'Import documents').click()
        .elementBy(tag: 'a', contains: 'Import from a CSV file').click()
        .waitForElementByCss('input[type=file]', wd.asserters.isDisplayed)

    wds_chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .elementByCss('input[type=file]').sendKeys(fullPath)

    wds_waitForRequirements: ->
      @
        .waitForFunctionToReturnTrueInBrowser(-> $('.requirements li.ok').length == 4 || $('.requirements li.bad').length > 0)

    wds_doUpload: ->
      firstUrl = null

      isAtNewUrl = new wd.asserters.Asserter (browser, cb) ->
        browser.url (err, url) ->
          if !err && url == originalUrl
            err = "Expected URL to change, but it is still #{originalUrl}"
          url = null if err
          cb(err, url)

      @
        .url (u) -> firstUrl = url
        .elementBy(tag: 'button', contains: 'Upload').click()
        .waitFor(isAtNewUrl, 5000)

    wds_chooseAndDoUpload: (path) ->
      @
        .wds_chooseFile(path)
        .wds_waitForRequirements()
        .wds_doUpload()

    wds_waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 10000)

  asUser.usingTemporaryUser
    before: ->
      @userBrowser
        .wds_openCsvUploadPage()
        .wds_chooseAndDoUpload(csvPath)
        .wds_waitForJobsToComplete()

    after: ->
      @userBrowser
        .get(Url.index)
        .acceptingNextAlert()
        .waitForElementBy(tag: 'input', class: 'btn-danger', value: 'Delete').click()
