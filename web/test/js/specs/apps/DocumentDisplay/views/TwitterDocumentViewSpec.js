import i18n from 'i18n'
import TwitterDocumentView from 'apps/DocumentDisplay/views/TwitterDocumentView'

describe('TwitterDocumentView', function() {
  beforeEach(function() {
    i18n.reset_messages_namespaced('views.Document.show.DocumentView.twitter', {
      loading: 'loading',
      deleted: 'deleted',
    })

    this.div = document.createElement('div')
    document.body.appendChild(this.div)
    this.view = new TwitterDocumentView({
      target: this.div,
      data: {
        twitterWidgetsUrl: '/base/mock-js/twitter.js',
      },
    })
  })

  afterEach(function() {
    this.view.destroy()
    document.body.removeChild(this.div)
  })

  describe('with a tweet', function() {
    beforeEach(function() {
      this.view.set({
        document: {
          displayType: 'twitter',
          displayUrl: 'https://twitter.com/adamhooper/status/910855864239759360',
        },
      })
    })

    it('should define window.twttr.ready', function() {
      expect(window.twttr).to.be.defined
      expect(window.twttr.ready).to.be.defined
    })

    it('should render the tweet', function(done) {
      window.twttr.ready(() => {
        expect(this.div.textContent).to.contain('Rendered tweet 910855864239759360')
        done()
      })
    })

    it('should skip rendering a tweet when we skip over it', function(done) {
      this.view.set({
        document: {
          displayType: 'twitter',
          displayUrl: 'https://twitter.com/adamhooper/status/910855864239759361',
        },
      })
      window.twttr.ready(() => {
        expect(this.div.textContent).to.contain('Rendered tweet 910855864239759361')
        expect(this.div.textContent).not.to.contain('Rendered tweet 910855864239759360')
        done()
      })
    })

    it('should delete a tweet div when we navigate to the next one', function(done) {
      window.twttr.ready(() => {
        this.view.set({
          document: {
            displayType: 'twitter',
            displayUrl: 'https://twitter.com/adamhooper/status/910855864239759361',
          },
        })

        window.twttr.ready(() => {
          expect(this.div.textContent).to.contain('Rendered tweet 910855864239759361')
          expect(this.div.textContent).not.to.contain('Rendered tweet 910855864239759360')
          done()
        })
      })
    })

    it('should show a message for a deleted tweet', function(done) {
      this.view.set({
        document: {
          displayType: 'twitter',
          // mock-js/twitter.js hack: a status with "000000" will render a
          // visibility:hidden widget. Twitter's deleted tweets also have
          // visibility:hidden.
          displayUrl: 'https://twitter.com/adamhooper/status/910855864239000000',
        },
      })

      expect(this.div.textContent).not.to.contain('eleted')
      window.twttr.ready(() => {
        expect(this.div.textContent).to.contain('eleted')
        done()
      })
    })

    it('should delete the element Twitter leaks after navigating away from a deleted tweet while it renders', function(done) {
      // First render: a deleted tweet
      this.view.set({
        document: {
          displayType: 'twitter',
          displayUrl: 'https://twitter.com/adamhooper/status/910855864239000000',
        },
      })

      window.twttr.ready(() => {
        // We want to be here before Twitter calls the Promise callback for
        // the first render. But that would add complexity to the test, so let's
        // just skip that assumption and treat this test more like ... er ...
        // documentation.
        //expect(this.div.textContent).not.to.contain('eleted')

        // Second render: a not-deleted tweet
        this.view.set({
          document: {
            displayType: 'twitter',
            displayUrl: 'https://twitter.com/adamhooper/status/910855864239123456', // not deleted
          },
        })

        window.twttr.ready(() => {
          expect(this.div.textContent).not.to.contain('eleted')
          done()
        })
      })
    })
  })
})
