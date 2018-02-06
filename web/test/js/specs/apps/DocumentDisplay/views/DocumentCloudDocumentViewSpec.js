import DocumentCloudDocumentView from 'apps/DocumentDisplay/views/DocumentCloudDocumentView'

describe('DocumentCloudDocumentView', function() {
  beforeEach(function() {
    this.div = document.createElement('div')
    document.body.appendChild(this.div)
    this.view = new DocumentCloudDocumentView({
      target: this.div,
      data: {
      },
    })
  })

  afterEach(function() {
    this.view.destroy()
    document.body.removeChild(this.div)
  })

  it('should render an iframe', function() {
    this.view.set({
      document: {
        displayType: 'documentCloud',
        displayUrl: 'https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principalocum#p23',
      },
      preferences: {
        sidebar: false,
      },
    })

    const iframe = this.div.querySelector('iframe')

    expect(iframe).not.to.be.null
    expect(iframe.src).to.eq('https://www.documentcloud.org/documents/675478-letter-from-glen-burnie-high-school-principalocum?sidebar=false#p23')
  })
})
