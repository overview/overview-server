<div class="document-view-http"
  ><iframe title="" id="document-contents-http" src={{url}}></iframe
></div>

<script>
  // CSV you can upload to localhost or production to test with real sites:
  // url,text
  // https://www.facebook.com/topic/Doritos/105611692805738,Doritos Facebook Status
  // http://example.org/,HTTP page
  // https://example.com/,HTTPS page

  export default {
    data() {
      return {
        document: null,
      }
    },

    computed: {
      url: (document) => document && document.displayUrl || 'about:blank',
    },
  }
</script>
