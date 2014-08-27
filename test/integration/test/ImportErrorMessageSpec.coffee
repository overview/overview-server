asUserUploadingFiles = require('../support/asUserUploadingFiles')
shouldBehaveLikeATree = require('../support/behave/likeATree')
testMethods = require('../support/testMethods')
wd = require('wd')


Url =
  index: '/documentsets'
  pdfUpload: '/imports/pdf'


describe 'ImportError', ->
  asUserUploadingFiles('ImportErrorMessage')
  
  testMethods.usingPromiseChainMethods
  
    cancelTopJob: ->
      @
        .get(Url.index)
        .acceptingNextAlert()
        .elementBy(tag: 'input', class: 'btn-danger', value: 'Delete').click()

  describe 'after importing too few documents', ->
    before ->
      @userBrowser
        .openFileUploadPage()
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
        .waitForElementBy(tag: 'a', contains: 'Pdf Upload', visible: true).click()
        .waitForElementBy(tag: 'div', class: 'job-app error', visible: true).should.eventually.exist
        .waitForElementBy(tag: 'p', class: 'status', contains: 'Need at least 2 documents').should.eventually.exist
        

  describe 'after entering a bad regex in Important Words', ->
    before ->
      @userBrowser
        .openFileUploadPage()
        .chooseFile('PdfUpload/Cat1.pdf')
        .elementBy(tag: 'button', contains: 'Done adding files', visible: true).click()
        .waitForElementBy(tag: 'input', name: 'name', visible: true).type('Pdf Upload')
        .elementBy(tag: 'textarea', name: 'important_words', visible: true).type('***')        
        .doImport()

    after ->
      @userBrowser
        .cancelTopJob()
    
    it 'should show Bad Pattern error', ->
      @userBrowser
        .get(Url.index)
        .waitForElementBy(tag: 'a', contains: 'Pdf Upload', visible: true).click()
        .waitForElementBy(tag: 'div', class: 'job-app error', visible: true).should.eventually.exist
        .waitForElementBy(tag: 'p', class: 'status', contains: 'Invalid pattern in Important Words').should.eventually.exist

        


  
