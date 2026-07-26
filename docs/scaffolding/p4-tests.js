/**
 * P4: Unit Tests for Delta Renderer & Undo/Redo
 * Test cases to verify parity with server-side DeltaContentCommand.render()
 *
 * Run with: Jest, Mocha, or any JavaScript test framework
 */

describe('P4 Delta Renderer', () => {
  describe('Basic formatting', () => {
    test('renders bold text', () => {
      const delta = {
        ops: [
          { insert: 'Hello ', attributes: {} },
          { insert: 'bold', attributes: { bold: true } },
          { insert: ' world\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<strong>bold</strong>');
      expect(html).toMatch(/<p>Hello <strong>bold<\/strong> world<\/p>/);
    });

    test('renders italic text', () => {
      const delta = {
        ops: [
          { insert: 'This is ', attributes: {} },
          { insert: 'italic', attributes: { italic: true } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<em>italic</em>');
    });

    test('renders code text', () => {
      const delta = {
        ops: [
          { insert: 'Run ', attributes: {} },
          { insert: 'npm start', attributes: { code: true } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<code>npm start</code>');
    });

    test('combines multiple formats', () => {
      const delta = {
        ops: [
          { insert: 'bold and italic', attributes: { bold: true, italic: true } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<strong>');
      expect(html).toContain('<em>');
    });
  });

  describe('Block types', () => {
    test('renders headings', () => {
      const delta = {
        ops: [
          { insert: 'Heading 1', attributes: { header: 1 } },
          { insert: '\n', attributes: { header: 1 } },
          { insert: 'Heading 2', attributes: { header: 2 } },
          { insert: '\n', attributes: { header: 2 } }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<h1>Heading 1</h1>');
      expect(html).toContain('<h2>Heading 2</h2>');
    });

    test('renders unordered lists', () => {
      const delta = {
        ops: [
          { insert: 'Item 1', attributes: { list: 'bullet' } },
          { insert: '\n', attributes: { list: 'bullet' } },
          { insert: 'Item 2', attributes: { list: 'bullet' } },
          { insert: '\n', attributes: { list: 'bullet' } }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<ul>');
      expect(html).toContain('<li>Item 1</li>');
      expect(html).toContain('<li>Item 2</li>');
    });

    test('renders ordered lists', () => {
      const delta = {
        ops: [
          { insert: 'First', attributes: { list: 'ordered' } },
          { insert: '\n', attributes: { list: 'ordered' } },
          { insert: 'Second', attributes: { list: 'ordered' } },
          { insert: '\n', attributes: { list: 'ordered' } }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<ol>');
      expect(html).toContain('<li>First</li>');
      expect(html).toContain('<li>Second</li>');
    });

    test('renders blockquotes', () => {
      const delta = {
        ops: [
          { insert: 'Famous quote here', attributes: { blockquote: true } },
          { insert: '\n', attributes: { blockquote: true } }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<blockquote>Famous quote here</blockquote>');
    });

    test('renders code blocks', () => {
      const delta = {
        ops: [
          { insert: 'function hello() {\n  return "world";\n}', attributes: { 'code-block': true } },
          { insert: '\n', attributes: { 'code-block': true } }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<pre><code>');
      expect(html).toContain('function hello');
    });
  });

  describe('Links', () => {
    test('renders HTTP links', () => {
      const delta = {
        ops: [
          { insert: 'Click here', attributes: { link: 'https://example.com' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<a href="https://example.com">Click here</a>');
    });

    test('renders mailto links', () => {
      const delta = {
        ops: [
          { insert: 'email me', attributes: { link: 'mailto:test@example.com' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('mailto:test@example.com');
    });

    test('sanitizes javascript: URIs', () => {
      const delta = {
        ops: [
          { insert: 'click me', attributes: { link: 'javascript:alert("xss")' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('javascript:');
      expect(html).toContain('href="#"');
    });

    test('sanitizes data: URIs', () => {
      const delta = {
        ops: [
          { insert: 'click me', attributes: { link: 'data:text/html,<script>alert(1)</script>' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('data:');
      expect(html).toContain('href="#"');
    });
  });

  describe('Security', () => {
    test('escapes HTML in text', () => {
      const delta = {
        ops: [
          { insert: '<script>alert("xss")</script>\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('<script>');
      expect(html).toContain('&lt;script&gt;');
    });

    test('excludes image embeds (CVE-2025-15056)', () => {
      const delta = {
        ops: [
          { insert: { image: 'https://example.com/image.jpg' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('img');
      expect(html).not.toContain('example.com/image.jpg');
    });

    test('excludes video embeds (CVE-2025-15056)', () => {
      const delta = {
        ops: [
          { insert: { video: 'https://example.com/video.mp4' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('video');
      expect(html).not.toContain('example.com/video.mp4');
    });

    test('excludes formula embeds (CVE-2025-15056)', () => {
      const delta = {
        ops: [
          { insert: { formula: 'e=mc^2' } },
          { insert: '\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).not.toContain('formula');
      expect(html).not.toContain('e=mc');
    });
  });

  describe('Edge cases', () => {
    test('handles empty delta', () => {
      const delta = { ops: [] };
      const html = DeltaRenderer.render(delta);
      expect(html).toBe('<p></p>');
    });

    test('handles null/undefined delta', () => {
      expect(DeltaRenderer.render(null)).toBe('<p></p>');
      expect(DeltaRenderer.render(undefined)).toBe('<p></p>');
    });

    test('handles multiple paragraphs', () => {
      const delta = {
        ops: [
          { insert: 'Paragraph 1\n', attributes: {} },
          { insert: 'Paragraph 2\n', attributes: {} },
          { insert: 'Paragraph 3\n', attributes: {} }
        ]
      };

      const html = DeltaRenderer.render(delta);
      expect(html).toContain('<p>Paragraph 1</p>');
      expect(html).toContain('<p>Paragraph 2</p>');
      expect(html).toContain('<p>Paragraph 3</p>');
    });
  });

  describe('Parity with server', () => {
    test('matches server output for basic content', () => {
      const delta = {
        ops: [
          { insert: 'Hello ', attributes: {} },
          { insert: 'world', attributes: { bold: true } },
          { insert: '\n', attributes: {} }
        ]
      };

      // This would be fetched from server in real test
      const serverHtml = '<p>Hello <strong>world</strong></p>';

      const result = DeltaRenderer.testParity(delta, serverHtml);
      expect(result.match).toBe(true);
    });

    // TODO: Add server comparison tests after server-side renderer is exposed
  });
});

describe('Undo/Redo Manager', () => {
  let quill, undoRedoManager;

  beforeEach(() => {
    // Mock Quill instance
    quill = {
      history: {
        maxStack: 50,
        stack: { undo: [], redo: [] },
        undo: jest.fn(),
        redo: jest.fn()
      },
      on: jest.fn()
    };

    undoRedoManager = new UndoRedoManager(quill, {});
  });

  test('disables undo button when stack is empty', () => {
    quill.history.stack.undo = [];
    undoRedoManager.updateButtonStates();
    expect(undoRedoManager.undoBtn.disabled).toBe(true);
  });

  test('enables undo button when stack has items', () => {
    quill.history.stack.undo = [{ ops: [] }];
    undoRedoManager.updateButtonStates();
    expect(undoRedoManager.undoBtn.disabled).toBe(false);
  });

  test('calls quill.history.undo() on undo()', () => {
    undoRedoManager.undo();
    expect(quill.history.undo).toHaveBeenCalled();
  });

  test('calls quill.history.redo() on redo()', () => {
    undoRedoManager.redo();
    expect(quill.history.redo).toHaveBeenCalled();
  });
});
