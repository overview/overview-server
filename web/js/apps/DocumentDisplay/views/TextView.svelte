<div class="text">
  {{#await textWithHighlights}}
    <div class="loading">{{t('loading')}}</div>
  {{then plainAndHighlightedPairs}}
    {{#if isFromOcr}}
      <p class="is-from-ocr">{{{t('isFromOcr_html')}}}</p>
    {{/if}}

    {{#if isOnlyTextAvailable}}
      <p class="only-text-available">{{t('onlyTextAvailable')}}</p>
    {{/if}}

    <pre ref:pre class={{wrapText ? 'wrap' : ''}}>{{#each plainAndHighlightedPairs as [ plain, highlighted ], i}}{{plain}}{{#if highlighted}}<em class={{highlightIndex === i ? 'current' : ''}}>{{highlighted}}</em>{{/if}}{{/each}}</pre>

    <TextFindView on:changeHighlightIndex='handleChangeHighlightIndex(event)' nHighlights={{(plainAndHighlightedPairs.length || 1) - 1}} highlightQ={{highlightQ}} highlightIndex={{highlightIndex}} />
  {{catch error}}
    <div class="error">{{console.warn(error.message, error) || t('error', error.message)}}</div>
  {{/await}}
</div>

<script>
  import i18n from 'i18n'
  import TextFindView from './TextFindView'

  const NullText = Promise.resolve('')
  const NullHighlights = Promise.resolve([])

  function scrollToEmWithinPre(em, pre) {
    const buffer = 20 // px

    const current = {
      top: pre.scrollTop,
      bottom: pre.scrollTop + pre.clientHeight,
      left: pre.scrollLeft,
      right: pre.scrollLeft + pre.clientWidth,
    }

    const wanted = {
      top: em.offsetTop,
      bottom: em.offsetTop + em.offsetHeight,
      left: em.offsetLeft,
      right: em.offsetLeft + em.offsetWidth,
    }

    if (wanted.bottom + buffer > current.bottom) {
      pre.scrollTop = Math.min(pre.scrollHeight - pre.clientHeight, wanted.bottom + buffer - pre.clientHeight)
    }
    if (wanted.right + buffer > current.right) {
      pre.scrollLeft = Math.min(pre.scrollWidth - pre.clientWidth, wanted.right + buffer - pre.clientWidth)
    }
    if (wanted.left - buffer < current.left) {
      pre.scrollLeft = Math.max(0, wanted.left - buffer)
    }
    if (wanted.top - buffer < current.top) {
      pre.scrollTop = Math.max(0, wanted.top - buffer)
    }
  }

  function handleHighlightScrolling(textView) {
    // IF we've rendered text
    // AND we have a highlight index
    // then ASSUMING we have an em.highlighted.current somewhere
    // ... scroll to it.
    const awaitTextWithHighlights = (textWithHighlights) => {
      textWithHighlights.then(() => {
        // TODO figure out Svelte's guarantees. [adam, 2018-02-02] I suspect
        // at this point we don't actually know whether the DOM has updated;
        // but we know Svelte will update it synchronously, so we should
        // schedule the scroll ASAP.
        window.setTimeout(scrollToCurrentHighlight, 0)
      })
    }
    const scrollToCurrentHighlight = () => {
      const pre = textView.refs.pre
      if (!pre) return

      const em = textView.refs.pre.querySelector('em.current')
      if (!em) return

      scrollToEmWithinPre(em, pre)
    }

    // We want to auto-scroll to the highlight WHEN:
    // * The user switches documents
    // * The user navigates through highlights
    textView.observe('textWithHighlights', awaitTextWithHighlights)
    textView.observe('highlightIndex', scrollToCurrentHighlight, { defer: true }) // after render
  }

  export default {
    data() {
      return {
        document: null,
        highlightQ: null,
        highlightIndex: 0,
        transactionQueue: null,
        isOnlyTextAvailable: false,
        preferences: null,
        t: i18n.namespaced('views.Document.show.TextView'),
      }
    },

    computed: {
      documentId: (document) => document ? document.id : null,
      isFromOcr: (document) => document ? document.isFromOcr : null,
      wrapText: (preferences) => preferences && preferences.wrap || false,

      text: (documentId, transactionQueue) => {
        if (documentId) {
          return transactionQueue.ajax({ url: `/documents/${documentId}.txt` })
        } else {
          return NullText
        }
      },

      highlights: (documentId, highlightQ, transactionQueue) => {
        if (documentId && highlightQ) {
          const documentSetId = Math.floor(documentId / Math.pow(2, 32))
          const url = `/documentsets/${documentSetId}/documents/${documentId}/highlights?q=${encodeURIComponent(highlightQ)}`
          return transactionQueue.ajax({ url })
        } else {
          return NullHighlights
        }
      },

      /**
       * Array of [ plainString, highlightedString ] pairs.
       *
       * This Array has nHighlights+1 entries: think of it as an Array
       * alternating between plain and highlighted, _always_ beginning and
       * ending with plain. The first and last plainString entries may be
       * empty; the final highlightedString is always empty; all other strings
       * are guaranteed to be non-empty.
       */
      textWithHighlights: (text, highlights) => {
        return Promise.all([ text, highlights ])
          .then(([ text, highlights ]) => {
            let pos = 0
            const ret = []

            for (const [ begin, end ] of highlights) {
              ret.push([
                text.substring(pos, begin),
                text.substring(begin, end),
              ])
              pos = end
            }

            ret.push([
              text.substring(pos),
              '',
            ])

            return ret
          })
      }
    },

    components: {
      TextFindView,
    },

    methods: {
      handleChangeHighlightIndex(ev) {
        this.set({ highlightIndex: ev.highlightIndex })
      },
    },

    oncreate() {
      handleHighlightScrolling(this)

      // Reset highlight when data changes
      this.observe('textWithHighlights', () => this.set({ highlightIndex: 0 }))
    },
  }
</script>
