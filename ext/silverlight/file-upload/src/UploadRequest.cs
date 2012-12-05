using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Browser;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
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
    private Uri uri;
    private HttpWebRequest request; // for Abort()
    private SynchronizationContext syncContext;
    private bool completed = false; // ensures we don't call Complete() twice
    private string headerAccept = null;
    private string headerContentType = null;
    private string headerContentRange = null;
    private Dictionary<string,string> headers = new Dictionary<string,string>();

    /** Allow JavaScript to change headers */
    [ScriptableMember]
    public void AddRequestHeader(string key, string val) {
      switch (key) {
        case "Accept":
          headerAccept = val;
          break;
        case "Content-Length":
          break; // ignore
        case "Content-Type":
          headerContentType = val;
          break;
        case "Content-Range":
          headerContentRange = val;
          break;
        default:
          headers[key] = val;
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

    private class ChunkStatus {
      public int BytesSent { get; set; }
      public CompletedEventArgs Args { get; set; }

      public ChunkStatus(int bytesSent, CompletedEventArgs args) {
        BytesSent = bytesSent;
        Args = args;
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
      this.uri = new Uri(url);
    }

#region Send and Abort
    [ScriptableMember]
    public void Send(Blob blob) {
      this.syncContext = SynchronizationContext.Current;

      // We don't need to worry about CancellationToken: when we Abort() the
      // sending stream will close, an exception will be thrown and caught,
      // and we'll call OnFailed()
      SendAsync(blob);
    }

    [ScriptableMember]
    public void Abort() {
      // an exception will be raised in whichever async method is running.
      request.Abort();
    }

    private async void SendAsync(Blob blob) {
      // We can't set AllowWriteStreamBuffering = false on an HttpWebRequest
      // without disabling HTTPOnly cookies. Our workaround: send a bunch of
      // chunks.
      long bytesUploaded = 0;
      long bytesTotal = blob.Size;
      int chunkSize = 512*1024; // 512kb. Arbitrary.

      long bytesContentRangeStart = 0;
      if (headerContentRange != null) {
        int hyphen = headerContentRange.IndexOf('-');
        if (hyphen > 0) {
          string contentRangeStart = headerContentRange.Substring(0, hyphen);
          // Throw an exception if it isn't an int
          bytesContentRangeStart = Int64.Parse(contentRangeStart);
        }
      }

      try {
        using (Stream blobStream = blob.OpenStream()) {
          ChunkStatus status = null;

          while (bytesUploaded < bytesTotal) {
            status = await SendNextChunkAsync(blobStream, chunkSize, bytesUploaded + bytesContentRangeStart, bytesTotal);
            bytesUploaded += status.BytesSent;
            OnUploadProgress(new UploadProgressEventArgs(bytesUploaded, bytesTotal));
          }

          if (status != null) OnCompleted(status.Args);
        }
      } catch (Exception e) {
        OnFailed(e);
      }
    }

    private async Task<ChunkStatus> SendNextChunkAsync(Stream blobStream, int chunkSize, long chunkStart, long bytesTotal) {
      // Errors will be caught by the caller.
      var buffer = new byte[chunkSize];
      int bytesTransferred = await blobStream.ReadAsync(buffer, 0, chunkSize);

      this.request = WebRequestCreator.BrowserHttp.Create(uri) as HttpWebRequest;
      request.Method = "POST";
      request.ContentType = "application/octet-stream"; // default
      request.ContentLength = bytesTransferred;
      // Set headers
      foreach (var entry in this.headers) {
        request.Headers[entry.Key] = entry.Value;
      }
      if (headerAccept != null) request.Accept = headerAccept;
      if (headerContentType != null) request.ContentType = headerContentType;
      // Set content-range, specially
      if (headerContentRange != null) {
        request.Headers["X-MSHACK-Content-Range"] = "" + chunkStart + "-" + (chunkStart + bytesTransferred - 1) + "/" + bytesTotal;
      }

      using (Stream sendStream = await request.GetRequestStreamAsync()) {
        await sendStream.WriteAsync(buffer, 0, bytesTransferred);
      }

      HttpStatusCode status;
      WebHeaderCollection headers;
      string body;

      using (HttpWebResponse response = await request.GetResponseAsync() as HttpWebResponse)
      using (Stream responseStream = response.GetResponseStream())
      using (StreamReader reader = new StreamReader(responseStream)) {
        status = response.StatusCode;
        headers = response.GetRealOrFakeHeaders();
        body = await reader.ReadToEndAsync();
      }

      CompletedEventArgs args = new CompletedEventArgs(status, headers, body);
      return new ChunkStatus(bytesTransferred, args);
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
      MessageBox.Show(e.ToString());
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
