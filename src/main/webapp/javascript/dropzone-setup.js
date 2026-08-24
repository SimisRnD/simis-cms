// Initialize Dropzone with consistent options and behavior
// acceptedFiles is optional -- when omitted, Dropzone's own default (accept anything) applies, so
// existing callers that pass only two arguments are unaffected. The image library passes 'image/*'
// so a non-image is rejected in the browser instead of after a round trip.
function initializeDropzone(containerId, maxFilesize, acceptedFiles) {
  Dropzone.options[containerId] = {
    autoProcessQueue: false,
    parallelUploads: 2,
    maxFilesize: maxFilesize,
    acceptedFiles: acceptedFiles,
    clickable: '#dz-browse',
    dictDefaultMessage: 'Drag and drop files here (max ' + maxFilesize + ' MB)<br/><br/>or use the Browse button below',
    init: function() {
      var submitButton = document.querySelector("#submit-all");
      var errorRegion  = document.querySelector("#upload-errors");
      var statusRegion = document.querySelector("#upload-status");
      var errorCount   = 0;
      var successCount = 0;
      var noticeCount  = 0;
      var myDropzone = this;

      // The region stays in the DOM (empty) so its aria-live announces reliably, but it must not
      // paint as an alert while it holds nothing -- with the callout/alert classes hard-coded in
      // the markup it rendered an empty red box under the drop target on every page load. Add the
      // styling only while a message is showing.
      function addUploadMessage(text) {
        var msg = document.createElement('p');
        msg.textContent = text;
        errorRegion.appendChild(msg);
        errorRegion.classList.add('callout', 'alert');
      }

      function clearUploadMessages() {
        errorRegion.innerHTML = '';
        errorRegion.classList.remove('callout', 'alert');
      }

      submitButton.addEventListener("click", function() {
        errorCount = 0;
        successCount = 0;
        noticeCount = 0;
        clearUploadMessages();
        statusRegion.textContent = '';
        myDropzone.processQueue();
      });

      this.on("addedfile", function() {
        submitButton.disabled = false;
      });

      this.on("success", function(file, response) {
        // The widget POST behind this dropzone always answers the XHR with HTTP 200 -- even a
        // server-rejected file (disallowed extension, oversized, etc.) -- so Dropzone's own
        // status-code-based success/error split can't tell them apart here. When the response is
        // JSON (it always is on this endpoint) and carries the widget's {"error": "..."} shape,
        // treat it as a failure instead of a success so the admin doesn't see "uploaded" for a
        // file that was actually rejected.
        if (response && typeof response === 'object' && response.error) {
          errorCount++;
          if (file.previewElement) {
            file.previewElement.classList.remove('dz-success');
            file.previewElement.classList.add('dz-error');
          }
          addUploadMessage(file.name + ': ' + response.error);
        } else {
          successCount++;
          // The file itself uploaded, but something optional alongside it did not (issue #1197: the
          // "also add to the Image Library" option). Not a failed upload, so it does not raise
          // errorCount -- but it must not be swallowed either, since a silently-skipped step is
          // exactly what made that issue so hard to diagnose from the UI.
          if (response && typeof response === 'object' && response.libraryError) {
            noticeCount++;
            addUploadMessage(file.name + ': ' + response.libraryError);
          }
        }
        myDropzone.processQueue();
      });

      this.on("error", function(file, message) {
        errorCount++;
        addUploadMessage(file.name + ': ' + (typeof message === 'string' ? message : (message.error || 'Upload failed')));
      });

      this.on("queuecomplete", function() {
        if (errorCount === 0 && successCount > 0) {
          if (noticeCount > 0) {
            // Don't auto-reload past a notice the admin hasn't read yet
            statusRegion.textContent = successCount + (successCount === 1 ? ' file' : ' files')
              + ' uploaded. Reload the page to see them.';
            return;
          }
          statusRegion.textContent = successCount + (successCount === 1 ? ' file' : ' files') + ' uploaded. Refreshing…';
          setTimeout(function() { window.location.reload(); }, 1200);
        }
      });

      document.querySelector("#clear-dropzone").addEventListener("click", function() {
        myDropzone.removeAllFiles(true);
        errorCount = 0;
        successCount = 0;
        noticeCount = 0;
        clearUploadMessages();
        statusRegion.textContent = '';
        submitButton.disabled = true;
      });
    }
  };
}
