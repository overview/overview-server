const View = require('apps/Show/views/DocumentDetailLinksView')

const Backbone = require('backbone')

function buildLink(url) {
  return {
    url: url,
    title: 'Title for ' + url,
    text: 'Text for ' + url,
    iconClass: 'url',
  }
}

describe('DocumentDetailLinksView', function() {
  beforeEach(function() {
    this.views = new Backbone.Collection([])
    this.view = new View({ views: this.views })
    this.div = document.createElement('div')
    this.div.className = 'document-detail-links-view-spec-container'
    this.div.style.position = 'relative'
    document.body.appendChild(this.div)
  })

  afterEach(function() {
    this.view.remove()
    document.body.removeChild(this.div)
  })

  it('should add a link when adding a view', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    expect(this.view.el.childNodes.length).to.eq(1)
    const li = this.view.el.childNodes[0]
    expect(li.tagName).to.eq('LI')
    expect(li.childNodes.length).to.eq(1)
    const a = li.childNodes[0]
    expect(a.title).to.eq('Title for http://foo')
    expect(a.textContent).to.contain('Text for http://foo')
    const i = a.childNodes[0]
    expect(i.classList.contains('icon-url')).to.be.true
  })

  // Do we need this?
  //it('should leave a link when adding a view with a duplicate url', function() {
  //  this.views.add({ documentDetailLink: buildLink('http://foo') })
  //  // Test we don't overwrite a link. This is important because integration
  //  // tests might try to click on an old link instead of a new one that is
  //  // being written into the same spot. (They'd have no way of knowing when
  //  // the link has been overwritten.)
  //  const li1 = this.view.el.childNodes[0]
  //  this.views.add({ documentDetailLink: buildLink('http://foo') })
  //  expect(this.view.el.childNodes.length).to.eq(1)
  //  expect(this.view.el.childNodes[0]).to.eq(li1, 'HTML element was erroneously overwritten')
  //})

  it('should do nothing when creating a view without a url', function() {
    this.views.add({ documentDetailLink: null })
    expect(this.view.el.childNodes.length).to.eq(0)
  })

  it('should edit a url', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.at(0).set({ documentDetailLink: buildLink('http://bar') })
    expect(this.view.el.childNodes.length).to.eq(1)
    expect(this.view.el.childNodes[0].childNodes[0].getAttribute('data-url')).to.eq('http://bar')
  })

  it('should add a link when editing a duplicate url', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.at(0).set({ documentDetailLink: buildLink('http://bar') })
    expect(this.view.el.childNodes.length).to.eq(2)
    expect(this.view.el.childNodes[0].childNodes[0].getAttribute('data-url')).to.eq('http://bar')
    expect(this.view.el.childNodes[1].childNodes[0].getAttribute('data-url')).to.eq('http://foo')
  })

  it('should do nothing when editing a duplicate url to be another duplicate url', function() {
    // Keep the view order in mind. If we change foo,foo,bar to bar,foo,bar,
    // we want the data-url to be for bar,foo -- not foo,bar. But in this test,
    // we are changing foo,foo,bar to foo,bar,bar -- preserving link order.
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.add({ documentDetailLink: buildLink('http://foo') }) // we'll change this one
    this.views.add({ documentDetailLink: buildLink('http://bar') })
    this.views.at(1).set({ documentDetailLink: buildLink('http://bar') })
    expect(this.view.el.childNodes.length).to.eq(2)
    expect(this.view.el.childNodes[0].childNodes[0].getAttribute('data-url')).to.eq('http://foo')
    expect(this.view.el.childNodes[1].childNodes[0].getAttribute('data-url')).to.eq('http://bar')
  })

  it('should nix a link when editing a url to be a duplicate url', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.add({ documentDetailLink: buildLink('http://bar') })
    this.views.at(1).set({ documentDetailLink: buildLink('http://foo') })
    expect(this.view.el.childNodes.length).to.eq(1)
    expect(this.view.el.childNodes[0].childNodes[0].getAttribute('data-url')).to.eq('http://foo')
  })

  it('should nix a link when setting a url to null', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.at(0).set({ documentDetailLink: null })
    expect(this.view.el.innerHTML).to.eq('')
  })

  it('should nix a link when removing a view', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.remove(this.views.at(0))
    expect(this.view.el.innerHTML).to.eq('')
  })

  it('should do nothing when removing a duplicate-url view', function() {
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.add({ documentDetailLink: buildLink('http://foo') })
    this.views.remove(this.views.at(0))
    expect(this.view.el.childNodes.length).to.eq(1)
  })

  it('should open a link in an iframe', function() {
    this.view.setDocumentId(3)
    this.views.add({
      apiToken: 'a-token',
      serverUrlFromPlugin: 'http://server-from-plugin',
      documentDetailLink: buildLink('data:text/plain,/foo/:documentId')
    })
    this.view.el.childNodes[0].childNodes[0].click()
    const iframe = document.querySelector('iframe#view-document-detail')
    expect(iframe).to.exist
    expect(iframe.getAttribute('src')).to.match(/data:text\/plain,\/foo\/3\?apiToken=a-token&server=http%3A%2F%2Fserver-from-plugin/)
  })

  it('should default server to document.location.origin', function() {
    this.view.setDocumentId(3)
    this.views.add({
      apiToken: 'a-token',
      documentDetailLink: buildLink('data:text/plain,/foo/:documentId')
    })
    this.view.el.childNodes[0].childNodes[0].click()
    const iframe = document.querySelector('iframe#view-document-detail')
    expect(iframe.getAttribute('src')).to.match(/data:text\/plain,\/foo\/3\?apiToken=a-token&server=http%3A%2F%2Flocalhost%3A9876/) // That's Karma's server
  })

  it('should remove the opened iframe on click', function() {
    this.view.setDocumentId(3)
    this.views.add({ apiToken: 'a-token', serverUrlFromPlugin: 'http://server-from-plugin', documentDetailLink: buildLink('data:text/plain,url') })
    this.view.el.childNodes[0].childNodes[0].click()
    document.querySelector('.document-detail-background').click()
    const iframe = document.querySelector('iframe#view-document-detail')
    expect(iframe).not.to.exist
  })
})
