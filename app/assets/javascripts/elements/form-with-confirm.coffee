define [ 'jquery' ], ($) ->
  $ ->
    $('body').on 'submit', 'form[data-confirm]', (e) ->
      form = e.currentTarget
      message = $(this).attr('data-confirm')
      if window.confirm(message)
        # Disable clicking again.
        #
        # This is a HACK. We should implement this more broadly, not as a
        # side-effect of data-confirm forms.
        window.setTimeout((-> $(form).on('submit', (e2) -> e2.preventDefault(); false)), 0)
      else
        e.preventDefault()
