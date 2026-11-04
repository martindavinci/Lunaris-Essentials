// Makes the new files with pdf-lib, away from the page. Each is a fresh document: what was deleted really
// leaves. What cannot survive the move goes first, as a link to another page would carry that page along.
importScripts('pdf-lib.min.js');

const { PDFArray, PDFDict, PDFDocument, PDFName, PDFObjectCopier, PDFPage, degrees } = PDFLib;

const NAME = {
  annots: PDFName.of('Annots'),
  subtype: PDFName.of('Subtype'),
  link: PDFName.of('Link'),
  dest: PDFName.of('Dest'),
  action: PDFName.of('A'),
  kind: PDFName.of('S'),
  uri: PDFName.of('URI'),
  page: PDFName.of('P'),
  parent: PDFName.of('Parent'),
  additional: PDFName.of('AA'),
  beads: PDFName.of('B'),
  thumb: PDFName.of('Thumb'),
};
const PICTURE_LONG_SIDE = 842;
const FULL_TURN = 360;
const SAVE_SHARE = 0.25;
const PROGRESS_EVERY_MILLIS = 100;
const SAVE_ALL_AT_ONCE = 1e9;

class SourceFailure extends Error {
  constructor(reason, source) {
    super(reason);
    this.reason = reason;
    this.source = source;
  }
}

const bytesWaiting = new Map();
const documents = new Map();
const cleaned = new WeakSet();
let job = null;
let nextOutput = null;
let totalPages = 0;
let donePages = 0;
let lastProgress = 0;

const sourceBytes = (source) => new Promise((resolve) => {
  bytesWaiting.set(source, resolve);
  postMessage({ type: 'need', source });
});

const openPdf = async (source) => {
  if (documents.has(source)) return documents.get(source);
  const bytes = await sourceBytes(source);
  try {
    const opened = await PDFDocument.load(bytes, { updateMetadata: false, throwOnInvalidObject: false });
    documents.set(source, opened);
    return opened;
  } catch (failure) {
    if (failure instanceof RangeError) throw failure;
    const locked = /encrypted/i.test(String(failure && failure.message));
    throw new SourceFailure(locked ? 'protected' : 'damaged', source);
  }
};

const keepsItsPlace = (annotation) => {
  if (annotation.get(NAME.subtype) !== NAME.link) return true;
  if (annotation.has(NAME.dest)) return false;
  const action = annotation.lookupMaybe(NAME.action, PDFDict);
  return !action || action.get(NAME.kind) === NAME.uri;
};

/** Any of these can lead to other pages, which would then stay in the file. */
const cleanPage = (leaf) => {
  if (cleaned.has(leaf)) return;
  cleaned.add(leaf);
  leaf.delete(NAME.additional);
  leaf.delete(NAME.beads);
  leaf.delete(NAME.thumb);
  const annotations = leaf.lookupMaybe(NAME.annots, PDFArray);
  if (!annotations) return;
  for (let i = annotations.size() - 1; i >= 0; i -= 1) {
    const annotation = annotations.lookupMaybe(i, PDFDict);
    if (!annotation || !keepsItsPlace(annotation)) {
      annotations.remove(i);
    } else {
      annotation.delete(NAME.page);
      annotation.delete(NAME.additional);
      if (annotation.get(NAME.subtype) === PDFName.of('Widget')) annotation.delete(NAME.parent);
    }
  }
};

const turned = (page, turn) => {
  if (turn) page.setRotation(degrees((page.getRotation().angle + turn) % FULL_TURN));
};

const addCopiedPage = async (target, copiers, entry) => {
  const source = await openPdf(entry.source);
  if (entry.index >= source.getPageCount()) throw new SourceFailure('damaged', entry.source);
  if (!copiers.has(entry.source)) copiers.set(entry.source, PDFObjectCopier.for(source.context, target.context));
  const leaf = source.getPage(entry.index).node;
  cleanPage(leaf);
  const copied = copiers.get(entry.source).copy(leaf);
  const page = PDFPage.of(copied, target.context.register(copied), target);
  turned(page, entry.turn);
  target.addPage(page);
};

const addPicture = async (target, pictures, entry) => {
  if (!pictures.has(entry.source)) {
    const bytes = await sourceBytes(entry.source);
    try {
      const embed = job.sources[entry.source] === 'png' ? 'embedPng' : 'embedJpg';
      pictures.set(entry.source, await target[embed](bytes));
    } catch (failure) {
      if (failure instanceof RangeError) throw failure;
      throw new SourceFailure('damaged', entry.source);
    }
  }
  const picture = pictures.get(entry.source);
  const scale = PICTURE_LONG_SIDE / Math.max(picture.width, picture.height);
  const width = picture.width * scale;
  const height = picture.height * scale;
  const page = target.addPage([width, height]);
  page.drawImage(picture, { x: 0, y: 0, width, height });
  turned(page, entry.turn);
};

const report = (page, pages, output, force) => {
  const now = Date.now();
  if (!force && now - lastProgress < PROGRESS_EVERY_MILLIS) return;
  lastProgress = now;
  const fraction = (donePages / totalPages) * (1 - SAVE_SHARE);
  postMessage({ type: 'progress', fraction, page, pages, output });
};

const makeOutput = async (output) => {
  const pages = job.outputs[output];
  const target = await PDFDocument.create({ updateMetadata: false });
  const copiers = new Map();
  const pictures = new Map();
  for (let i = 0; i < pages.length; i += 1) {
    const entry = pages[i];
    if (entry.width) {
      turned(target.addPage([entry.width, entry.height]), entry.turn);
    } else if (job.sources[entry.source] === 'pdf') {
      await addCopiedPage(target, copiers, entry);
    } else {
      await addPicture(target, pictures, entry);
    }
    donePages += 1;
    report(i + 1, pages.length, output, false);
  }
  report(pages.length, pages.length, output, true);
  const bytes = await target.save({ useObjectStreams: true, updateFieldAppearances: false, objectsPerTick: SAVE_ALL_AT_ONCE });
  postMessage({ type: 'output', output, bytes }, [bytes.buffer]);
};

const failed = (failure) => {
  if (failure instanceof SourceFailure) {
    postMessage({ type: 'failed', reason: failure.reason, source: failure.source });
  } else {
    postMessage({ type: 'failed', reason: failure instanceof RangeError ? 'memory' : 'damaged' });
  }
};

const startNext = () => {
  if (nextOutput >= job.outputs.length) {
    postMessage({ type: 'done' });
    return;
  }
  const output = nextOutput;
  nextOutput += 1;
  makeOutput(output).catch(failed);
};

onmessage = ({ data }) => {
  switch (data.type) {
    case 'start':
      job = data.job;
      nextOutput = 0;
      totalPages = job.outputs.reduce((sum, pages) => sum + pages.length, 0);
      startNext();
      break;
    case 'source': {
      const resolve = bytesWaiting.get(data.source);
      bytesWaiting.delete(data.source);
      if (resolve) resolve(new Uint8Array(data.bytes));
      break;
    }
    case 'next':
      startNext();
      break;
    default:
  }
};
