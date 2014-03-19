asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')

Url =
  index: '/documentsets'

describe 'CsvUpload', ->
  testMethods.usingPromiseChainMethods
    openCsvUploadPage: ->
      @
        .get(Url.index)
        .waitForFunctionToReturnTrueInBrowser(-> $?.fn?.dropdown? && $.isReady)
        .elementBy(tag: 'a', contains: 'Import documents').click()
        .elementBy(tag: 'a', contains: 'Import from a CSV file').click()
        .waitForElementByCss('input[type=file]', wd.asserters.isDisplayed)

    chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .elementByCss('input[type=file]').sendKeys(fullPath)

    waitForRequirements: ->
      @
        .waitForFunctionToReturnTrueInBrowser(-> $('.requirements li.ok').length == 4 || $('.requirements li.bad').length > 0)

    doUpload: ->
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

    chooseAndDoUpload: (path) ->
      @
        .chooseFile(path)
        .waitForRequirements()
        .doUpload()

    deleteTopUpload: ->
      @
        .get(Url.index)
        .acceptingNextAlert()
        .elementBy(class: 'btn-danger', text: 'Delete').click()

    waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 5000)

  asUser.usingTemporaryUser()

  describe 'after uploading a document set', ->
    before ->
      @userBrowser
        .openCsvUploadPage()
        .chooseAndDoUpload('CsvUpload/basic.csv')
        .waitForJobsToComplete()
    after ->
      @userBrowser.deleteTopUpload()

    it 'should show the document set', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy(tag: 'h3', contains: 'basic.csv').should.eventually.exist

    describe 'in the default tree', ->
      before ->
        @userBrowser
          .get(Url.index)
          .waitForElementBy(tag: 'a', contains: 'basic.csv', visible: true).click()

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', text: 'This is the fourth document.' }
        ]
