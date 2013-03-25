define [ 'jquery' ], ($) ->
  $ ->
    $(document).on 'submit', 'form', (e) ->
      form = e.currentTarget

      # Defer until every other callback has run
      window.setTimeout(->
        # We deferred, so now we know if the form was submitted. If it wasn't,
        # don't provide feedback.
        return if e.isDefaultPrevented()

        # Change every input/button with a data-feedback attribute to have its
        # text match that attribute.
        $(':submit[data-feedback]', form).each ->
          $input = $(this)
          feedback = $input.attr('data-feedback')
          if $input.is('input')
            $input.val(feedback) # <input>
          else
            $input.text(feedback) # <button>
          $input.addClass('feedback')
          $input.prop('disabled', true)
      , 0)
