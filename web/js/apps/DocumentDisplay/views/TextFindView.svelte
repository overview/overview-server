{{#if highlightQ}}
  <div class="find">
    <div class="label">{{t('label', highlightQ, highlightIndex + 1, nHighlights)}}</div>
    <button on:click='onClickPrevious()' class="btn-link previous-highlight" title={{t('previousHighlight')}}><i class="icon overview-icon-chevron-left"></i></button>
    <button on:click='onClickNext()' class="btn-link next-highlight" title={{t('nextHighlight')}}><i class="icon overview-icon-chevron-right"></i></button>
  </div>
{{/if}}

<script>
  import i18n from 'i18n'

  export default {
    data() {
      return {
        nHighlights: 0,
        highlightIndex: 0,
        highlightQ: null,
        t: i18n.namespaced('views.Document.show.FindView'),
      }
    },

    methods: {
      onClickNext() {
        const highlightIndex = this.get('highlightIndex') || 0
        const nHighlights = this.get('nHighlights') || 1
        const newHighlightIndex = (highlightIndex + 1) % nHighlights
        this.fire('changeHighlightIndex', { highlightIndex: newHighlightIndex })
      },

      onClickPrevious() {
        const highlightIndex = this.get('highlightIndex') || 0
        const nHighlights = this.get('nHighlights') || 1
        const newHighlightIndex = (highlightIndex + nHighlights - 1) % nHighlights
        this.fire('changeHighlightIndex', { highlightIndex: newHighlightIndex })
      },
    },
  }
</script>
