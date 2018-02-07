<div class={{`showing-${visibleDisplay}${highlightQ && ' highlighting' || ''}`}}>
  {{#if documentViewDocument}}
    <DocumentView
      on:changePdfNotes="fire('changePdfNotes', event)"
      ref:documentView
      document={{documentViewDocument}}
      preferences={{preferences}}
      highlightQ={{highlightQ}}
      />
  {{/if}}
  {{#if textViewDocument}}
    <TextView
      document={{textViewDocument}}
      highlightQ={{highlightQ}}
      transactionQueue={{transactionQueue}}
      isOnlyTextAvailable={{isOnlyTextAvailable}}
      preferences={{preferences}}
      />
  {{/if}}
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

      // The document ... or null if we're only showing document.
      //
      // We assume TextView is resource-intensive: if we're only showing
      // document we get huge network, CPU and memory savings here.
      textViewDocument: (document, visibleDisplay) => visibleDisplay === 'text' ? document : null,
    },

    methods: {
      beginCreatePdfNote() {
        this.refs.documentView.beginCreatePdfNote()
      },

      goToPdfNote(note) {
        this.refs.documentView.goToPdfNote(note)
      },
    },

    components: {
      DocumentView,
      TextView,
    },
  }
</script>
