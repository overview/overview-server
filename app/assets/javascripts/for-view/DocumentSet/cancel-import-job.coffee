define [ 'jquery' ], ($) ->
  $ ->
    $(document).on 'click', 'button.delete-import-job', (ev) ->
      button = ev.currentTarget
      message = button.getAttribute('data-confirm')
      if window.confirm(message)
        url = button.getAttribute('data-url')
        $(button).replaceWith('<i class="icon icon-spinner icon-spin"/>')
        # Fire off the AJAX request and then do nothing; the next progress
        # check should pick up on the change.
        $.ajax
          type: 'DELETE'
          url: url
