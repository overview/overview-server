asUserUploadingFiles = require('../support/asUserUploadingFiles')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')

Url =
  index: '/documentsets'
  pdfUpload: '/imports/file'

describe 'PdfUpload', ->
  asUserUploadingFiles('PdfUpload')

  testMethods.usingPromiseChainMethods
    waitForJobsToComplete: ->
      @
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('progress').length == 0), 15000)

    deleteTopUpload: ->
      @
        .get(Url.index)
        .elementByCss('.actions .dropdown-toggle').click()
        .acceptingNextAlert()
        .elementByCss('.delete-document-set').click()

    loadImportedTree: (name) ->
      @
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: name, visible: true).click()

  describe 'after uploading pdfs', ->
    before ->
      @userBrowser
        .get('/tour?X-HTTP-Method-Override=DELETE')
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .chooseFile('PdfUpload/Cat2.pdf')
        .chooseFile('PdfUpload/Cat3.pdf')
        .chooseFile('PdfUpload/Cat4.pdf')
        .chooseFile('PdfUpload/Jules1.pdf')
        .chooseFile('PdfUpload/Jules2.pdf')
        .chooseFile('PdfUpload/Jules3.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files', visible: true).click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true)
        .elementBy(tag: 'input', name: 'name').type('Pdf Upload')
        .doImport()
        .waitForJobsToComplete()

    after ->
      @userBrowser
        .deleteTopUpload()

    it 'should show document set', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy({tag: 'h3', contains: 'Pdf Upload'}, 10000).should.eventually.exist
        .elementBy(tag: 'a', contains: 'Pdf Upload').click() # go back to where we were
        .waitForJqueryReady()

    shouldBehaveLikeATree
      documents: [
        { type: 'pdf', title: 'Cat4.pdf' }
      ]
      searches: [
        { query: 'chase', nResults: 4 }
      ]

  describe 'after uploading one pdf split by page', ->
    before ->
      @userBrowser
        .get('/tour?X-HTTP-Method-Override=DELETE')
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'label', contains: 'Each page is one document').click()
        .doImport()
        .waitForJobsToComplete()

    after ->
      @userBrowser
        .deleteTopUpload()

    shouldBehaveLikeATree
      documents: [
        { type: 'pdf', title: 'Cat1.pdf – page 1' },
        { type: 'pdf', title: 'Cat1.pdf – page 2' },
        { type: 'pdf', title: 'Cat1.pdf – page 3' }
      ]
      searches: [
        { query: 'face', nResults: 3 }
      ]

  describe 'after uploading three pdfs', ->
    before ->
      @userBrowser
        .get('/tour?X-HTTP-Method-Override=DELETE')
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .chooseFile('PdfUpload/Cat2.pdf')
        .chooseFile('PdfUpload/Cat3.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'input', name: 'split_documents', value: true).click()
        .doImport()
        .waitForJobsToComplete()

    after ->
      @userBrowser
        .deleteTopUpload()

    shouldBehaveLikeATree
      documents: [
        { type: 'pdf', title: 'Cat1.pdf – page 3' }
        { type: 'pdf', title: 'Cat2.pdf – page 4' },
        { type: 'pdf', title: 'Cat3.pdf – page 4' }
      ]
      searches: [
        { query: 'burrow', nResults: 9 }
      ]
