asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  pdfUpload: '/imports/pdf'


describe 'ImportError', ->
  testMethods.usingPromiseChainMethods
    openPdfUploadPage: ->
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

    waitForJobsToComplete: ->
      @
        .sleep(5000) # async requests can time out; this won't
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 15000)

    cancelTopJob: ->
      @
        .get(Url.index)
        .acceptingNextAlert()
        .elementBy(tag: 'input', class: 'btn-danger', value: 'Cancel Import').click()

    loadImportedTree: (name) ->
      @
        .waitForJobsToComplete()
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: name, visible: true).click()
      
  asUser.usingTemporaryUser(title: 'PdfUpload')

  describe 'after importing too few documents', ->
    before ->
      @userBrowser
        .openPdfUploadPage()
        .chooseFile('PdfUpload/OnePage.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files', visible: true).click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .doImport()

    after ->
      @userBrowser
        .cancelTopJob()
    
    it 'should show Too Few Documents error', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy({tag: 'h2', contains: 'Pdf Upload'}, 10000).should.eventually.exist
        .waitForElementBy({tag: 'span', class: 'state', contains: 'stalled'}).should.eventually.exist
        .waitForElementBy({tag: 'span', class: 'state-description', contains: 'Need at least 2 documents'}).should.eventually.exist
        


  
