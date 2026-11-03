// Marks on the document that are not part of it: the sentence being read aloud, and the first page as a picture.
const THUMBNAIL_WIDTH_PX = 240;
const THUMBNAIL_QUALITY = 0.7;
const DATA_URL_START = 'base64,';
// The card of the controls covers this much above the bottom of the screen.
const CARD_SPACE_PX = 120;
const SIGHT_MARGIN_PX = 16;

// Letters only: the app speaks text without the line breaks and hyphens of the layout.
const isSkipped = (character) => /[\s\-­]/.test(character);
const lettersOf = (text) => [...text].filter((character) => !isSkipped(character)).join('');

// Every letter of the text layer and where it is, to find a sentence that crosses lines.
const lettersOfLayer = (spans) => {
  let letters = '';
  const places = [];
  for (const span of spans) {
    const walker = document.createTreeWalker(span, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const text = node.data;
      for (let offset = 0; offset < text.length;) {
        const character = String.fromCodePoint(text.codePointAt(offset));
        if (!isSkipped(character)) {
          letters += character;
          for (let unit = 0; unit < character.length; unit++) places.push({ node, offset });
        }
        offset += character.length;
      }
    }
  }
  return { letters, places };
};

// The same words can come back on a page: the voice says which time it is at.
const indexOfOccurrence = (letters, sentence, occurrence) => {
  let found = letters.indexOf(sentence);
  for (let seen = 0; seen < occurrence && found >= 0; seen++) {
    found = letters.indexOf(sentence, found + sentence.length);
  }
  return found;
};

export function installSentenceMark({ container, viewer, eventBus, barTop }) {
  let wanted = null;

  const clear = () => {
    for (const layer of document.querySelectorAll('.speaking-layer')) layer.remove();
  };

  // The page stays where it is while the sentence is in sight, and moves only when the voice leaves it behind.
  const keepInSight = (top, bottom) => {
    const clearTop = barTop() + SIGHT_MARGIN_PX;
    const clearBottom = container.clientHeight - CARD_SPACE_PX;
    if (top < clearTop || bottom > clearBottom) {
      container.scrollBy({ top: top - clearTop, behavior: 'smooth' });
    }
  };

  // A sentence not found, because the text of the page is laid out differently from how it reads, stays unmarked.
  const apply = (follow) => {
    clear();
    if (!wanted) return;
    const view = viewer.getPageView(wanted.page - 1);
    const spans = [...(view?.div.querySelectorAll('.textLayer span[role="presentation"]') ?? [])];
    const sentence = lettersOf(wanted.text);
    if (!sentence || spans.length === 0) return;
    const { letters, places } = lettersOfLayer(spans);
    const start = indexOfOccurrence(letters, sentence, wanted.occurrence);
    if (start < 0) return;
    const first = places[start];
    const last = places[start + sentence.length - 1];
    const range = document.createRange();
    range.setStart(first.node, first.offset);
    range.setEnd(last.node, last.offset + 1);
    const page = view.div.getBoundingClientRect();
    const rects = [...range.getClientRects()].filter((rect) => rect.width > 0 && rect.height > 0);
    if (rects.length === 0) return;
    const layer = document.createElement('div');
    layer.className = 'speaking-layer';
    for (const rect of rects) {
      const mark = document.createElement('div');
      mark.style.left = `${rect.left - page.left}px`;
      mark.style.top = `${rect.top - page.top}px`;
      mark.style.width = `${rect.width}px`;
      mark.style.height = `${rect.height}px`;
      layer.append(mark);
    }
    view.div.append(layer);
    if (follow) keepInSight(Math.min(...rects.map((rect) => rect.top)), Math.max(...rects.map((rect) => rect.bottom)));
  };

  // The text layer of a page comes after the page itself, goes away with it, and changes with the zoom.
  eventBus.on('textlayerrendered', ({ pageNumber }) => {
    if (wanted?.page === pageNumber) apply(true);
  });

  return {
    mark(page, text, occurrence) {
      wanted = { page, text, occurrence };
      apply(true);
    },
    unmark() {
      wanted = null;
      clear();
    },
  };
}

// Base64 JPEG. On a canvas of its own: the night colours are only a filter on the page shown.
export async function firstPageJpeg(pdf) {
  const page = await pdf.getPage(1);
  const scale = THUMBNAIL_WIDTH_PX / page.getViewport({ scale: 1 }).width;
  const viewport = page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  canvas.width = Math.ceil(viewport.width);
  canvas.height = Math.ceil(viewport.height);
  await page.render({ canvas, viewport }).promise;
  const url = canvas.toDataURL('image/jpeg', THUMBNAIL_QUALITY);
  return url.slice(url.indexOf(DATA_URL_START) + DATA_URL_START.length);
}
