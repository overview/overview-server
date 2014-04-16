asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  csvUpload: '/imports/csv'
  publicDocumentSets: '/public-document-sets'

userToTrXPath = (email) -> "//tr[contains(td[@class='email'], '#{email}')]"

describe 'ExampleDocumentSets', ->

  testMethods.usingPromiseChainMethods
    waitForUserLoaded: (email) ->
      @
        .waitForElementByXPath(userToTrXPath(email))

    openCsvUploadPage: ->
      @
        .get(Url.csvUpload)
        .waitForElementByCss('input[type=file]')

    chooseFile: (path) ->
      fullPath = "#{__dirname}/../files/#{path}"
      @
        .elementByCss('input[type=file]').sendKeys(fullPath)

    cloneExample: ->
      @
        .waitForJqueryReady()
        .waitForElementBy(tag: 'button', contains: 'Clone').click()
        .waitForUrl(Url.index)

    toggleExampleDocumentSet: ->
      checkbox = { tag: 'label', contains: 'Set as example document set', visible: true }

      @
        .elementBy(tag: 'a', contains: 'Share').click()
        .waitForElementBy(checkbox, 10000)
        .listenForJqueryAjaxComplete()
        .elementBy(checkbox).click()
        .waitForElementBy(tag: 'a', contains: 'Close', visible: true).click()

    waitForRequirements: ->
      @
        .waitForFunctionToReturnTrueInBrowser(-> $('.requirements li.ok').length == 4 || $('.requirements li.bad').length > 0)

    doUpload: ->
      @
        .elementBy(tag: 'button', contains: 'Upload').click()
        .waitForUrl(Url.index, 10000)

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
        .waitForJqueryReady()
        .waitForFunctionToReturnTrueInBrowser((-> $('.document-set-creation-jobs').length == 0), 10000)

  asUser.usingTemporaryUser(title: 'ExampleDocumentSets')
  
  describe 'after being set as an example', ->
    before ->
      @adminBrowser
        .openCsvUploadPage()
        .chooseAndDoUpload('CsvUpload/basic.csv')
        .waitForJobsToComplete()
        .toggleExampleDocumentSet()
        .then =>
          @userBrowser
            .get(Url.publicDocumentSets)
            .cloneExample()

             
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
          .get(Url.publicDocumentSets)
          .waitForElementBy(tag: 'p', contains: 'There are currently no example document sets.').should.eventually.exist

    it 'should keep clone after original is deleted', ->
      @adminBrowser
        .openCsvUploadPage()
        .chooseAndDoUpload('CsvUpload/basic.csv')
        .waitForJobsToComplete()
        .toggleExampleDocumentSet()
        .then =>
          @userBrowser
            .get(Url.publicDocumentSets)
            .cloneExample()
        .then =>
          @adminBrowser
            .deleteTopUpload()
        .then =>
          @userBrowser
            .get(Url.index)
            .waitForElementBy(tag: 'a', contains: 'basic.csv').should.eventually.exist
            .deleteTopUpload()
