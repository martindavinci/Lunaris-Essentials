// What one finger does while annotating. pdf.js draws and types by itself, but with a finger it does
// not select text, so the highlighter is here, along with the eraser and putting a mark on a page.
// Two fingers stay with gestures.js: they scroll and zoom.
const TAP_SLOP_PX = 12;
const ERASER_REACH_PX = 10;
const TEXT_REACH_PX = 24;
const OWN_POINTERS = ['highlight', 'eraser'];
const PLACING = ['note', 'sign'];
const DRAWING = ['pen', 'highlightFree'];

// The browser answers with the nearest text however far it is: a finger on a picture is not on the title.
const textPositionAt = (x, y) => {
  const caret = document.caretRangeFromPoint(x, y);
  const parent = caret?.startContainer.parentElement;
  const layer = parent?.closest('.textLayer');
  if (!layer) return null;
  const box = parent.getBoundingClientRect();
  const near = x >= box.left - TEXT_REACH_PX && x <= box.right + TEXT_REACH_PX
    && y >= box.top - TEXT_REACH_PX && y <= box.bottom + TEXT_REACH_PX;
  return near ? { node: caret.startContainer, offset: caret.startOffset, layer } : null;
};

export function installEditingTouches({ container, tool, ui, marks, onNoText, onLifted }) {
  let first = null;
  let anchor = null;
  let selecting = false;
  let start = null;
  let last = null;

  const onEditor = (event) => event.target.closest?.('.stampEditor');

  // pdf.js must not see what these tools do with the finger, nor a second finger on any of them.
  const filterPointer = (event) => {
    const current = tool();
    if (!current) {
      first = null;
      return;
    }
    if (event.type === 'pointerdown' && first === null) first = event.pointerId;
    const second = event.pointerId !== first;
    const own = OWN_POINTERS.includes(current) || (PLACING.includes(current) && !onEditor(event));
    if (second || own) event.stopImmediatePropagation();
    if ((event.type === 'pointerup' || event.type === 'pointercancel') && !second) first = null;
  };
  for (const type of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) {
    container.addEventListener(type, filterPointer, true);
  }
  container.addEventListener('contextmenu', (event) => {
    if (tool()) event.preventDefault();
  }, true);

  const moveSelection = (x, y) => {
    const position = textPositionAt(x, y);
    if (!position) return;
    anchor ??= position;
    if (position.layer !== anchor.layer) return;
    getSelection().setBaseAndExtent(anchor.node, anchor.offset, position.node, position.offset);
  };

  const finishSelection = (moved) => {
    const manager = ui();
    if (getSelection().isCollapsed) {
      if (moved && !anchor) onNoText();
    } else {
      manager?.highlightSelection('touch');
      manager?.unselectAll();
    }
    getSelection().empty();
    anchor = null;
  };

  const editorAt = (x, y) => {
    const manager = ui();
    const page = document.elementsFromPoint(x, y)
      .map((element) => element.closest('.page[data-page-number]'))
      .find(Boolean);
    if (!manager || !page) return null;
    let found = null;
    let foundArea = Infinity;
    for (const editor of manager.getEditors(Number(page.dataset.pageNumber) - 1)) {
      const box = editor.div?.getBoundingClientRect();
      if (!box) continue;
      const near = x >= box.left - ERASER_REACH_PX && x <= box.right + ERASER_REACH_PX
        && y >= box.top - ERASER_REACH_PX && y <= box.bottom + ERASER_REACH_PX;
      const area = box.width * box.height;
      if (near && area < foundArea) {
        found = editor;
        foundArea = area;
      }
    }
    return found;
  };

  const eraseAt = (x, y) => {
    const editor = editorAt(x, y);
    if (!editor) return;
    ui().setSelected(editor);
    ui().delete();
  };

  const at = (touch) => [touch.clientX, touch.clientY];

  container.addEventListener('touchstart', (event) => {
    const current = tool();
    if (!current) return;
    if (event.touches.length > 1) {
      // The second finger: this was the start of a scroll, not of a mark.
      selecting = false;
      start = null;
      anchor = null;
      getSelection().empty();
      return;
    }
    const [x, y] = at(event.touches[0]);
    start = { x, y };
    last = { x, y };
    // No long press selection, no click: the finger belongs to the tool.
    if (OWN_POINTERS.includes(current) || (PLACING.includes(current) && !onEditor(event))) event.preventDefault();
    if (current === 'highlight') {
      selecting = true;
      moveSelection(x, y);
    } else if (current === 'eraser') {
      eraseAt(x, y);
    }
  }, { capture: true, passive: false });

  container.addEventListener('touchmove', (event) => {
    const current = tool();
    if (!current || !start || event.touches.length > 1) return;
    const [x, y] = at(event.touches[0]);
    last = { x, y };
    if (selecting) moveSelection(x, y);
    // A finger never lies quite still: one tap takes one mark.
    else if (current === 'eraser' && Math.hypot(x - start.x, y - start.y) > TAP_SLOP_PX) eraseAt(x, y);
  }, { capture: true, passive: true });

  container.addEventListener('touchend', (event) => {
    const current = tool();
    if (!current || event.touches.length > 0) return;
    const wasStart = start;
    start = null;
    if (wasStart) {
      const moved = Math.hypot(last.x - wasStart.x, last.y - wasStart.y) > TAP_SLOP_PX;
      const wasSelecting = selecting;
      selecting = false;
      if (wasSelecting) finishSelection(moved);
      else if (PLACING.includes(current) && !moved) marks.tap(current, last.x, last.y);
      else if (DRAWING.includes(current)) {
        // After pdf.js has closed the line. On its own it keeps the next ones in the same mark.
        setTimeout(() => {
          ui()?.currentLayer?.endDrawingSession(false);
          ui()?.unselectAll();
          onLifted();
        });
      }
    }
    onLifted();
  }, { capture: true, passive: true });

  container.addEventListener('touchcancel', () => {
    selecting = false;
    start = null;
    anchor = null;
    getSelection().empty();
  }, { capture: true, passive: true });
}
