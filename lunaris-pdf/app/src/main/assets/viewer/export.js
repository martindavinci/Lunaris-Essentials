// The document with its changes, sent to the app a piece at a time: each piece waits for the app to
// have written the last one, since a queue of messages between the two drops the oldest when it is full.
const CHUNK_BYTES = 1048576;
const STRING_BLOCK_BYTES = 8192;

const base64Of = (bytes) => {
  let text = '';
  for (let i = 0; i < bytes.length; i += STRING_BLOCK_BYTES) {
    text += String.fromCharCode.apply(null, bytes.subarray(i, i + STRING_BLOCK_BYTES));
  }
  return btoa(text);
};

export function installExport({ post, pdf, commit }) {
  let data = null;
  let total = 0;
  let next = 0;
  let attempt = 0;

  const send = (index) => {
    const start = index * CHUNK_BYTES;
    post({ type: 'exportChunk', index, total, data: base64Of(data.subarray(start, start + CHUNK_BYTES)) });
    next = index + 1;
  };

  return {
    async start() {
      const mine = ++attempt;
      data = null;
      try {
        commit();
        const saved = await pdf().saveDocument();
        if (mine !== attempt) return;
        data = saved;
        total = Math.ceil(saved.length / CHUNK_BYTES);
        send(0);
      } catch {
        if (mine === attempt) post({ type: 'exportFailed' });
      }
    },
    ack(index) {
      if (!data || index !== next - 1) return;
      if (next < total) {
        send(next);
      } else {
        post({ type: 'exportDone', bytes: data.length });
        data = null;
      }
    },
  };
}
