<div class="document-view-document-cloud"
  ><iframe title="" id="document-contents-document-cloud" src={{iframeUrl}}></iframe
></div>

<script>
  export default {
    data() {
      return {
        document: null,
        preferences: null,
      }
    },

    computed: {
      showSidebar: (preferences) => preferences && preferences.sidebar || false,
      iframeUrl: (document, showSidebar) => {
        if (!document) return 'about:blank'

        return document.displayUrl.replace(/(#p\d+)?$/, (__, p) => `?responsive=false&sidebar=${String(showSidebar)}${p || ''}`)
      }
    }
  }
</script>
-->
