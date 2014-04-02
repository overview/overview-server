asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')

Url =
  index: '/documentsets'
  csvUpload: '/imports/csv'

isSelected = new wd.asserters.Asserter((target, cb) -> target.isSelected(cb))

describe 'CsvUpload', ->
  testMethods.usingPromiseChainMethods
    openCsvUploadPage: ->
      @
        .get(Url.csvUpload)
        .waitForJqueryReady()

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
          if !err && url == firstUrl
            err = "Expected URL to change, but it is still #{firstUrl}"
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
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 10000)

  asUser.usingTemporaryUser()

  describe 'finding a character set', ->
    testMethods.usingPromiseChainMethods
      loadCsvAndWaitForRequirements: (path) ->
        @
          .openCsvUploadPage()
          .chooseFile(path)
          .waitForRequirements()

      shouldHaveLoadedCsvWithText: (text) ->
        @
          .elementByCss('.requirements li.text.ok').should.eventually.exist
          .elementByCss('.requirements li.csv.ok').should.eventually.exist
          .elementByCss('.requirements li.header.ok').should.eventually.exist
          .elementByCss('.requirements li.data.ok').should.eventually.exist
          .elementByCss('.preview table').text().should.eventually.contain(text)

    it 'should load UTF-8', ->
      @userBrowser
        .loadCsvAndWaitForRequirements('CsvUpload/basic-utf8.csv')
        .elementByCss('select[name=charset] option', isSelected).text().should.eventually.equal('Unicode (UTF-8)')
        .shouldHaveLoadedCsvWithText('achète avec des €')

    it 'should load Windows-1252', ->
      @userBrowser
        .loadCsvAndWaitForRequirements('CsvUpload/basic-windows-1252.csv')
        .elementByCss('select[name=charset] option', isSelected).text().should.eventually.equal('Unicode (UTF-8)')
        .elementByCss('.requirements li.text.bad').should.eventually.exist
        .elementByCss('.preview table').text().should.eventually.contain('ach�te avec des �')
        .elementByCss('select[name=charset] option[value=windows-1252]').click()
        .shouldHaveLoadedCsvWithText('achète avec des €')

    it 'should reset the form, including encoding', ->
      @userBrowser
        .loadCsvAndWaitForRequirements('CsvUpload/basic-windows-1252.csv')
        .elementByCss('select[name=charset] option[value=windows-1252]').click()
        .elementBy(tag: 'button', contains: 'Reset').click()
        .chooseFile('CsvUpload/basic-utf8.csv')
        .waitForRequirements()
        .elementByCss('select[name=charset] option', isSelected).text().should.eventually.equal('Unicode (UTF-8)')
        .shouldHaveLoadedCsvWithText('achète avec des €')

    it 'should show an error when there is no text column', ->
      @userBrowser
        .loadCsvAndWaitForRequirements('CsvUpload/basic-no-text.csv')
        .elementByCss('.requirements li.header.bad').should.eventually.exist

    it 'should show an error when there are too few documents', ->
      @userBrowser
        .loadCsvAndWaitForRequirements('CsvUpload/basic-2docs.csv')
        .elementByCss('.requirements li.data.bad').should.eventually.exist

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
        searches: [
          { query: 'document', nResults: 4 }
        ]
