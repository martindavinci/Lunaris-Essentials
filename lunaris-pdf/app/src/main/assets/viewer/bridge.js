// The viewer page: pdf.js showing one document, driven by the app through window.lunaris.handle.
// The page has no UI of its own beyond the document and the side scroller.
import * as pdfjsLib from './pdf.min.mjs';
import { installScroller } from './scroller.js';
import { installGestures } from './gestures.js';

globalThis.pdfjsLib = pdfjsLib;
await import('./pdf_viewer.mjs');
const { EventBus, PDFLinkService, PDFFindController, PDFViewer } = globalThis.pdfjsViewer;
pdfjsLib.GlobalWorkerOptions.workerSrc = 'pdf.worker.min.mjs';

const FIND_PENDING = 3;
const RANGE_CHUNK_BYTES = 262144;
const MAX_OUTLINE_ITEMS = 5000;
// As tall as the top padding of #viewer: a page lands below the app's bar, not under it.
const BAR_GAP_PX = 64;

const post = (message) => globalThis.lunarisHost.postMessage(JSON.stringify(message));

const container = document.getElementById('container');
const viewerElement = document.getElementById('viewer');
const eventBus = new EventBus();
const linkService = new PDFLinkService({ eventBus });
const findController = new PDFFindController({ eventBus, linkService });
const viewer = new PDFViewer({
  container,
  viewer: viewerElement,
  eventBus,
  linkService,
  findController,
  removePageBorders: true,
  textLayerMode: 1,
  annotationMode: pdfjsLib.AnnotationMode.ENABLE,
  annotationEditorMode: pdfjsLib.AnnotationEditorType.DISABLE,
});
linkService.setViewer(viewer);

let pdf = null;
let task = null;
let answerPassword = null;
let lastQuery = null;
let start = { page: 1, scale: 'page-width' };

const goToPage = (page) => {
  viewer.currentPageNumber = page;
  container.scrollTop = Math.max(0, container.scrollTop - BAR_GAP_PX);
};

const reportPage = () => post({
  type: 'pageChanged',
  page: viewer.currentPageNumber,
  scale: String(viewer.currentScaleValue),
});
eventBus.on('pagechanging', reportPage);
eventBus.on('scalechanging', reportPage);

eventBus.on('pagesinit', () => {
  viewer.currentScaleValue = start.scale;
  goToPage(Math.min(start.page, pdf.numPages));
  post({ type: 'loaded', pages: pdf.numPages, outline: [] });
  sendOutline();
});

const pageOfDestination = async (dest) => {
  try {
    const explicit = typeof dest === 'string' ? await pdf.getDestination(dest) : dest;
    if (!Array.isArray(explicit)) return undefined;
    const target = explicit[0];
    return Number.isInteger(target) ? target + 1 : (await pdf.getPageIndex(target)) + 1;
  } catch {
    return undefined;
  }
};

// After the first page: a long outline must not hold the document back.
const sendOutline = async () => {
  const items = [];
  const walk = (entries, depth) => {
    for (const entry of entries ?? []) {
      if (items.length >= MAX_OUTLINE_ITEMS) return;
      items.push({ entry, depth });
      walk(entry.items, depth + 1);
    }
  };
  walk(await pdf.getOutline().catch(() => null), 0);
  if (items.length === 0) return;
  const outline = await Promise.all(items.map(async ({ entry, depth }) => ({
    title: entry.title,
    page: entry.dest ? await pageOfDestination(entry.dest) : undefined,
    depth,
  })));
  post({ type: 'loaded', pages: pdf.numPages, outline });
};

const findResult = (matches, done) => post({
  type: 'findResult',
  current: matches?.current ?? 0,
  total: matches?.total ?? 0,
  done,
});
eventBus.on('updatefindmatchescount', ({ matchesCount }) => findResult(matchesCount, false));
eventBus.on('updatefindcontrolstate', ({ state, matchesCount }) => findResult(matchesCount, state !== FIND_PENDING));

const open = ({ password, page, scale }) => {
  start = { page, scale };
  if (answerPassword) {
    const answer = answerPassword;
    answerPassword = null;
    answer(password ?? '');
    return;
  }
  task = pdfjsLib.getDocument({
    url: 'document.pdf',
    password,
    wasmUrl: 'wasm/',
    cMapUrl: 'cmaps/',
    cMapPacked: true,
    standardFontDataUrl: 'standard_fonts/',
    iccUrl: 'iccs/',
    enableXfa: false,
    isEvalSupported: false,
    disableRange: false,
    // pdf.js stops reading the whole file only with both of these.
    disableStream: true,
    disableAutoFetch: true,
    rangeChunkSize: RANGE_CHUNK_BYTES,
  });
  task.onPassword = (answer, reason) => {
    answerPassword = answer;
    post({ type: 'passwordNeeded', wrong: reason === pdfjsLib.PasswordResponses.INCORRECT_PASSWORD });
  };
  task.promise.then((document) => {
    pdf = document;
    viewer.setDocument(document);
    linkService.setDocument(document, null);
    scroller.setDocument();
  }).catch(() => post({ type: 'loadFailed' }));
};

const readPageText = async (page) => {
  const content = await (await pdf.getPage(page)).getTextContent();
  const text = content.items.map((item) => (item.str ?? '') + (item.hasEOL ? '\n' : '')).join('');
  post({ type: 'pageText', page, text });
};

const find = ({ query, previous }) => {
  eventBus.dispatch('find', {
    source: null,
    type: query === lastQuery ? 'again' : '',
    query,
    caseSensitive: false,
    entireWord: false,
    highlightAll: true,
    findPrevious: previous,
    matchDiacritics: false,
  });
  lastQuery = query;
};

const inRange = (page) => pdf && Number.isInteger(page) && page >= 1 && page <= pdf.numPages;

const scroller = installScroller({ container, viewer, goToPage });
installGestures({
  container,
  viewer,
  viewerElement,
  goToPage,
  onTap: () => post({ type: 'tapped' }),
  onLink: (url) => post({ type: 'linkTapped', url }),
});

globalThis.lunaris = {
  handle(command) {
    switch (command.type) {
      case 'open': open(command); break;
      case 'goToPage': if (inRange(command.page)) goToPage(command.page); break;
      case 'find': find(command); break;
      case 'closeFind':
        lastQuery = null;
        eventBus.dispatch('findbarclose', { source: null });
        break;
      case 'setNight': document.body.classList.toggle('night', command.on); break;
      case 'readPageText':
        if (inRange(command.page)) readPageText(command.page).catch(() => post({ type: 'pageText', page: command.page, text: '' }));
        break;
    }
  },
};

post({ type: 'ready' });
