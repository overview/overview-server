import Backbone from 'backbone'
import i18n from 'i18n'
import TextView from 'apps/DocumentDisplay/views/TextView'

afterRenders = (callback) =>
  setTimeout(callback, 2)

describe 'apps/DocumentDisplay/views/TextView', ->
  beforeEach ->
    i18n.reset_messages_namespaced 'views.Document.show',
      'FindView.label': 'finding,{0},{1},{2}'
      'FindView.nextHighlight': 'nextHighlight'
      'FindView.previousHighlight': 'previousHighlight'
      'TextView.loading': 'loading'
      'TextView.error': 'error,{0}'
      'TextView.onlyTextAvailable': 'onlyTextAvailable'
      'TextView.isFromOcr_html': 'isFromOcr_html'

    @transactionQueue = {
      ajax: sinon.stub()
    }

    @div = document.createElement('div')
    document.body.appendChild(@div)
    @view = new TextView({
      target: @div,
      data: {
        transactionQueue: @transactionQueue,
      },
    })

    @sandbox = sinon.sandbox.create()

    @stubbingTextAndHighlights = (text, highlights, callback) =>
      @transactionQueue.ajax
        .withArgs(sinon.match(url: sinon.match(/\d+\.txt$/)))
        .returns(Promise.resolve(text))

      @transactionQueue.ajax
        .withArgs(sinon.match(url: sinon.match(/\d+\/highlights\?q=.*/)))
        .returns(Promise.resolve(highlights))

      callback()

  afterEach ->
    @view.destroy()
    document.body.removeChild(@div)
    @sandbox.restore()

  it 'should render a loading message when loading', ->
    @view.set({ document: { id: 123, }})
    expect(@div.querySelector('.loading')).not.to.eq(null)

  it 'should render an error message when loading fails', (done) ->
    @sandbox.stub(console, 'warn')
    @transactionQueue.ajax.returns(Promise.reject(new Error('an error named Foo')))
    @view.set({ document: { id: 123, }})

    afterRenders =>
      error = @div.querySelector('.error')
      expect(error).not.to.eq(null)
      expect(error.textContent).to.match(/an error named Foo/)
      done()

  it 'should render text when loading succeeds', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, }})

      afterRenders =>
        pre = @div.querySelector('pre')
        expect(pre).not.to.eq(null)
        expect(pre.textContent).to.eq('Here is the text')
        done()

  it 'should query for text based on document ID', ->
    @view.set({ document: { id: 123, } })

    expect(@transactionQueue.ajax).to.have.been.calledWith(sinon.match(url: '/documents/123.txt'))
    expect(@transactionQueue.ajax).not.to.have.been.calledTwice

  it 'should query for highlights along with text', ->
    @view.set({ document: { id: 120259088178, }, highlightQ: 'foo' })

    expect(@transactionQueue.ajax).to.have.been.calledWith(sinon.match(url: '/documents/120259088178.txt'))
    expect(@transactionQueue.ajax).to.have.been.calledWith(sinon.match(url: '/documentsets/28/documents/120259088178/highlights?q=foo'))

  it 'should render highlights with text', (done) ->
    @stubbingTextAndHighlights 'foo bar moo mar', [ [ 4, 7 ], [ 12, 15 ] ], =>
      @view.set({ document: { id: 123, }, highlightQ: 'non-null' })

      afterRenders =>
        pre = @div.querySelector('pre')
        expect(pre.innerHTML.replace(/<!---->/g, '')).to.eq('foo <em class="current">bar</em> moo <em class="">mar</em>')
        done()

  it 'should change the current highlight', (done) ->
    @stubbingTextAndHighlights 'foo bar moo mar', [ [ 4, 7 ], [ 12, 15 ] ], =>
      @view.set({ document: { id: 123, }, highlightQ: 'non-null' })

      afterRenders =>
        @view.set({ highlightIndex: 1 })

        pre = @div.querySelector('pre')
        expect(pre.innerHTML.replace(/<!---->/g, '')).to.eq('foo <em class="">bar</em> moo <em class="current">mar</em>')
        done()

  it 'should wrap when preference is set', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, }, preferences: { wrap: true } })

      afterRenders =>
        expect(@div.querySelector('pre').classList.contains('wrap')).to.be.true
        done()

  it 'should not wrap when preference is unset', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, }, preferences: { wrap: false } })

      afterRenders =>
        expect(@div.querySelector('pre').classList.contains('wrap')).to.be.false
        done()

  it 'should show only-text-available when set', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, }, isOnlyTextAvailable: true })

      afterRenders =>
        expect(@div.querySelector('.only-text-available')).not.to.be.null
        done()

  it 'should not show only-text-available when unset', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, }, isOnlyTextAvailable: false })

      afterRenders =>
        expect(@div.querySelector('.only-text-available')).to.be.null
        done()

  it 'should render is-from-ocr when the document is from OCR', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, isFromOcr: true }})

      afterRenders =>
        expect(@div.querySelector('.is-from-ocr')).not.to.be.null
        done()

  it 'should not render is-from-ocr when the document is from OCR', (done) ->
    @stubbingTextAndHighlights 'Here is the text', [], =>
      @view.set({ document: { id: 123, isFromOcr: false }})

      afterRenders =>
        expect(@div.querySelector('.is-from-ocr')).to.be.null
        done()
