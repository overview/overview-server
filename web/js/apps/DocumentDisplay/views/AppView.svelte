<div class={{`showing-${visibleDisplay}${highlightQ && ' highlighting' || ''}`}}>
  <DocumentView ref:documentView document={{documentViewDocument}} preferences={{preferences}} highlightQ={{highlightQ}} />
  <TextView document={{document}} highlightQ={{highlightQ}} transactionQueue={{transactionQueue}} isOnlyTextAvailable={{isOnlyTextAvailable}} preferences={{preferences}} />
</div>

<script>
  import DocumentView from './DocumentView'
  import TextView from './TextView'

  export default {
    data() {
      return {
        document: null,
        highlightQ: null,
        preferences: null,
      }
    },

    computed: {
      // "text" or "document": what the user wants to show
      visibleDisplayPreference(preferences) {
        return (preferences && preferences.text) ? 'text' : 'document'
      },

      // "text" or "document", forcing "text" if the user wants "document" but there is none
      visibleDisplay(visibleDisplayPreference, isOnlyTextAvailable) {
        return isOnlyTextAvailable ? 'text' : visibleDisplayPreference
      },

      isOnlyTextAvailable: (document) => !(document && document.displayUrl),

      // The document ... or null if we're only showing text.
      //
      // We assume DocumentView is resource-intensive: if we're only showing
      // text we get huge network, CPU and memory savings here.
      documentViewDocument: (document, visibleDisplay) => visibleDisplay === 'document' ? document : null,
    },

    methods: {
      beginCreatePdfNote() {
        this.refs.documentView.beginCreatePdfNote()
      },
    },

    components: {
      DocumentView,
      TextView,
    },
  }
</script>
