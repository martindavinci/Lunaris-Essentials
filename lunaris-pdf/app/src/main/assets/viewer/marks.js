// Marks on the document that are not part of it: the sentence being read aloud, and the first page as a picture.
const THUMBNAIL_WIDTH_PX = 240;
const THUMBNAIL_QUALITY = 0.7;
const DATA_URL_START = 'base64,';

// Letters only: the app speaks text without the line breaks and hyphens of the layout.
const lettersOf = (text) => text.replace(/[\s\-­]/g, '');

export function installSentenceMark({ viewer, eventBus }) {
  let wanted = null;

  const clear = () => {
    for (const span of document.querySelectorAll('.speaking')) span.classList.remove('speaking');
  };

  // Line by line, so a sentence that starts halfway along marks that whole line. One not found is left unmarked.
  const apply = () => {
    clear();
    if (!wanted) return;
    const view = viewer.getPageView(wanted.page - 1);
    const spans = [...(view?.div.querySelectorAll('.textLayer span[role="presentation"]') ?? [])];
    let letters = '';
    const ends = spans.map((span) => {
      letters += lettersOf(span.textContent);
      return letters.length;
    });
    const sentence = lettersOf(wanted.text);
    const start = sentence ? letters.indexOf(sentence) : -1;
    if (start < 0) return;
    const end = start + sentence.length;
    spans.forEach((span, index) => {
      const from = index === 0 ? 0 : ends[index - 1];
      if (ends[index] > start && from < end) span.classList.add('speaking');
    });
  };

  // The text layer of a page comes after the page itself, and goes away with it.
  eventBus.on('textlayerrendered', ({ pageNumber }) => {
    if (wanted?.page === pageNumber) apply();
  });

  return {
    mark(page, text) {
      wanted = { page, text };
      apply();
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
