// The first page as a picture for the list of recent documents.
const THUMBNAIL_WIDTH_PX = 240;
const THUMBNAIL_QUALITY = 0.7;
const DATA_URL_START = 'base64,';

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
