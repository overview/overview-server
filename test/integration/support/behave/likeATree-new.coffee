module.exports = likeATree = (opts) ->
  describe 'likeATree', ->
    beforeEach ->
      # Start from a fresh page load
      @browser
        .refresh()
        .shortcuts.documentSet.waitUntilStable()

    it 'should show a document list title', ->
      @browser
        .getText(id: 'document-list-title')
          .then((text) -> expect(text).to.match(/\d+ documents?/))

    opts.documents?.forEach (document) ->
      it "should show a #{document.type} document with title #{document.title}", ->
        extra = switch document.type
          when 'text'
            => @browser.assertExists(tag: 'pre', contains: document.contains, wait: 'pageLoad')
          when 'pdf'
            => @browser.assertExists(css: '.document[data-document-type=pdf] iframe')

        @browser
          .click(tag: 'h3', contains: document.title)
          .assertExists(tag: 'h2', contains: document.title, wait: true) # wait for animation
          .find(tag: 'div', class: 'keywords', contains: 'Key words: ', wait: true) # again, wait for animation

        extra()

    opts.searches?.forEach (search) ->
      it "should search for #{search.query}", ->
        @browser
          .sendKeys(search.query, css: '#tree-app-search [name=query]')
          .shortcuts.jquery.listenForAjaxComplete()
          .click(css: '#tree-app-search button[type=submit]')
          .shortcuts.jquery.waitUntilAjaxComplete() # waits for something to change
          .assertExists(tag: 'h4', contains: "#{search.nResults} document", wait: 'pageLoad')

    opts.ignoredWords?.forEach (word) ->
      it "should show ignored word #{word}", ->
        @browser
          .click(css: 'ul.view-tabs li.active .toggle-popover')
          .assertExists(tag: 'dd', contains: word)
          .click(css: 'ul.view-tabs li.active .toggle-popover')

    opts.importantWords?.forEach (word) ->
      it "should show important word #{word}", ->
        @browser
          .click(css: 'ul.view-tabs li.active .toggle-popover')
          .assertExists(tag: 'dd', contains: word)
          .click(css: 'ul.view-tabs li.active .toggle-popover')
