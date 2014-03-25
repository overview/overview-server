testMethods = require('../testMethods')
wd = require('wd')

module.exports = (opts) ->
  testMethods.usingPromiseChainMethods
    waitForDocumentListToLoad: ->
      # It's loaded when "#document-list-title .loading" changes to
      # "#document-list-title .node"
      @waitForElementByCss('#document-list-title .node')

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
      .get(@likeATree.url)
      .waitForElementBy(tag: 'canvas')

  it 'should show a document list title', ->
    @userBrowser
      .waitForDocumentListToLoad()
      .elementById('document-list-title').text().should.eventually.match(/\d+ documents in folder “.*”/)

  if 'documents' of opts
    for document in opts.documents
      it "should show a #{document.type} document", ->
        extra = =>
          switch document.type
            when 'text'
              @userBrowser
                .elementBy(tag: 'pre', contains: document.contains).should.eventually.exist

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
          .waitForElementByCss('h4', wd.asserters.textInclude("#{search.nResults} document"), 20000).should.eventually.exist
