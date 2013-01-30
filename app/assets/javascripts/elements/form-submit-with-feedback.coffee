$ ->
  $(document).on 'submit', 'form', (e) ->
    $(this).find(':submit[data-feedback]').each ->
      $input = $(this)
      feedback = $input.attr('data-feedback')
      if $input.is('input')
        $input.val(feedback) # <input>
      else
        $input.text(feedback) # <button>
      $input.addClass('feedback')
      $input.attr('disabled', true)
