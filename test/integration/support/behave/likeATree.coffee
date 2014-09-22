testMethods = require('../testMethods')
wd = require('wd')

module.exports = (opts) ->
  testMethods.usingPromiseChainMethods
    waitForDocumentListToLoad: ->
      # It's loaded when "#document-list-title.loading" changes to
      # "#document-list-title.loaded"
      @waitForElementByCss('#document-list-title.loaded', 5000)

  before ->
    @likeATree = {}
    @userBrowser
      .url()
      .then((url) => @likeATree.url = url)

  beforeEach ->
    # Pick up new promise chain methods
    @userBrowser = @userBrowser.noop()

  afterEach ->
    @userBrowser
      .elementByCss('#tree-app-tree button.refresh').click()

  it 'should show a document list title', ->
    @userBrowser
      .waitForDocumentListToLoad()
      .elementById('document-list-title').text().should.eventually.match(/\d+ document/)

  if 'documents' of opts
    for document in opts.documents
      it "should show a #{document.type} document with title #{document.title}", ->
        extra = =>
          switch document.type
            when 'text'
              @userBrowser
                .waitForElementBy({ tag: 'pre', contains: document.contains }, 10000).should.eventually.exist
            when 'pdf'
              @userBrowser
                .waitForElementBy({ tag: 'object', type: 'application/pdf'}, 10000).should.eventually.exists

        @userBrowser
          .waitForDocumentListToLoad()
          .elementBy(tag: 'h3', contains: document.title).click()
          .elementBy(tag: 'h3', contains: document.title).should.eventually.exist
          .elementBy(tag: 'h3', contains: 'Key words: ').should.eventually.exist
          .then(extra)

  if 'searches' of opts
    for search in opts.searches
      it "should search for #{search.query}", ->
        @userBrowser
          .elementByCss('#tree-app-search [name=query]').type(search.query)
          .listenForJqueryAjaxComplete()
          .elementByCss('#tree-app-search input[type=submit]').click()
          .waitForJqueryAjaxComplete() # wait for UI to clear previous search results
          .waitForElementBy({ tag: 'h4', contains: "#{search.nResults} document" }, 20000).should.eventually.exist

  if 'ignoredWords' of opts
    for word in opts.ignoredWords
      it "should show ignored word #{word}", ->
        @userBrowser
          .elementByCss("li.active a .toggle-popover").click()
          .elementBy(tag: 'dd', contains: word).should.eventually.exist

  if 'importantWords' of opts
    for word in opts.importantWords
      it "should show important word #{word}", ->
        @userBrowser
          .elementByCss("li.active a .toggle-popover").click()
          .elementBy(tag: 'dd', contains: word).should.eventually.exist
          
