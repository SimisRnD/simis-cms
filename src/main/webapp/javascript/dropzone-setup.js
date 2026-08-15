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
      var myDropzone = this;

      submitButton.addEventListener("click", function() {
        errorCount = 0;
        successCount = 0;
        errorRegion.innerHTML = '';
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
          var msg = document.createElement('p');
          msg.textContent = file.name + ': ' + response.error;
          errorRegion.appendChild(msg);
        } else {
          successCount++;
        }
        myDropzone.processQueue();
      });

      this.on("error", function(file, message) {
        errorCount++;
        var msg = document.createElement('p');
        msg.textContent = file.name + ': ' + (typeof message === 'string' ? message : (message.error || 'Upload failed'));
        errorRegion.appendChild(msg);
      });

      this.on("queuecomplete", function() {
        if (errorCount === 0 && successCount > 0) {
          statusRegion.textContent = successCount + (successCount === 1 ? ' file' : ' files') + ' uploaded. Refreshing…';
          setTimeout(function() { window.location.reload(); }, 1200);
        }
      });

      document.querySelector("#clear-dropzone").addEventListener("click", function() {
        myDropzone.removeAllFiles(true);
        errorCount = 0;
        successCount = 0;
        errorRegion.innerHTML = '';
        statusRegion.textContent = '';
        submitButton.disabled = true;
      });
    }
  };
}
