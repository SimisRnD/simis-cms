/**
 * P4: Delta JSON to HTML Renderer
 * Must match server-side DeltaContentCommand.render() output exactly
 *
 * Handles Quill 2.x Delta format:
 * - Basic formatting: bold, italic, code, underline
 * - Blocks: paragraphs, headings, lists, blockquotes, code blocks
 * - Links
 *
 * CVE-2025-15056 mitigation: Excludes formula and video embeds
 * Allowlist approach: Only renders known-safe tags
 */

class DeltaRenderer {
  /**
   * Render Delta JSON to safe HTML string
   * @param {object} delta - Quill Delta object { ops: [...] }
   * @returns {string} HTML string
   */
  static render(delta) {
    if (!delta || !delta.ops) {
      return '<p></p>';
    }

    const ops = delta.ops;
    let html = '';
    let currentBlock = 'p';
    let currentBlockContent = [];
    let inList = false;
    let listType = 'ul';
    let listStack = []; // For nested lists

    for (let i = 0; i < ops.length; i++) {
      const op = ops[i];
      const insert = op.insert;
      const attributes = op.attributes || {};

      // Handle newlines (block breaks)
      if (typeof insert === 'string' && insert.includes('\n')) {
        const lines = insert.split('\n');

        for (let j = 0; j < lines.length; j++) {
          const line = lines[j];

          // Emit pending block
          if (j > 0) {
            html += this.emitBlock(currentBlock, currentBlockContent, listType);
            currentBlockContent = [];
            currentBlock = 'p';
            inList = false;
          }

          // Determine block type from attributes
          if (attributes.header) {
            currentBlock = `h${Math.min(attributes.header, 6)}`;
          } else if (attributes.list) {
            currentBlock = 'li';
            listType = attributes.list === 'ordered' ? 'ol' : 'ul';
            inList = true;
          } else if (attributes.blockquote) {
            currentBlock = 'blockquote';
          } else if (attributes['code-block']) {
            currentBlock = 'pre';
          } else {
            currentBlock = 'p';
          }

          // Add text content (if not empty line)
          if (line) {
            currentBlockContent.push({
              text: line,
              attributes: attributes
            });
          }
        }
      } else if (typeof insert === 'string') {
        // Regular text
        currentBlockContent.push({
          text: insert,
          attributes: attributes
        });
      }
      // NOTE: Ignore embeds (images, videos, formulas) per CVE-2025-15056
    }

    // Emit final block
    if (currentBlockContent.length > 0 || currentBlock !== 'p') {
      html += this.emitBlock(currentBlock, currentBlockContent, listType);
    }

    return html || '<p></p>';
  }

  /**
   * Emit a single block (p, h1-h6, blockquote, pre, li)
   * Applies inline formatting to content
   */
  static emitBlock(blockType, content, listType = 'ul') {
    if (blockType === 'li') {
      const tag = listType === 'ordered' ? 'ol' : 'ul';
      const items = content.map(c => `<li>${this.formatInline(c.text, c.attributes)}</li>`).join('');
      return `<${tag}>${items}</${tag}>`;
    }

    if (blockType === 'pre') {
      const text = content.map(c => c.text).join('');
      return `<pre><code>${this.escapeHtml(text)}</code></pre>`;
    }

    if (blockType === 'blockquote') {
      const text = content.map(c => this.formatInline(c.text, c.attributes)).join('');
      return `<blockquote>${text}</blockquote>`;
    }

    // p, h1-h6
    const text = content.map(c => this.formatInline(c.text, c.attributes)).join('');
    return `<${blockType}>${text}</${blockType}>`;
  }

  /**
   * Apply inline formatting (bold, italic, code, link, underline)
   */
  static formatInline(text, attributes = {}) {
    let formatted = this.escapeHtml(text);

    if (attributes.code) {
      formatted = `<code>${formatted}</code>`;
    }

    if (attributes.bold) {
      formatted = `<strong>${formatted}</strong>`;
    }

    if (attributes.italic) {
      formatted = `<em>${formatted}</em>`;
    }

    if (attributes.underline) {
      formatted = `<u>${formatted}</u>`;
    }

    if (attributes.link) {
      const href = this.sanitizeUrl(attributes.link);
      formatted = `<a href="${href}">${formatted}</a>`;
    }

    return formatted;
  }

  /**
   * Escape HTML special characters
   */
  static escapeHtml(text) {
    const map = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    };
    return text.replace(/[&<>"']/g, char => map[char]);
  }

  /**
   * Sanitize URLs (prevent javascript: and data: URIs)
   */
  static sanitizeUrl(url) {
    if (!url) return '';

    const trimmed = url.trim().toLowerCase();

    // Reject javascript: and data: URIs
    if (trimmed.startsWith('javascript:') || trimmed.startsWith('data:')) {
      return '#';
    }

    // Allow http://, https://, mailto:, and relative paths
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://') ||
        trimmed.startsWith('mailto:') || trimmed.startsWith('/') || !trimmed.includes(':')) {
      return this.escapeHtml(url);
    }

    return '#';
  }

  /**
   * Test helper: compare client vs server output
   * Used in tests to verify parity
   */
  static testParity(delta, serverHtml) {
    const clientHtml = this.render(delta);
    return {
      match: clientHtml === serverHtml,
      client: clientHtml,
      server: serverHtml
    };
  }
}

// Export for use in browser
if (typeof module !== 'undefined' && module.exports) {
  module.exports = DeltaRenderer;
}
