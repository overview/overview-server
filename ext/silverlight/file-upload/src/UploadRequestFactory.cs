using System.Windows.Browser;

namespace OverviewProject.FileUpload {
  public class UploadRequestFactory {
    public UploadRequestFactory() {}

    [ScriptableMember]
    public UploadRequest CreateUploadRequest(string url) {
      return new UploadRequest(url);
    }
  }
}
