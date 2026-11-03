// Signatures and notes: pictures put on the page where the finger taps. pdf.js has no note of its own
// that it can save, but a picture can carry a comment, which it writes as a normal PDF note.
const SIGNATURE_WIDTH = 0.3;
const PIN_WIDTH = 0.05;
const PIN_PX = 96;
const PIN_FOLD = 0.3;
const PIN_FILL = '#ffd54f';
const PIN_EDGE = '#b8860b';
const PNG = 'image/png';

const blobOf = (base64) => {
  const text = atob(base64);
  const bytes = new Uint8Array(text.length);
  for (let i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i);
  return new Blob([bytes], { type: PNG });
};

// Made at once: the finger is on the page now, and a tool may change by the time something slower ends.
const pinPicture = () => {
  const canvas = document.createElement('canvas');
  canvas.width = PIN_PX;
  canvas.height = PIN_PX;
  const context = canvas.getContext('2d');
  const fold = PIN_PX * PIN_FOLD;
  context.fillStyle = PIN_FILL;
  context.strokeStyle = PIN_EDGE;
  context.lineWidth = PIN_PX / 24;
  context.beginPath();
  context.moveTo(2, 2);
  context.lineTo(PIN_PX - 2, 2);
  context.lineTo(PIN_PX - 2, PIN_PX - fold);
  context.lineTo(PIN_PX - fold, PIN_PX - 2);
  context.lineTo(2, PIN_PX - 2);
  context.closePath();
  context.fill();
  context.stroke();
  return blobOf(canvas.toDataURL(PNG).split(',')[1]);
};

export function installMarks({ viewer, post, ui, onEdit }) {
  const notes = new Map();
  let signature = null;
  let pin = null;

  const pointsAt = (x, y) => document.elementsFromPoint(x, y);

  const pageAt = (x, y) => pointsAt(x, y)
    .map((element) => element.closest('.page[data-page-number]'))
    .find(Boolean);

  // Centred on the finger. The sizes are parts of the page, and set before pdf.js reads the picture.
  const place = (x, y, blob, width, ratio) => {
    const page = pageAt(x, y);
    const layer = page && viewer.getPageView(Number(page.dataset.pageNumber) - 1).annotationEditorLayer?.annotationEditorLayer;
    if (!layer) return null;
    const box = layer.div.getBoundingClientRect();
    const editor = layer.createAndAddNewEditor(
      { offsetX: x - box.left, offsetY: y - box.top },
      true,
      { bitmapFile: new File([blob], 'mark.png', { type: PNG }) },
    );
    if (!editor) return null;
    const [pageWidth, pageHeight] = editor.pageDimensions;
    editor.width = width;
    editor.height = width * ratio * (pageWidth / pageHeight);
    return editor;
  };

  const onStamp = (x, y) => pointsAt(x, y).some((element) => element.closest('.stampEditor'));

  const noteAt = (x, y) => pointsAt(x, y)
    .map((element) => notes.get(element.closest('.stampEditor')?.id))
    .find(Boolean);

  const tapForNote = (x, y) => {
    const existing = noteAt(x, y);
    if (existing) {
      post({ type: 'noteRequested', id: existing.id, text: existing.comment?.text ?? '' });
      return;
    }
    pin ??= pinPicture();
    const editor = place(x, y, pin, PIN_WIDTH, 1);
    if (!editor) return;
    editor.div.classList.add('pin');
    notes.set(editor.id, editor);
    post({ type: 'noteRequested', id: editor.id, text: '' });
  };

  const tapForSignature = (x, y) => {
    if (!signature) {
      // A tap on one that is there picks it up, to move it or change its size.
      if (!onStamp(x, y)) ui()?.unselectAll();
      return;
    }
    const placed = place(x, y, signature.blob, SIGNATURE_WIDTH, signature.ratio);
    if (!placed) return;
    signature = null;
    post({ type: 'signaturePlaced' });
  };

  return {
    tap(tool, x, y) {
      if (tool === 'note') tapForNote(x, y);
      else tapForSignature(x, y);
    },
    disarm() {
      signature = null;
    },
    async arm(png) {
      const blob = blobOf(png);
      const picture = await createImageBitmap(blob);
      signature = { blob, ratio: picture.height / picture.width };
      picture.close();
    },
    setNoteText(id, text) {
      const editor = notes.get(id);
      if (!editor) return;
      editor.comment = { text, richText: null, date: new Date(), deleted: false };
      onEdit();
    },
    // A new one that stays empty leaves nothing to undo; one that had text can come back.
    removeNote(id) {
      const editor = notes.get(id);
      if (!editor) return;
      if (editor.comment?.text) {
        ui()?.setSelected(editor);
        ui()?.delete();
      } else {
        editor.remove();
        notes.delete(id);
      }
      onEdit();
    },
  };
}
