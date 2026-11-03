// Pinch to zoom, double tap to fit the width, single tap for the app's bars, and links.
const DOUBLE_TAP_MS = 280;
const TAP_SLOP_PX = 12;
const DOUBLE_TAP_ZOOM = 2;
const NAMED_FIT = 'page-width';
const FIT_SCALES = ['page-width', 'page-fit', 'page-actual', 'auto'];

const distance = (a, b) => Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
const middle = (a, b) => [(a.clientX + b.clientX) / 2, (a.clientY + b.clientY) / 2];

export function installGestures({ container, viewer, viewerElement, goToPage, onTap, onLink, onInternalLink }) {
  let pinch = null;
  let tapStart = null;
  let tapTimer = 0;

  // During the gesture only a CSS transform changes, which is cheap; the pages are drawn on release.
  container.addEventListener('touchstart', (event) => {
    if (event.touches.length === 2) {
      const [a, b] = event.touches;
      const origin = middle(a, b);
      const box = viewerElement.getBoundingClientRect();
      viewerElement.style.transformOrigin = `${origin[0] - box.left}px ${origin[1] - box.top}px`;
      pinch = { startDistance: distance(a, b), origin, factor: 1 };
      tapStart = null;
    } else if (event.touches.length === 1) {
      const [touch] = event.touches;
      tapStart = { x: touch.clientX, y: touch.clientY };
    }
  }, { passive: true });

  container.addEventListener('touchmove', (event) => {
    if (pinch && event.touches.length === 2) {
      event.preventDefault();
      pinch.factor = distance(event.touches[0], event.touches[1]) / pinch.startDistance;
      viewerElement.style.transform = `scale(${pinch.factor})`;
    } else if (tapStart) {
      const [touch] = event.touches;
      if (Math.hypot(touch.clientX - tapStart.x, touch.clientY - tapStart.y) > TAP_SLOP_PX) tapStart = null;
    }
  }, { passive: false });

  const endPinch = () => {
    viewerElement.style.transform = '';
    if (pinch.factor !== 1) viewer.updateScale({ scaleFactor: pinch.factor, origin: pinch.origin });
    pinch = null;
  };

  container.addEventListener('touchend', (event) => {
    if (pinch && event.touches.length < 2) endPinch();
  }, { passive: true });
  container.addEventListener('touchcancel', () => {
    if (pinch) endPinch();
    tapStart = null;
  }, { passive: true });

  const doubleTap = (x, y) => {
    if (FIT_SCALES.includes(String(viewer.currentScaleValue))) {
      viewer.updateScale({ scaleFactor: DOUBLE_TAP_ZOOM, origin: [x, y] });
    } else {
      viewer.currentScaleValue = NAMED_FIT;
    }
  };

  // Capturing, so a link in a PDF never navigates: the app shows it first.
  container.addEventListener('click', (event) => {
    const link = event.target.closest?.('a');
    if (link) {
      if (link.classList.contains('internalLink')) {
        onInternalLink();
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      if (link.href) onLink(link.href);
      return;
    }
    if (!tapStart || !getSelection().isCollapsed) return;
    if (tapTimer) {
      clearTimeout(tapTimer);
      tapTimer = 0;
      doubleTap(event.clientX, event.clientY);
    } else {
      tapTimer = setTimeout(() => {
        tapTimer = 0;
        onTap();
      }, DOUBLE_TAP_MS);
    }
  }, true);

  addEventListener('resize', () => {
    const scale = String(viewer.currentScaleValue);
    if (!viewer.pagesCount || !FIT_SCALES.includes(scale)) return;
    const page = viewer.currentPageNumber;
    viewer.currentScaleValue = scale;
    goToPage(page);
  });
}
