asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'


userToTrXPath = (email) -> "//tr[contains(td[@class='email'], '#{email}')]"

describe 'Example Document Sets', ->

  testMethods.usingPromiseChainMethods
    waitForUserLoaded: (email) ->
      @
        .waitForElementByXPath(userToTrXPath(email))

    openCsvUploadPage: ->
      @
        .get(Url.index)
        .waitForFunctionToReturnTrueInBrowser(-> $?.fn?.dropdown? && $.isReady)
        .waitForElementByCss('.dropdown .btn', wd.asserters.isDisplayed, 5000).click()
        .elementBy(tag: 'a', contains: 'Import from a CSV file').click()
        .waitForElementByCss('input[type=file]', wd.asserters.isDisplayed)

    chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .elementByCss('input[type=file]').sendKeys(fullPath)

    openCloneExamplePage: ->
      @
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: 'Import documents').click()
        .elementBy(tag: 'a', contains: 'Import an example document set').click()

    cloneExample: ->
      isAtNewUrl = new wd.asserters.Asserter (browser, cb) ->
        browser.url (err, url) ->
          if !err && url == originalUrl
            err = "Expected URL to change, but it is still #{originalUrl}"
          url = null if err
          cb(err, url)

      @
        .waitForExampleToAppear()
        .waitForElementBy(tag: 'button', contains: 'Clone').click()
        .waitFor(isAtNewUrl, 5000)

    waitForExampleToAppear: ->
      @
        .waitForElementBy({ tag: 'ul', class: 'shared-document-sets' }, 10000)

      
    toggleExampleDocumentSet: ->
      @
        .waitForElementByCss('.show-sharing-settings', wd.asserters.isDisplayed, 10000).click()
        .waitForElementByCss('input[type=checkbox]', wd.asserters.isDisplayed, 10000).click()
        .waitForElementBy(tag: 'a', contains: 'Close', visible: 'true').click()

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
        .elementBy(tag: 'input', class: 'btn-danger', value: 'Delete').click()

        
    waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 5000)


  asUser.usingTemporaryUser()
  
  describe 'after being set as an example', ->
    before ->
      @adminBrowser
        .openCsvUploadPage()
        .chooseAndDoUpload('CsvUpload/basic.csv')
        .waitForJobsToComplete()
        .toggleExampleDocumentSet()
        .then((a) =>
          @userBrowser
            .openCloneExamplePage()
            .cloneExample())

             
    after ->
      Q.all([
        @userBrowser
          .deleteTopUpload()
        @adminBrowser
          .deleteTopUpload()
      ])


    it 'should be cloneable',  ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy(tag: 'h3', contains: 'basic.csv').should.eventually.exist
        
    describe 'the cloned example', ->
      before ->
        @userBrowser
          .waitForElementBy(tag: 'a', contains: 'basic.csv').click()
          .waitForElementBy(tag: 'canvas')

      shouldBehaveLikeATree
        documents: [
          { type: 'text', title: 'Fourth', contains: 'This is the fourth document.' }
        ]
        searches: [
          { query: 'document', nResults: 4 }
        ]

    describe 'after being removed as an example', ->
      before ->
        @adminBrowser
          .get(Url.index)
          .toggleExampleDocumentSet()

      after ->
        @adminBrowser
          .get(Url.index)
          .toggleExampleDocumentSet()
          
      it 'should not show up in example list', ->
        @userBrowser
          .openCloneExamplePage()
          .waitForElementBy(tag: 'p', contains: 'There are currently no example document sets.').should.eventually.exist

  describe 'after being deleted', ->

    after ->
      @userBrowser
        .deleteTopUpload()

        
    it 'should not have deleted cloned document set', ->
      @adminBrowser
        .openCsvUploadPage()
        .chooseAndDoUpload('CsvUpload/basic.csv')
        .waitForJobsToComplete()
        .toggleExampleDocumentSet()
        .then((a) =>
          @userBrowser
            .openCloneExamplePage()
            .cloneExample())
        .then((u) =>
          @adminBrowser
            .deleteTopUpload())
        .then((a) =>
          @userBrowser
            .get(Url.index)
            .waitForElementBy(tag: 'a', contains: 'basic.csv').should.eventually.exist)
            
            
          
