define [ 'jquery', 'i18n' ], ($, i18n) ->
  t = i18n.namespaced('time_display')

  $.fn.time_display = ->
    @each(time_display)

  time_display = ->
    dateString = @getAttribute('datetime')
    date = new Date(dateString)
    format = @getAttribute('data-format')

    text = if format
      t(format, date)
    else
      date.toString()

    @childNodes[0].nodeValue = text

    if !@getAttribute('title')
      @setAttribute('title', date.toString())

    undefined
