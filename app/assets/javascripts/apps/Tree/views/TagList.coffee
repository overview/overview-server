define [ 'jquery', 'underscore', 'backbone', 'i18n', 'spectrum' ], ($, _, Backbone, i18n) ->
  t = (key, args...) -> i18n("views.DocumentSet.show.tag_list.#{key}", args...)

  addSpectrum = ($els) ->
    $els.filter(':not(.spectrum)').each ->
      $input = $(this)
      $input
        .addClass('spectrum')
        .spectrum({
          preferredFormat: 'hex6'
          showButtons: false
          move: (color) -> $input.spectrum('set', color.toHexString()).change() # Issue #168
        })

  removeSpectrum = ($els) ->
    $els.filter('.spectrum').spectrum('destroy')

  updateSpectrum = ($els) ->
    $els.filter('.spectrum').each ->
      $(this).spectrum('set', $(this).val())

  # Represents a list of tags
  #
  # Usage:
  #   tags = new Tags([], { tagStore: tagStore })
  #   view = new TagList({ collection: tags })
  #   $('body').append(view.el)
  #
  # The view will emit the following events:
  #
  # * add: user requested to add a new Tag (plain-old-data object with name/color)
  # * remove: user requested to remove a Tag
  # * update(tag, attrs): user requested to update a Tag (Backbone object) with
  #   new attributes.
  # * export: user requested to export the list of tags to CSV
  Backbone.View.extend
    className: 'vertical-tag-list'

    events:
      'click a.remove': '_onClickRemove'
      'submit li.new form': '_onSubmitNewTag'
      'submit li.existing form': '_onSubmitExistingTag'
      'change li.existing form': '_onChange'

    tagTemplate: _.template("""
      <li class="existing" data-cid="<%= tag.cid %>">
        <form method="post" action="#" class="form-horizontal">
          <%= window.csrfTokenHtml %>
          <input type="hidden" name="id" value="<%- tag.id || '' %>" />
          <input type="color" name="color" value="<%- tag.get('color') %>"
          /><input type="text" name="name" value="<%- tag.get('name') %>" />
          <div class="count"><%- t('n_documents', tag.get('size') || 0) %></div>
        </form>
        <a class="remove" href="#"><%- t('remove') %></a>
      </li>
    """)

    template: _.template("""
      <p class="preamble">
        <%- t('preamble') %>
        <% if (tags.length && exportUrl) { %>
          <a class="export" href="<%- exportUrl %>"><%- t('export') %></a>
        <% } %>
      </p>
      <ul class="unstyled">
        <%= tags.map(renderTag).join('') %>
        <li class="new">
          <form method="post" action="#" class="form-horizontal">
            <input type="text" name="name" required="required" placeholder="<%- t('tag_name.placeholder') %>" />
            <button type="submit" class="btn"><%- t('submit') %></button>
          </form>
        </li>
      </ul>
    """)

    initialize: ->
      throw 'must set options.collection' if !@options.collection

      @listenTo(@collection, 'add', (tag) => @_addTag(tag))
      @listenTo(@collection, 'remove', (tag) => @_removeTag(tag))
      @listenTo(@collection, 'change', (tag, options) => @_changeTag(tag, options))
      @listenTo(@collection, 'reset', => @render())
      @render()

    render: ->
      removeSpectrum(@$('input[type=color]'))
      html = @template({
        t: t
        tags: @collection
        exportUrl: @options.exportUrl
        renderTag: (tag) => @tagTemplate({ tag: tag, t: t })
      })
      @$el.html(html)
      addSpectrum(@$('input[type=color]'))
      this

    remove: ->
      removeSpectrum(@$('input[type=color]'))
      Backbone.View.prototype.remove.call(this)

    _$liForTag: (tag) ->
      @$("[data-cid=#{tag.cid}]")

    _addTag: (tag) ->
      html = @tagTemplate({
        t: t
        tag: tag
      })

      index = @collection.indexOf(tag)
      @$("ul>li:eq(#{index})").before(html)
      addSpectrum(@$('input[type=color]'))

    _removeTag: (tag) ->
      $li = @_$liForTag(tag)
      removeSpectrum($li.find('input[type=color]'))
      $li.remove()

    _changeTag: (tag, options) ->
      $li = @_$liForTag(tag)
      $li.find('input[name=id]').val(tag.id)
      $li.find('.count').text(t('n_documents', tag.get('size') || 0))

      if !options? || !options.interacting
        $li.find('input[name=name]').val(tag.get('name'))
        $color = $li.find('input[name=color]')
        $color.val(tag.get('color'))
        updateSpectrum($color)

    _onClickRemove: (e) ->
      e.preventDefault()
      cid = $(e.target).closest('[data-cid]').attr('data-cid')

      tag = @collection.get(cid)
      count = tag.get('size') || 0
      message = t('remove.confirm', tag.get('name'), count)

      @trigger('remove', tag) if confirm(message)

    _onSubmitNewTag: (e) ->
      e.preventDefault()
      $form = $(e.currentTarget).closest('form')

      tag = {
        name: $form.find('input[name=name]').val()
        color: $form.find('input[name=color]').val()
      }

      @trigger('add', tag)
      $form[0].reset()

    _onSubmitExistingTag: (e) ->
      e.preventDefault()
      $form = $(e.currentTarget).closest('form')

      $li = $(e.currentTarget).closest('[data-cid]')
      cid = $li.attr('data-cid')
      tag = @collection.get(cid)

      attrs = {
        name: $form.find('input[name=name]').val()
        color: $form.find('input[name=color]').val()
      }

      @trigger('update', tag, attrs)

    _onChange: (e) ->
      $form = $(e.currentTarget).closest('form')
      $form.submit()
