using System;
using System.Collections.Generic;
using System.Linq;
using System.IO;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  using Extensions;

#region WebRequest helpers
  namespace Extensions {
    internal static class WebRequestExtensions {
      public static Task<Stream> GetRequestStreamAsync(this WebRequest webRequest) {
        return Task.Factory.FromAsync<Stream>(
            webRequest.BeginGetRequestStream,
            webRequest.EndGetRequestStream,
            null);
      }

      public static Task<WebResponse> GetResponseAsync(this WebRequest webRequest) {
        return Task.Factory.FromAsync<WebResponse>(
            webRequest.BeginGetResponse,
            webRequest.EndGetResponse,
            null);
      }
    }

    internal static class HttpWebResponseExtensions {
      public static WebHeaderCollection GetRealOrFakeHeaders(this HttpWebResponse response) {
        WebHeaderCollection headers;
        
        if (response.SupportsHeaders) {
          headers = response.Headers;
        } else {
          headers = new WebHeaderCollection();

          headers["Content-Length"] = "" + response.ContentLength;
          headers["Content-Type"] = "" + response.ContentType;
        }

        return headers;
      }
    }

    internal static class WebHeaderCollectionExtensions {
      public static string AsHttpString(this WebHeaderCollection headers) {
        List<string> lines = new List<string>();

        foreach (string key in headers.AllKeys) {
          lines.Add(key + ": " + headers[key]);
        }

        return String.Join("\r\n", lines);
      }
    }
  }
#endregion

  public class UploadRequest {
    private HttpWebRequest request;
    private Blob blob;
    private SynchronizationContext syncContext;
    private bool completed = false; // ensures we don't call Complete() twice

    [ScriptableMember]
    public WebHeaderCollection Headers { get { return request.Headers; } }

    /** Allow JavaScript to change headers */
    [ScriptableMember]
    public void AddRequestHeader(string key, string val) {
      switch (key) {
        case "Accept":
          request.Accept = val;
          break;
        case "Content-Length":
          break; // ignore
        case "Content-Type":
          request.ContentType = val;
          break;
        case "Content-Range":
          Headers["X-MSHACK-Content-Range"] = val;
          break;
        default:
          Headers[key] = val;
          break;
      }
    }

#region Event arguments
    [ScriptableType]
    public class CompletedEventArgs : EventArgs {
      public int StatusInt { get; set; }
      public string StatusText { get; set; }
      public string AllHeaders { get; set; }
      public string Body { get; set; }

      public CompletedEventArgs(HttpStatusCode status, WebHeaderCollection headers, string body) {
        StatusInt = (int) status;
        StatusText = status.ToString();
        AllHeaders = headers.AsHttpString();
        Body = body;
      }
    }

    [ScriptableType]
    public class UploadProgressEventArgs : EventArgs {
      public long Loaded { get; set; }
      public long Total { get; set; }

      public UploadProgressEventArgs(long loaded, long total) {
        Loaded = loaded;
        Total = total;
      }
    }
#endregion

#region Events
    [ScriptableMember] public event EventHandler<CompletedEventArgs> Completed;
    [ScriptableMember] public event EventHandler<UploadProgressEventArgs> UploadProgress;
#endregion

    public UploadRequest(string url) {
      this.request = WebRequest.Create(url) as HttpWebRequest;
      this.request.Method = "POST";
      this.request.ContentType = "application/octet-stream"; // default
    }

#region Send and Abort
    [ScriptableMember]
    public void Send(Blob blob) {
      this.syncContext = SynchronizationContext.Current;
      this.blob = blob;

      // We don't need to worry about CancellationToken: when we Abort() the
      // sending stream will close, an exception will be thrown and caught,
      // and we'll call OnFailed()
      SendAsync();
    }

    [ScriptableMember]
    public void Abort() {
      // an exception will be raised in whichever async method is running.
      request.Abort();
    }

    private async void SendAsync() {
      try {
        this.request.ContentLength = this.blob.Size;

        using (Stream sendStream = await request.GetRequestStreamAsync())
        using (Stream blobStream = blob.OpenStream()) {
          await CopyStreamToStreamAsync(blobStream, sendStream);
        }

        using (HttpWebResponse response = await request.GetResponseAsync() as HttpWebResponse) {
          using (Stream responseStream = response.GetResponseStream())
          using (StreamReader reader = new StreamReader(responseStream)) {
            string body = await reader.ReadToEndAsync();
            HttpStatusCode status = response.StatusCode;
            WebHeaderCollection headers = response.GetRealOrFakeHeaders();

            var eventArgs = new CompletedEventArgs(status, headers, body);
            OnCompleted(eventArgs);
          }
        }
      } catch (Exception e) {
        OnFailed(e);
        throw;
      }
    }

    // Like Stream.copyToAsync(), but with progress and no "async" keyword
    private async Task CopyStreamToStreamAsync(Stream source, Stream destination) {
      try {
        var buffer = new byte[0x1000];
        long totalBytes = 0;
        int bytesRead;

        while (0 != (bytesRead = await source.ReadAsync(buffer, 0, buffer.Length))) {
          await destination.WriteAsync(buffer, 0, bytesRead);

          totalBytes += bytesRead;
          OnUploadProgress(new UploadProgressEventArgs(totalBytes, blob.Size));
        }
      } catch (Exception e) {
        OnFailed(e);
        throw;
      }
    }
#endregion

#region Event Raisers
    protected void OnCompleted(CompletedEventArgs args) {
      syncContext.Post(delegate(Object _) {
        if (completed) return;

        completed = true;

        if (Completed != null) Completed(this, args);
      }, null);
    }

    protected void OnFailed(Exception e) {
      this.OnCompleted(new CompletedEventArgs(HttpStatusCode.InternalServerError, new WebHeaderCollection(), "" + e));
    }

    protected void OnUploadProgress(UploadProgressEventArgs args) {
      syncContext.Post(delegate(Object _) {
        if (completed) return;

        if (UploadProgress != null) UploadProgress(this, args);
      }, null);
    }
#endregion
  }
}
