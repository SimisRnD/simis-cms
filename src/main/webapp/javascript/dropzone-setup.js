// Initialize Dropzone with consistent options and behavior
function initializeDropzone(containerId, maxFilesize) {
  Dropzone.options[containerId] = {
    autoProcessQueue: false,
    parallelUploads: 2,
    maxFilesize: maxFilesize,
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

      this.on("success", function() {
        successCount++;
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
