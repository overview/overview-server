define [ 'i18n' ], (i18n) ->
  t = i18n.namespaced('views.Tree.show.helpers.TextHighlightHelper')

   # If there are no highlights, call this with highlights=[]
  addHighlights: (text, highlights) ->
    position = 0

    nodes = []

    for [ begin, end ] in highlights
      #escape these for safety
      text1 = text.substring(position, begin)
      text2 = text.substring(begin, end)

      if text1.length
        nodes.push(text1)

      node2 = []
      node2.push('<em class="highlight">')
      node2.push(text2)
      node2.push('</em>')
      nodes.push(node2.join(''))

      position = end

    finalText = text.substring(position)
    if finalText.length
      nodes.push(finalText)

    nodes = nodes.join('').trim()

    return nodes