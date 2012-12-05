/*
 * Mimics an XMLHttpRequest, kind of.
 *
 * usage:
 *
 *     var file = undefined;
 *     var blob = undefined;
 *     var api = window.SilverlightFileUploadPlugin(slPlugin, function(selected_file) {
 *       file = selected_file;
 *       blob = file.slice(100, 1400);
 *     });
 *
 *     // and then call this code as a consequence...
 *
 *     var progressCallback = function(obj) {
 *       console.log("Progress: " + obj.length + " of " + obj.total);
 *     };
 *     var xhr = new XMLHttpUploadRequest(silverlightPlugin, progressCallback);
 *     xhr.open(type, url, async); // type must be 'POST'; async must be true
 *     xhr.setRequestHeader('Content-Range', '100-1400/2311');
 *     xhr.send(blob);
 *
 * For simplicity's sake, some assumptions are made. xhr.readyState leaps from
 * 0 (UNSENT) to 1 (OPENED) to 4 (DONE), without passing through any in-between
 * states.
 *
 * In a nutshell, this interface is the minimum we need to:
 *
 * * Select a file using our Silverlight plugin;
 * * Use our Silverlight Blob, File and UploadRequest classes;
 * * Use jQuery's default (XMLHttpRequest) transport;
 * * Let clients listen to progress events.
 *
 * To listen to upload progress on a jQuery AJAX request, you need to create it
 * like this:
 *
 *     var file = undefined;
 *     var api = window.SilverlightFileUploadPlugin(slPlugin, function(selected_file) {
 *       // Method called when user clicks a file
 *       file = selected_file;
 *     });
 *
 *     var progressCallback = function(obj) {
 *       console.log("Progress: " + obj.length + " of " + obj.total);
 *     };
 *     var create_xhr = function() {
 *       return api.createXMLHttpUploadRequest(progressCallback);
 *     };
 *
 *     var jqXHR = $.ajax({
 *       type: 'POST',
 *       url: url,
 *       data: file,
 *       processData: false,
 *       xhr: create_xhr
 *     });
 */
(function() {
  var READY_STATE = {
    UNSENT: 0,
    OPENED: 1,
    HEADERS_RECEIVED: 2,
    LOADING: 3,
    DONE: 4
  };

  function url_to_absolute_url(url) {
    var a = document.createElement('a');
    a.href = url;
    return a.href;
  };

  // XMLHttpRequest lookalike
  function XMLHttpUploadRequest(silverlightPlugin, progressCallback) {
    this.uploadRequestFactory = function(url) {
      // We can't grab a handle to a Silverlight method, hence this wrapper factory
      return silverlightPlugin.Content.UploadRequestFactory.CreateUploadRequest(url);
    };
    this.progressCallback = progressCallback;
    this.onreadystatechange = function() {};
    this.headers = {};

    Object.defineProperty(this, 'status', {
      enumerable: true,
      get: function() { return this.response.StatusInt; }
    });
    Object.defineProperty(this, 'statusText', {
      enumerable: true,
      get: function() { return this.response.StatusText; }
    });
    Object.defineProperty(this, 'responseXML', {
      enumerable: true,
      get: function() { return this.response.Body; }
    });
    Object.defineProperty(this, 'responseText', {
      enumerable: true,
      get: function() { return this.response.Body; }
    });
  }

  XMLHttpUploadRequest.prototype.open = function(type, url, async) {
    if (type != 'POST') {
      throw 'type given was "' + type + '"; it must be "POST"';
    }

    if (!async) {
      throw 'async given was false; it must be true';
    }

    this.url = url_to_absolute_url(url);
  };

  XMLHttpUploadRequest.prototype.setRequestHeader = function(key, value) {
    this.headers[key] = value;
  };

  XMLHttpUploadRequest.prototype.overrideMimeType = function(mimeType) {
    // ignored
  };

  XMLHttpUploadRequest.prototype.getAllResponseHeaders = function() {
    return this.response.AllHeaders;
  };

  XMLHttpUploadRequest.prototype.send = function(blob) {
    this.uploadRequest = this.uploadRequestFactory(this.url);

    var _this = this;
    var setResponseAndReadyState = function(readyState, response) {
      _this.response = response;
      _this.readyState = readyState;
      if (_this.onreadystatechange) {
        _this.onreadystatechange();
      }
    };

    var on_complete = function(sender, args) {
      setResponseAndReadyState(READY_STATE.DONE, args);
    };

    var on_progress = function(sender, args) {
      if (_this.progressCallback) {
        _this.progressCallback({ loaded: args.Loaded, total: args.Total });
      }
    };

    this.uploadRequest.addEventListener('Completed', on_complete);
    this.uploadRequest.addEventListener('UploadProgress', on_progress);

    var key, value;
    for (key in this.headers) {
      value = this.headers[key];
      this.uploadRequest.AddRequestHeader(key, value);
    }

    this.uploadRequest.Send(blob._silverlightObject);
  };

  XMLHttpUploadRequest.prototype.abort = function() {
    this.uploadRequest.Abort();
  }

  // Blob API. http://www.w3.org/TR/FileAPI/#blob
  function Blob(silverlightObject) {
    this._silverlightObject = silverlightObject;

    Object.defineProperty(this, 'size', {
      enumerable: true,
      get: function() { return silverlightObject.Size; }
    });
  }

  Blob.prototype.slice = function(start, end /*, contentType=undefined*/) {
    return new Blob(this._silverlightObject.slice(start, end));
  };

  // File API. http://www.w3.org/TR/FileAPI/#file
  function File(silverlightObject) {
    this._silverlightObject = silverlightObject;

    var keys = { size: 'Size', name: 'Name', lastModifiedDate: 'LastModifiedDate' };
    var jsKey, slKey;
    var doDefineProperty = function(obj, jsKey, slKey) {
      Object.defineProperty(obj, jsKey, {
        enumerable: true,
        get: function() { return silverlightObject[slKey]; }
      });
    };
    for (jsKey in keys) {
      slKey = keys[jsKey];
      doDefineProperty(this, jsKey, slKey);
    }
  }

  File.prototype.slice = function(start, end /*, contentType=undefined*/) {
    return new Blob(this._silverlightObject.slice(start, end));
  };

  // FileReader API. http://www.w3.org/TR/FileAPI/#FileReader-interface
  function FileReader(silverlightPlugin) {
    var silverlightObject = this._silverlightObject =
      silverlightPlugin.Content.FileReaderFactory.CreateFileReader();

    Object.defineProperty(this, 'readyState', {
      enumerable: true,
      get: function() { return silverlightObject.ReadyState; }
    });
    Object.defineProperty(this, 'result', {
      enumerable: true,
      get: function() { return silverlightObject.Result; }
    });
    Object.defineProperty(this, 'error', {
      enumerable: true,
      get: function() {
        if (silverlightObject.Error) {
          return { name: silverlightObject.Error.Name };
        } else {
          return null;
        }
      }
    });

    this.onloadend = function() {};

    var _this = this;
    var silverlight_onloadend = function(sender, args) {
      _this.onloadend({
        lengthComputable: args.LengthComputable,
        length: args.Length,
        total: args.Total
      });
    };
    silverlightObject.addEventListener('OnLoadEnd', silverlight_onloadend);
  }

  FileReader.prototype.readAsText = function(blob, encoding) {
    this._silverlightObject.ReadAsText(blob._silverlightObject, encoding);
  }

  window.SilverlightFileUploadPlugin = function(silverlightPlugin, fileSelectedCallback) {
    var picker = silverlightPlugin.Content.FilePickerControl;
    picker.addEventListener('FileSelected', function(sender, args) {
      var file = new File(args.File); // wrap Silverlight object with JS one
      fileSelectedCallback(file);
    });

    return {
      createXMLHttpUploadRequest: function(callback) {
        return new XMLHttpUploadRequest(silverlightPlugin, callback);
      },
      createFileReader: function() {
        return new FileReader(silverlightPlugin);
      }
    };
  };
})();
