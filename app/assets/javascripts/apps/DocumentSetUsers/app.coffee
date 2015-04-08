define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSetUser.index')

  class DocumentSetUserApp extends Backbone.View
    templates:
      main: _.template("""
        <p class="list-header"></p>
        <ul>
        </ul>
        <p class="explanation"><%- t('explanation') %></p>
        <form method="post" class="input-group add-viewer" action="">
          <input
            type="email"
            name="email"
            class="input-sm form-control"
            required="required"
            placeholder="<%- t('email_placeholder') %>"
            />
          <span class="input-group-btn">
            <button type="submit" class="btn btn-primary">
              <i class="icon overview-icon-plus"></i> <%- t('add') %>
            </button>
          </span>
        </form>
      """)

      public: _.template("""
        <div class="checkbox">
          <label>
            <input type="checkbox" name="public" value="true"/>
            <%- t('example_document_set.checkbox_label') %>
          </label>
        </div>
      """)

      li: _.template("""
        <li data-email="<%- email %>">
          <span class="email"><%- email %></span>
          <a class="remove" href="#"><%- t('remove') %></a>
        </li>
      """)

    events:
      'click a.remove': '_onClickRemove'
      'change input[name=public]': '_onChangePublic'
      'submit form.add-viewer': '_onSubmit'
      'input input[name=email]': '_onInputEmail'

    initialize: (options) ->
      @options = options
      @emails = options.emails.slice(0)
      @initialRender()
      @render()

    initialRender: ->
      html = @templates.main(t: t)
      if @options.isAdmin
        html += @templates.public(t: t)

      @$el.html(html)

      @ui =
        header: @$('.list-header')
        email: @$('input[name=email]')
        ul: @$('ul')
        public: @$('input[name=public]')

      liHtmls = @options.emails.map((email) => @templates.li(email: email, t: t))
      @ui.ul.html(liHtmls.join(''))

      @ui.public.prop('checked', @options.isPublic)

    render: ->
      @_renderHeader()
      parent?.postMessage('resize', '*')

    _renderHeader: ->
      @ui.header.text(t('list_header', @emails.length))

    _onClickRemove: (e) ->
      e.preventDefault()

      $li = $(e.target).closest('li')
      email = $li.attr('data-email')
      $li.remove()
      @emails.splice(@emails.indexOf(email), 1)
      @render()

      email = $li.attr('data-email')
      $.ajax
        type: 'DELETE'
        url: "/documentsets/#{@options.documentSetId}/users/#{encodeURIComponent(email)}"
        error: (xhr, textStatus, errorThrown) -> console.warn(errorThrown)

    _onChangePublic: ->
      isPublic = @ui.public.prop('checked')
      $.ajax
        type: 'PUT'
        url: "/documentsets/#{@options.documentSetId}"
        data: { public: isPublic }
        error: (xhr, textStatus, errorThrown) -> console.warn(errorThrown)

    _onSubmit: (e) ->
      e.preventDefault()

      email = @ui.email.val()
      @ui.email.val('')
      return if @emails.indexOf(email) != -1

      @emails.push(email)
      html = @templates.li(email: email, t: t)
      @ui.ul.append(html)

      @render()

      $.ajax
        type: 'PUT'
        url: "/documentsets/#{@options.documentSetId}/users/#{encodeURIComponent(email)}"
        error: (xhr, textStatus, errorThrown) -> console.warn(errorThrown)
