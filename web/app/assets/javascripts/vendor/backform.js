/*
  Backform
  http://github.com/amiliaapp/backform

  Copyright (c) 2014 Amilia Inc.
  Written by Martin Drapeau
  Licensed under the MIT @license
 */
(function(){

  // Backform namespace and global options
  Backform = {
    // HTML markup global class names. More can be added by individual controls
    // using _.extend. Look at RadioControl as an example.
    formClassName: "backform form-horizontal",
    groupClassName: "form-group",
    controlLabelClassName: "control-label col-sm-4",
    controlsClassName: "col-sm-8",
    controlClassName: "form-control",
    helpClassName: "help-block",
    errorClassName: "has-error",

    // Bootstrap 2.3 adapter
    bootstrap2: function() {
      _.extend(Backform, {
        groupClassName: "control-group",
        controlLabelClassName: "control-label",
        controlsClassName: "controls",
        controlClassName: "input-xlarge",
        helpClassName: "text-error",
        errorClassName: "error"
      });
      _.each(Backform, function(value, name) {
        if (_.isFunction(Backform[name]) &&
            _.isFunction(Backform[name].prototype["bootstrap2"]))
          Backform[name].prototype["bootstrap2"]();
      });
    },
    // https://github.com/wyuenho/backgrid/blob/master/lib/backgrid.js
    resolveNameToClass: function (name, suffix) {
      if (_.isString(name)) {
        var key = _.map(name.split('-'), function (e) {
          return e.slice(0, 1).toUpperCase() + e.slice(1);
        }).join('') + suffix;
        var klass = Backform[key];
        if (_.isUndefined(klass)) {
          throw new ReferenceError("Class '" + key + "' not found");
        }
        return klass;
      }
      return name;
    }
  };

  // Backform Form view
  // A collection of field models.
  var Form = Backform.Form = Backbone.View.extend({
    fields: undefined,
    errorModel: undefined,
    tagName: "form",
    className: function() {
      return Backform.formClassName;
    },
    initialize: function(options) {
      if (!(options.fields instanceof Backbone.Collection))
        options.fields = new Fields(options.fields || this.fields);
      this.fields = options.fields;
      this.model.errorModel = options.errorModel || this.model.errorModel || new Backbone.Model();
    },
    render: function() {
      this.$el.empty();

      var form = this,
          $form = this.$el,
          model = this.model;

      this.fields.each(function(field) {
        var control = new (field.get("control"))({
          field: field,
          model: model
        });
        $form.append(control.render().$el);
      });

      return this;
    }
  });

  // Field model and collection
  // A field maps a model attriute to a control for rendering and capturing user input
  var Field = Backform.Field = Backbone.Model.extend({
    defaults: {
      name: "", // Name of the model attribute
      nested: undefined, // Optional. If model attribute is an object, nested attribute to display (and update)
      label: "",
      placeholder: "",
      disabled: false,
      required: false,
      value: undefined, // Optional. Default value when model is empty.
      control: undefined // Control name or class
    },
    initialize: function() {
      var control = Backform.resolveNameToClass(this.get("control"), "Control");
      this.set({control: control}, {silent: true});
    }
  });

  var Fields = Backform.Fields = Backbone.Collection.extend({
    model: Field
  });


  // Base Control class
  var Control = Backform.Control = Backbone.View.extend({
    defaults: {}, // Additional field defaults
    className: function() {
      return Backform.groupClassName;
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <span class="<%=Backform.controlClassName%> uneditable-input"><%=value%></span>',
      '</div>'
    ].join("\n")),
    initialize: function(options) {
      this.field = options.field; // Back-reference to the field

      var name = this.field.get("name");
      this.listenTo(this.model, "change:" + name, this.render);
      if (this.model.errorModel instanceof Backbone.Model)
        this.listenTo(this.model.errorModel, "change:" + name, this.updateInvalid);
    },
    getValueFromDOM: function() {
      return this.$el.find(".uneditable-input").text();
    },
    onChange: function(e) {
      var model = this.model,
          $el = $(e.target),
          name = this.field.get("name"),
          nested = this.field.get("nested"),
          value = this.getValueFromDOM(),
          changes = {};

      if (this.model.errorModel instanceof Backbone.Model)
        this.model.errorModel.unset(name);

      if (_.isEmpty(nested)) {
        changes[name] = value;
      } else {
        changes[name] = _.clone(model.get(name)) || {};
        this.keyPathSetter(changes[name], nested, value);
      }
      model.set(changes, {silent: true}); // Make sure that change event is fired for nested objects
      model.trigger('change');
    },
    render: function() {
      var field = _.defaults(this.field.toJSON(), this.defaults),
          attributes = this.model.toJSON(),
          value = field.nested ? this.keyPathAccessor(attributes[field.name], field.nested) : attributes[field.name],
          data = _.extend(field, {value: value, attributes: attributes});
      this.$el.html(this.template(data));
      this.updateInvalid();
      return this;
    },
    clearInvalid: function() {
      this.$el.removeClass(Backform.errorClassName)
        .find("."+Backform.helpClassName+".error").remove();
      return this;
    },
    updateInvalid: function() {
      var errorModel = this.model.errorModel;
      if (!(errorModel instanceof Backbone.Model)) return this;

      this.clearInvalid();

      var name = this.field.get("name"),
          nested = this.field.get("nested"),
          error = errorModel.get(this.field.get("name"));
      if (_.isEmpty(error)) return;

      if (nested && _.isObject(error)) error = this.keyPathAccessor(error, nested);
      if (_.isEmpty(error)) return;

      this.$el.addClass(Backform.errorClassName);
      this.$el.find("."+Backform.controlsClassName)
        .append('<span class="'+Backform.helpClassName+' error">' + (_.isArray(error) ? error.join(", ") : error) + '</span>');

      return this;
    },
    keyPathAccessor: function(obj, path) {
      var res = obj;
      path = path.split('.');
      for (var i=0; i < path.length; i++) {
        if (res[path[i]]) res=res[path[i]];
      }
      return _.isObject(res) && !_.isArray(res) ? null : res;
    },
    keyPathSetter: function(obj, path, value) {
      path = path.split('.');
      while (path.length > 1) {
        obj = obj[path.shift()];
      }
      return obj[path.shift()] = value;
    }
  });

  // Built-in controls

  var UneditableInputControl = Backform.UneditableInputControl = Control;

  var SpacerControl = Backform.SpacerControl = Control.extend({
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>">&nbsp;</label>',
      '<div class="<%=Backform.controlsClassName%>"></div>'
    ].join("\n"))
  });

  var TextareaControl = Backform.TextareaControl = Control.extend({
    defaults: {},
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <textarea class="<%=Backform.controlClassName%>" name="<%=name%>" data-nested="<%=nested%>" placeholder="<%-placeholder%>" <%=disabled ? "disabled" : ""%> <%=required ? "required" : ""%>><%-value%></textarea>',
      '</div>',
    ].join("\n")),
    events: {
      "change textarea": "onChange",
      "focus textarea": "clearInvalid"
    },
    getValueFromDOM: function() {
      return this.$el.find("textarea").val();
    }
  });

  var SelectControl = Backform.SelectControl = Control.extend({
    defaults: {
      options: [] // List of options as [{label:<label>, value:<value>}, ...]
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <select class="<%=Backform.controlClassName%>" name="<%=name%>" data-nested="<%=nested%>" value="<%-JSON.stringify(value)%>" <%=disabled ? "disabled" : ""%> <%=required ? "required" : ""%> >',
      '    <% for (var i=0; i < options.length; i++) { %>',
      '      <% var option = options[i]; %>',
      '      <option value="<%-JSON.stringify(option.value)%>" <%=option.value == value ? "selected=\'selected\'" : ""%>><%-option.label%></option>',
      '    <% } %>',
      '  </select>',
      '</div>',
    ].join("\n")),
    events: {
      "change select": "onChange",
      "focus select": "clearInvalid"
    },
    getValueFromDOM: function() {
      return JSON.parse(this.$el.find("select").val());
    }
  });

  var InputControl = Backform.InputControl = Control.extend({
    defaults: {
      type: "text"
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <input type="<%=type%>" class="<%=Backform.controlClassName%>" name="<%=name%>" data-nested="<%=nested%>" value="<%-value%>" placeholder="<%-placeholder%>" <%=disabled ? "disabled" : ""%> <%=required ? "required" : ""%> />',
      '</div>',
    ].join("\n")),
    events: {
      "change input": "onChange",
      "focus input": "clearInvalid"
    },
    getValueFromDOM: function() {
      return this.$el.find("input").val();
    }
  });

  var BooleanControl = Backform.BooleanControl = InputControl.extend({
    defaults: {
      type: "checkbox"
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>">&nbsp;</label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <div class="checkbox">',
      '    <label>',
      '      <input type="<%=type%>" name="<%=name%>" data-nested="<%=nested%>" <%=value ? "checked=\'checked\'" : ""%> <%=disabled ? "disabled" : ""%> <%=required ? "required" : ""%> /> <%-label%>',
      '    </label>',
      '  </div>',
      '</div>',
    ].join("\n")),
    getValueFromDOM: function() {
      return this.$el.find("input").is(":checked");
    }
  });

  var CheckboxControl = Backform.CheckboxControl = BooleanControl;

  var RadioControl = Backform.RadioControl = InputControl.extend({
    defaults: {
      type: "radio",
      options: []
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%> <%=Backform.radioControlsClassName%>">',
      '  <% for (var i=0; i < options.length; i++) { %>',
      '    <% var option = options[i]; %>',
      '    <label class="<%=Backform.radioLabelClassName%>">',
      '      <input type="<%=type%>" name="<%=name%>" data-nested="<%=nested%>" value="<%-JSON.stringify(option.value)%>" <%=value == option.value ? "checked=\'checked\'" : ""%> <%=disabled ? "disabled" : ""%> <%=required ? "required" : ""%> /> <%-option.label%>',
      '    </label>',
      '  <% } %>',
      '</div>',
    ].join("\n")),
    getValueFromDOM: function() {
      return JSON.parse(this.$el.find("input:checked").val());
    },
    bootstrap2: function() {
      Backform.radioControlsClassName = "controls";
      Backform.radioLabelClassName = "radio inline";
    }
  });
  _.extend(Backform, {
    radioControlsClassName: "checkbox",
    radioLabelClassname: "checkbox-inline"
  });

  // Requires the Bootstrap Datepicker to work.
  var DatepickerControl = Backform.DatepickerControl = InputControl.extend({
    defaults: {
      type: "text",
      options: {}
    },
    events: {
      "changeDate input": "onChange",
      "focus input": "clearInvalid"
    },
    render: function() {
      InputControl.prototype.render.apply(this, arguments);
      this.$el.find("input").datepicker(this.field.get("options"));
      return this;
    }
  });

  var ButtonControl = Backform.ButtonControl = Control.extend({
    defaults: {
      type: "submit",
      status: undefined, // error or success
      message: undefined
    },
    template: _.template([
      '<label class="<%=Backform.controlLabelClassName%>"><%-label%></label>',
      '<div class="<%=Backform.controlsClassName%>">',
      '  <button type="<%=type%>" class="btn btn-default" <%=disabled ? "disabled" : ""%> >Submit</button>',
      '  <% var cls = ""; if (status == "error") cls = Backform.buttonStatusErrorClassName; if (status == "success") cls = Backform.buttonStatusSuccessClassname; %>',
      '  <span class="status <%=cls%>"><%=message%></span>',
      '</div>'
    ].join("\n")),
    initialize: function() {
      Control.prototype.initialize.apply(this, arguments);
      this.listenTo(this.field, "change:status", this.render);
      this.listenTo(this.field, "change:message", this.render);
    },
    bootstrap2: function() {
      Backform.buttonStatusErrorClassName = "text-error";
      Backform.buttonStatusSuccessClassname = "text-success";
    }
  });
  _.extend(Backform, {
    buttonStatusErrorClassName: "text-danger",
    buttonStatusSuccessClassname: "text-success"
  });

}).call(this);
