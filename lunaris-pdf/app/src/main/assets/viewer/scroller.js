// The side scroller: a thumb that follows the document and, dragged, moves through all of it.
const HIDE_AFTER_MS = 1500;
const THUMB_HEIGHT = 64;
const LABEL_HALF_HEIGHT = 16;

export function installScroller({ container, viewer, goToPage }) {
  const strip = document.getElementById('scroller');
  const thumb = document.getElementById('thumb');
  const label = document.getElementById('scroll-label');
  let dragging = false;
  let hideTimer = 0;
  let targetPage = 1;

  const scrollRange = () => container.scrollHeight - container.clientHeight;
  const travel = () => strip.clientHeight - THUMB_HEIGHT;

  const place = () => {
    const range = scrollRange();
    thumb.style.top = `${range > 0 ? (container.scrollTop / range) * travel() : 0}px`;
  };

  const show = () => {
    if (scrollRange() <= 0) return;
    strip.classList.add('visible');
    clearTimeout(hideTimer);
    if (!dragging) hideTimer = setTimeout(() => strip.classList.remove('visible'), HIDE_AFTER_MS);
  };

  // Binary search: the pages may have different heights, so the page is read from the layout.
  const pageAt = (top) => {
    let low = 1;
    let high = viewer.pagesCount;
    while (low < high) {
      const middle = Math.ceil((low + high) / 2);
      if (viewer.getPageView(middle - 1).div.offsetTop <= top) low = middle;
      else high = middle - 1;
    }
    return low;
  };

  const moveTo = (clientY) => {
    const fraction = Math.max(0, Math.min(1, (clientY - THUMB_HEIGHT / 2) / travel()));
    const top = fraction * scrollRange();
    container.scrollTop = top;
    // The page in the middle of the screen is the one pdf.js counts as current, and the app shows.
    if (fraction === 0) targetPage = 1;
    else if (fraction === 1) targetPage = viewer.pagesCount;
    else targetPage = pageAt(top + container.clientHeight / 2);
    label.textContent = `${targetPage} / ${viewer.pagesCount}`;
    label.style.top = `${Math.max(0, clientY - LABEL_HALF_HEIGHT)}px`;
  };

  container.addEventListener('scroll', () => {
    place();
    show();
  }, { passive: true });

  strip.addEventListener('pointerdown', (event) => {
    dragging = true;
    strip.setPointerCapture(event.pointerId);
    strip.classList.add('dragging');
    label.style.display = 'block';
    show();
    moveTo(event.clientY);
  });
  strip.addEventListener('pointermove', (event) => {
    if (dragging) moveTo(event.clientY);
  });
  const release = () => {
    if (!dragging) return;
    dragging = false;
    strip.classList.remove('dragging');
    label.style.display = 'none';
    goToPage(targetPage);
    show();
  };
  strip.addEventListener('pointerup', release);
  strip.addEventListener('pointercancel', release);

  return {
    setDocument() {
      place();
      show();
    },
  };
}
