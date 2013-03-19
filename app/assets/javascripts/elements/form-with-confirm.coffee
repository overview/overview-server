define [ 'jquery' ], ($) ->
  $ ->
    $('body').on 'submit', 'form[data-confirm]', (e) ->
      message = $(this).attr('data-confirm')
      if window.confirm(message)
        # Disable clicking again.
        #
        # This is a HACK. We should implement this more broadly, not as a
        # side-effect of data-confirm forms.
        $(this).find('input').prop('disabled', true)
      else
        e.preventDefault()
