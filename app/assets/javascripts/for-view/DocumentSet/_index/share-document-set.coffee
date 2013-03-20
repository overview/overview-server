
loading_template = _.template("""<p>Loading sharing settings.""")
sharing_dialog_template =  _.template("""
    <div class="well" id="manage-user-roles">
      <ul class="unstyled collaborators">
        <% _.each(viewers, function(viewer) { %> <li class="email"><%- viewer.email %></li> <% }); %>
      </ul>
    </div>
    <form method="post" class="form-inline add-viewer" action="<%- create_url %>" >
      <input type="email" class="input-small" placeholder="Email" name="email" />
      <input type="hidden" name="role" value="Viewer" />
      <input type="submit" value="Add Viewer" />
    </form>""")

sort_by_email = (viewers) ->
  viewers.sort (a, b) ->
    return if (a.email >= b.email) then 1 else -1
  

show_sharing_settings = (url, createUrl) ->
  $modal = $('#sharing-options-modal')
  $modal.modal('show')
  $modal.find('.modal-body').html(loading_template)
  $.getJSON(url)
    .success((emails) ->
      $modal.find('.modal-body').html(sharing_dialog_template({ viewers: sort_by_email(emails.viewers), create_url: createUrl })))
    .error((a, b, c) ->
      console.log("error #{b}"))

add_email_to_list = (new_email, emails) ->
  emails.push new_email
  $collaborators = $('ul.collaborators')
  $collaborators.empty()

  for email in emails.sort()
    $email_item = $('<li/>').addClass('email').text(email)
    $collaborators.append($email_item)
    

$ ->
  $('#sharing-options-modal').one 'show', ->
    $('#sharing-options-modal').on 'submit', 'form.add-viewer', (e) ->
      e.preventDefault()
      data = $(e.currentTarget).serialize()
      url = $(e.currentTarget).attr('action')

      emails = $('li.email').map(-> $(this).text()).get()

      $email =  $('input[name=email]')
      new_email = $email.val()
      $email.val("")

      if (new_email not in emails)
        add_email_to_list(new_email, emails)
        $.ajax({
          url: url
          type: 'POST'
          data: data
          })
        
  $('div.document-sets').on 'click', 'a.show-sharing-settings', (e) ->
    e.preventDefault()
    url = $(e.currentTarget).attr('href')
    createUrl = $(e.currentTarget).attr('data-create-url')
    show_sharing_settings(url, createUrl)


