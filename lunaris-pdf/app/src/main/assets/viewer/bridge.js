// The viewer page: pdf.js showing one document, driven by the app through window.lunaris.handle.
// The page has no UI of its own beyond the document and the side scroller.
import * as pdfjsLib from './pdf.min.mjs';
import { installScroller } from './scroller.js';
import { installGestures } from './gestures.js';
import { firstPageJpeg, installSentenceMark } from './marks.js';

globalThis.pdfjsLib = pdfjsLib;
await import('./pdf_viewer.mjs');
const { EventBus, PDFLinkService, PDFFindController, PDFViewer } = globalThis.pdfjsViewer;
pdfjsLib.GlobalWorkerOptions.workerSrc = 'pdf.worker.min.mjs';

const FIND_PENDING = 3;
const RANGE_CHUNK_BYTES = 262144;
const MAX_OUTLINE_ITEMS = 5000;
const LINK_SETTLE_MS = 400;
// The search bars are taller than the usual ones.
const SEARCH_BAR_EXTRA_PX = 56;

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
let lastSearch = null;
let start = { page: 1, scale: 'page-width' };
// As tall as the app's top bar: a page lands below it, not under it. It is also the top padding of #viewer.
let barTop = 96;

const goToPage = (page) => {
  viewer.currentPageNumber = page;
  container.scrollTop = Math.max(0, container.scrollTop - barTop);
};

const setBarSpace = ({ top, bottom }) => {
  barTop = top;
  document.documentElement.style.setProperty('--bar-top', `${top}px`);
  document.documentElement.style.setProperty('--bar-bottom', `${bottom}px`);
};

const firstPageDrawn = new Promise((resolve) => eventBus.on('pagerendered', resolve, { once: true }));

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
  const top = Math.min(...outline.map((item) => item.depth));
  scroller.setChapters(outline.filter((item) => item.depth === top && item.page));
};

const findResult = (matches, done) => {
  const total = matches?.total ?? 0;
  const pageIndex = total > 0 ? findController.selected.pageIdx : -1;
  post({
    type: 'findResult',
    current: matches?.current ?? 0,
    total,
    done,
    page: pageIndex + 1,
    onPage: findController.pageMatches[pageIndex]?.length ?? 0,
  });
};
// pdf.js leaves the current match near the top of the page area, which is under the search bar.
const keepMatchInSight = () => {
  const match = document.querySelector('.highlight.selected');
  if (!match) return;
  const top = match.getBoundingClientRect().top;
  const clear = barTop + SEARCH_BAR_EXTRA_PX;
  if (top < clear) container.scrollTop -= clear - top;
};
eventBus.on('updatetextlayermatches', () => requestAnimationFrame(keepMatchInSight));

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

const find = ({ query, previous, caseSensitive, wholeWords }) => {
  const search = JSON.stringify([query, caseSensitive, wholeWords]);
  eventBus.dispatch('find', {
    source: null,
    type: search === lastSearch ? 'again' : '',
    query,
    caseSensitive,
    entireWord: wholeWords,
    highlightAll: true,
    findPrevious: previous,
    matchDiacritics: false,
  });
  lastSearch = search;
};

// After the page on screen, which must not wait for it.
const makeThumbnail = async () => {
  await firstPageDrawn;
  post({ type: 'thumbnail', jpeg: await firstPageJpeg(pdf) });
};

const inRange = (page) => pdf && Number.isInteger(page) && page >= 1 && page <= pdf.numPages;

const onJump = (from, to) => {
  if (from !== to) post({ type: 'jumped', from, to });
};

const scroller = installScroller({
  container,
  viewer,
  goToPage,
  onScrollState: (moving, down) => post({ type: 'scrollChanged', moving, down }),
  onChapterCrossed: () => post({ type: 'chapterCrossed' }),
  onJump,
});
const sentenceMark = installSentenceMark({ viewer, eventBus });
installGestures({
  container,
  viewer,
  viewerElement,
  goToPage,
  onTap: () => post({ type: 'tapped' }),
  onLink: (url) => post({ type: 'linkTapped', url }),
  onInternalLink: () => {
    const from = viewer.currentPageNumber;
    setTimeout(() => onJump(from, viewer.currentPageNumber), LINK_SETTLE_MS);
  },
});

globalThis.lunaris = {
  handle(command) {
    switch (command.type) {
      case 'open': open(command); break;
      case 'goToPage': if (inRange(command.page)) goToPage(command.page); break;
      case 'find': find(command); break;
      case 'closeFind':
        lastSearch = null;
        eventBus.dispatch('findbarclose', { source: null });
        break;
      case 'setNight': document.body.classList.toggle('night', command.on); break;
      case 'readPageText':
        if (inRange(command.page)) readPageText(command.page).catch(() => post({ type: 'pageText', page: command.page, text: '' }));
        break;
      case 'setBarSpace': setBarSpace(command); break;
      case 'makeThumbnail': if (pdf) makeThumbnail().catch(() => {}); break;
      case 'markSentence': sentenceMark.mark(command.page, command.text); break;
      case 'clearMark': sentenceMark.unmark(); break;
    }
  },
};

post({ type: 'ready' });
