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
        .sleep(5000) # async requests can time out; this won't
        .waitForFunctionToReturnTrueInBrowser((-> $?.isReady && $('.document-set-creation-jobs').length == 0), 15000)

    deleteTopUpload: ->
      @
        .get(Url.index)
        .acceptingNextAlert()
        .elementBy(tag: 'input', class: 'btn-danger', value: 'Delete').click()

    loadImportedTree: (name) ->
      @
        .waitForJobsToComplete()
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: name, visible: true).click()
      

  describe 'after uploading pdfs', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .chooseFile('PdfUpload/Cat2.pdf')
        .chooseFile('PdfUpload/Cat3.pdf')
        .chooseFile('PdfUpload/Cat4.pdf')
        .chooseFile('PdfUpload/Jules1.pdf')
        .chooseFile('PdfUpload/Jules2.pdf')
        .chooseFile('PdfUpload/Jules3.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files', visible: true).click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'textarea', name: 'supplied_stop_words', visible: true).type('moose frog')
        .elementBy(tag: 'textarea', name: 'important_words', visible: true).type('couch face')
        .doImport()
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
          .openFileUploadPage()
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

  describe 'after uploading one pdf', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .doImport()
        .loadImportedTree('Pdf Upload')
        
    shouldBehaveLikeATree
      documents: [
          { type: 'pdf', title: 'Cat1.pdf – page 1' },
          { type: 'pdf', title: 'Cat1.pdf – page 2' },
          { type: 'pdf', title: 'Cat1.pdf – page 3' }
      ]
      searches: [
        { query: 'face', nResults: 3 }
      ]

    after ->
      @userBrowser
        .deleteTopUpload()

  describe 'after uploading three pdfs', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .chooseFile('PdfUpload/Cat2.pdf')        
        .chooseFile('PdfUpload/Cat3.pdf')        
        .elementBy(tag: 'button', contains: 'Done adding files').click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'input', name: 'split_documents', value: true).click()
        .doImport()
        .loadImportedTree('Pdf Upload')
        
  
    shouldBehaveLikeATree
      documents: [
          { type: 'pdf', title: 'Cat1.pdf – page 3' }
          { type: 'pdf', title: 'Cat2.pdf – page 4' },
          { type: 'pdf', title: 'Cat3.pdf – page 4' }
      ]
      searches: [
        { query: 'burrow', nResults: 9 }
      ]
      

    after ->
      @userBrowser
        .deleteTopUpload()
                                                      

  
