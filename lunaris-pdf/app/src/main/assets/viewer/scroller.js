// The side scroller: a slim thumb that follows the document and, dragged, moves through all of it.
// Scrolling is reported from here too, since this is what already listens to it.
const HIDE_AFTER_MS = 1500;
const IDLE_AFTER_MS = 1200;
const THUMB_HEIGHT = 44;
const THUMB_HEIGHT_DRAGGING = 56;
const HIT_HEIGHT = 64;
const LABEL_HALF_HEIGHT = 30;
const SCROLL_NOISE_PX = 1;

export function installScroller({ container, viewer, goToPage, onScrollState, onChapterCrossed, onJump }) {
  const strip = document.getElementById('scroller');
  const track = document.getElementById('track');
  const thumb = document.getElementById('thumb');
  const hit = document.getElementById('thumb-hit');
  const label = document.getElementById('scroll-label');
  const labelPage = document.getElementById('scroll-label-page');
  const labelChapter = document.getElementById('scroll-label-chapter');
  let dragging = false;
  let pinned = false;
  let hideTimer = 0;
  let idleTimer = 0;
  let targetPage = 1;
  let startPage = 1;
  let chapters = [];
  let chapterShown = null;
  let lastTop = 0;
  let moving = false;
  let down = false;
  let reported = '';

  const scrollRange = () => container.scrollHeight - container.clientHeight;
  const thumbHeight = () => (dragging ? THUMB_HEIGHT_DRAGGING : THUMB_HEIGHT);
  const travel = () => strip.clientHeight - thumbHeight();

  const place = () => {
    const range = scrollRange();
    const top = range > 0 ? (container.scrollTop / range) * travel() : 0;
    thumb.style.top = `${top}px`;
    hit.style.top = `${top + thumbHeight() / 2 - HIT_HEIGHT / 2}px`;
    return top;
  };

  const show = () => {
    if (scrollRange() <= 0) return;
    strip.classList.add('visible');
    clearTimeout(hideTimer);
    if (!dragging && !pinned) hideTimer = setTimeout(() => strip.classList.remove('visible'), HIDE_AFTER_MS);
  };

  const report = () => {
    const state = `${moving}/${down}`;
    if (state === reported) return;
    reported = state;
    onScrollState(moving, down);
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

  const chapterAt = (page) => {
    let found = null;
    for (const chapter of chapters) {
      if (chapter.page <= page) found = chapter;
    }
    return found;
  };

  const moveTo = (clientY) => {
    const y = clientY - strip.getBoundingClientRect().top;
    const fraction = Math.max(0, Math.min(1, (y - thumbHeight() / 2) / travel()));
    const top = fraction * scrollRange();
    container.scrollTop = top;
    // The page in the middle of the screen is the one pdf.js counts as current, and the app shows.
    if (fraction === 0) targetPage = 1;
    else if (fraction === 1) targetPage = viewer.pagesCount;
    else targetPage = pageAt(top + container.clientHeight / 2);
    const chapter = chapterAt(targetPage);
    if (chapter !== chapterShown) {
      chapterShown = chapter;
      onChapterCrossed();
    }
    labelPage.textContent = `${targetPage} / ${viewer.pagesCount}`;
    labelChapter.textContent = chapter?.title ?? '';
    const thumbTop = fraction * travel();
    const middle = strip.getBoundingClientRect().top + thumbTop + thumbHeight() / 2;
    label.style.top = `${Math.max(0, middle - LABEL_HALF_HEIGHT)}px`;
  };

  // A mark on the track where each chapter starts.
  const drawTicks = () => {
    track.replaceChildren();
    const range = scrollRange();
    if (range <= 0) return;
    for (const { page } of chapters) {
      const view = viewer.getPageView(page - 1);
      if (!view) continue;
      const tick = document.createElement('div');
      tick.className = 'tick';
      tick.style.top = `${Math.min(1, view.div.offsetTop / range) * travel() + thumbHeight() / 2}px`;
      track.append(tick);
    }
  };

  container.addEventListener('scroll', () => {
    const top = container.scrollTop;
    if (top > lastTop + SCROLL_NOISE_PX) down = true;
    else if (top < lastTop - SCROLL_NOISE_PX) down = false;
    lastTop = top;
    moving = !dragging;
    if (dragging) down = true;
    report();
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => {
      moving = false;
      down = dragging;
      report();
    }, IDLE_AFTER_MS);
    place();
    show();
  }, { passive: true });

  hit.addEventListener('pointerdown', (event) => {
    event.preventDefault();
    dragging = true;
    startPage = viewer.currentPageNumber;
    chapterShown = chapterAt(startPage);
    hit.setPointerCapture(event.pointerId);
    strip.classList.add('dragging');
    label.style.display = 'block';
    drawTicks();
    show();
    moveTo(event.clientY);
  });
  hit.addEventListener('pointermove', (event) => {
    if (dragging) moveTo(event.clientY);
  });
  const release = () => {
    if (!dragging) return;
    dragging = false;
    strip.classList.remove('dragging');
    label.style.display = 'none';
    goToPage(targetPage);
    onJump(startPage, targetPage);
    place();
    show();
  };
  hit.addEventListener('pointerup', release);
  hit.addEventListener('pointercancel', release);

  return {
    setDocument() {
      place();
      show();
    },
    setChapters(list) {
      chapters = list;
    },
    // While annotating it stays: a finger on the edge would otherwise draw instead of scrolling.
    setPinned(on) {
      pinned = on;
      show();
    },
  };
}
