asUser = require('../support/asUser')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  pdfUpload: '/imports/pdf'

describe 'PdfUpload', ->
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
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 15000)

    deleteTopUpload: ->
      @
        .get(Url.index)
        .acceptingNextAlert()
        .elementBy(tag: 'input', class: 'btn-danger', value: 'Delete').click()

  asUser.usingTemporaryUser(title: 'PdfUpload')

  describe 'after uploading pdfs', ->
    before ->
      @userBrowser
        .openPdfUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .chooseFile('PdfUpload/Cat2.pdf')
        .chooseFile('PdfUpload/Cat3.pdf')
        .chooseFile('PdfUpload/Cat4.pdf')
        .chooseFile('PdfUpload/Jules1.pdf')
        .chooseFile('PdfUpload/Jules2.pdf')
        .chooseFile('PdfUpload/Jules3.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'textarea', name: 'supplied_stop_words', visible: true).type('moose frog')
        .elementBy(tag: 'textarea', name: 'important_words', visible: true).type('couch face')
        .doImport()
        .sleep(5000) # async requests can time out; this won't
        .waitForJobsToComplete()

    after ->
      @userBrowser
        .deleteTopUpload()
    
    it 'should show document set', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy({tag: 'h3', contains: 'Pdf Upload'}, 10000).should.eventually.exist
        
    describe 'in the default tree', ->
      before ->
        @userBrowser
          .get(Url.index)
          .waitForElementBy(tag: 'a', contains: 'Pdf Upload', visible: true).click()

          
      shouldBehaveLikeATree
        documents: [
          { type: 'pdf', title: 'Cat4.pdf' }
        ]
        searches: [
          { query: 'chase', nResults: 4 }
        ]
        ignoredWords: [ 'moose', 'frog' ]
        importantWords: [ 'couch', 'face' ]

  describe 'after uploading a pdf with page splitting', ->
    before ->
      @userBrowser
        .openPdfUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .doImport()
        .sleep(5000) # async requests can time out; this won't
        .waitForJobsToComplete()
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: 'Pdf Upload', visible: true).click()


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

                                                      


  
