// Changing the document: the tool in hand and what has changed since the last save.
// The editors are the ones of pdf.js; what they lack with a finger on a phone is in editing_touches.js.
import { installEditingTouches } from './editing_touches.js';
import { installMarks } from './marks.js';
import { installExport } from './export.js';

const REPORT_MS = 250;
const FREE_HIGHLIGHT_OPACITY = 0.4;
const FULL_OPACITY = 1;
const EDITOR_KEY_START = 'pdfjs_internal_editor_';

export function installAnnotations({ container, viewer, eventBus, pdfjsLib, post, scroller }) {
  const { AnnotationEditorType: MODE, AnnotationEditorParamsType: PARAM } = pdfjsLib;
  const MODES = {
    highlight: MODE.HIGHLIGHT,
    highlightFree: MODE.INK,
    pen: MODE.INK,
    text: MODE.FREETEXT,
    note: MODE.STAMP,
    sign: MODE.STAMP,
    eraser: MODE.NONE,
  };
  const paramsOf = ({ tool, color, size }) => {
    switch (tool) {
      case 'highlight':
        return [[PARAM.HIGHLIGHT_COLOR, color]];
      case 'highlightFree':
        return [[PARAM.INK_COLOR, color], [PARAM.INK_THICKNESS, size], [PARAM.INK_OPACITY, FREE_HIGHLIGHT_OPACITY]];
      case 'pen':
        return [[PARAM.INK_COLOR, color], [PARAM.INK_THICKNESS, size], [PARAM.INK_OPACITY, FULL_OPACITY]];
      case 'text':
        return [[PARAM.FREETEXT_COLOR, color], [PARAM.FREETEXT_SIZE, size]];
      default:
        return [];
    }
  };

  let ui = null;
  let pdf = null;
  let current = { tool: null };
  let canUndo = false;
  let canRedo = false;
  let savedState = '[]';
  let savedCount = 0;
  // pdf.js also writes form values itself as it draws a page, for a radio button already chosen.
  const touchedFields = new Set();
  let reportTimer = 0;
  let reported = '';
  let signed = null;

  const commit = () => {
    ui?.commitOrRemove();
    ui?.currentLayer?.endDrawingSession(false);
  };

  const applyParams = () => {
    for (const [type, value] of paramsOf(current)) {
      eventBus.dispatch('switchannotationeditorparams', { source: null, type, value });
    }
  };

  const usersChanges = () => {
    const { map } = pdf?.annotationStorage.serializable ?? {};
    return map ? [...map].filter(([key]) => key.startsWith(EDITOR_KEY_START) || touchedFields.has(key)) : [];
  };

  const counts = () => {
    const changes = usersChanges();
    if (JSON.stringify(changes) === savedState) return { changes: 0, pages: 0 };
    const pages = new Set(changes.map(([, entry]) => entry.pageIndex).filter(Number.isInteger));
    return { changes: Math.max(1, changes.length - savedCount), pages: pages.size };
  };

  const report = () => {
    const message = { type: 'editsChanged', ...counts(), canUndo, canRedo };
    const text = JSON.stringify(message);
    if (text === reported) return;
    reported = text;
    post(message);
  };
  const reportSoon = () => {
    clearTimeout(reportTimer);
    reportTimer = setTimeout(report, REPORT_MS);
  };

  const marks = installMarks({ viewer, post, ui: () => ui, onEdit: reportSoon });
  installEditingTouches({
    container,
    tool: () => current.tool,
    ui: () => ui,
    marks,
    onNoText: () => post({ type: 'noTextHere' }),
    onLifted: () => {
      if (current.tool === 'text' && !ui?.hasSelection) applyParams();
      reportSoon();
    },
  });
  const exporter = installExport({ post, pdf: () => pdf, commit });

  eventBus.on('annotationeditoruimanager', ({ uiManager }) => {
    ui = uiManager;
  });
  eventBus.on('editingstateschanged', ({ details }) => {
    canUndo = details.hasSomethingToUndo ?? canUndo;
    canRedo = details.hasSomethingToRedo ?? canRedo;
    reportSoon();
  });
  // A form changes the document too, annotating or not.
  const fieldChanged = (event) => {
    const id = event.target.getAttribute?.('data-element-id');
    if (id) touchedFields.add(id);
    reportSoon();
  };
  container.addEventListener('input', fieldChanged, true);
  container.addEventListener('change', fieldChanged, true);
  container.addEventListener('touchend', reportSoon, { capture: true, passive: true });

  const setTool = (next) => {
    if (!ui) return;
    const sameText = next.tool === 'text' && current.tool === 'text';
    current = next;
    document.body.classList.toggle('annotating', next.tool !== null);
    document.body.dataset.tool = next.tool ?? '';
    scroller.setPinned(next.tool !== null);
    marks.disarm();
    // With an editor selected the options would go to it and not to the tool.
    if (!sameText) {
      commit();
      ui.unselectAll();
    }
    const mode = MODES[next.tool] ?? MODE.NONE;
    if (viewer.annotationEditorMode !== mode) viewer.annotationEditorMode = { mode };
    applyParams();
    reportSoon();
  };

  const isSigned = async () => {
    signed ??= Boolean((await pdf.getSignatures().catch(() => null))?.length);
    return signed;
  };

  const settle = async () => {
    commit();
    const now = counts();
    post({ type: 'editsSettled', ...now, signed: now.changes > 0 && await isSigned() });
  };

  const markSaved = () => {
    const changes = usersChanges();
    savedState = JSON.stringify(changes);
    savedCount = changes.length;
    report();
  };

  return {
    setDocument(document) {
      pdf = document;
    },
    handle(command) {
      switch (command.type) {
        case 'setTool': setTool(command); break;
        case 'undoEdit': ui?.undo(); reportSoon(); break;
        case 'redoEdit': ui?.redo(); reportSoon(); break;
        case 'deleteSelected': if (ui?.hasSelection) ui.delete(); reportSoon(); break;
        case 'settleEdits': settle(); break;
        case 'armSignature': marks.arm(command.png); break;
        case 'setNoteText': marks.setNoteText(command.id, command.text); break;
        case 'removeNote': marks.removeNote(command.id); break;
        case 'exportDocument': exporter.start(); break;
        case 'exportAck': exporter.ack(command.index); break;
        case 'markSaved': markSaved(); break;
      }
    },
  };
}
