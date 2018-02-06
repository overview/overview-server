<div class="document-view-unknown">
  <p class="message">{{t('unknown')}}</p>
  <p class="link"><a href={{url}} target="_blank">{{url}}</a></p>
</div>

<script>
  import i18n from 'i18n'

  export default {
    data() {
      return {
        document: null,
        t: i18n.namespaced('views.Document.show.DocumentView'),
      }
    },

    computed: {
      url: (document) => document && document.displayUrl || 'about:blank',
    },
  }
</script>
