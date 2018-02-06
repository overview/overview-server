<div class="document">
  {{#if displayType === 'documentCloud'}}
    <DocumentCloudDocumentView :document :preferences />
  {{elseif displayType === 'https'}}
    <HttpDocumentView :document />
  {{elseif displayType === 'pdf'}}
    <PdfDocumentView ref:pdfDocumentView :document :preferences :highlightQ />
  {{elseif displayType === 'twitter'}}
    <TwitterDocumentView :document />
  {{elseif document && document.displayUrl}}
    <UnknownDocumentView :document />
  {{else}}
    <NullDocumentView :document />
  {{/if}}
</div>

<script>
  import DocumentCloudDocumentView from './DocumentCloudDocumentView'
  import HttpDocumentView from './HttpDocumentView'
  import NullDocumentView from './NullDocumentView'
  import PdfDocumentView from './PdfDocumentView'
  import TwitterDocumentView from './TwitterDocumentView'
  import UnknownDocumentView from './UnknownDocumentView'

  export default {
    data() {
      return {
        document: null,
        preferences: null,
        highlightQ: null,
      }
    },

    computed: {
      displayType: (document) => document ? document.displayType : null,
    },

    components: {
      DocumentCloudDocumentView,
      HttpDocumentView,
      NullDocumentView,
      PdfDocumentView,
      TwitterDocumentView,
      UnknownDocumentView,
    },

    methods: {
      beginCreatePdfNote() {
        const child = this.refs.pdfDocumentView
        if (child) child.beginCreatePdfNote()
      }
    },
  }
</script>
