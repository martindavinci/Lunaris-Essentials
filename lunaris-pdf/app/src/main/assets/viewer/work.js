// Between the app and the worker that makes the files. Sources are read here, as the app answers
// requests from this page only, and each file goes to the app a piece at a time: every piece waits for
// the last to be written, since a queue of messages between the two drops the oldest when it is full.
const CHUNK_BYTES = 1048576;
const STRING_BLOCK_BYTES = 8192;

const post = (message) => lunarisHost.postMessage(JSON.stringify(message));

const base64Of = (bytes) => {
  let text = '';
  for (let i = 0; i < bytes.length; i += STRING_BLOCK_BYTES) {
    text += String.fromCharCode.apply(null, bytes.subarray(i, i + STRING_BLOCK_BYTES));
  }
  return btoa(text);
};

let worker = null;
let current = null;

const sendPiece = (index) => {
  const start = index * CHUNK_BYTES;
  post({
    type: 'chunk',
    output: current.output,
    index,
    total: current.total,
    data: base64Of(current.bytes.subarray(start, start + CHUNK_BYTES)),
  });
};

const stop = () => {
  if (worker) worker.terminate();
  worker = null;
  current = null;
};

const fail = (reason, source) => {
  stop();
  post(source === undefined ? { type: 'failed', reason } : { type: 'failed', reason, source });
};

const readSource = async (source) => {
  let answer;
  try {
    answer = await fetch(`/source/${source}`);
  } catch {
    fail('unreadable', source);
    return;
  }
  if (!answer.ok) {
    fail('unreadable', source);
    return;
  }
  try {
    const bytes = await answer.arrayBuffer();
    if (worker) worker.postMessage({ type: 'source', source, bytes }, [bytes]);
  } catch (failure) {
    fail(failure instanceof RangeError ? 'memory' : 'unreadable', source);
  }
};

const onWorker = ({ data }) => {
  switch (data.type) {
    case 'need':
      readSource(data.source);
      break;
    case 'progress':
      post(data);
      break;
    case 'output':
      current = { output: data.output, bytes: data.bytes, total: Math.max(1, Math.ceil(data.bytes.length / CHUNK_BYTES)) };
      sendPiece(0);
      break;
    case 'done':
      stop();
      post({ type: 'done' });
      break;
    case 'failed':
      fail(data.reason, data.source);
      break;
    default:
  }
};

window.lunarisWork = {
  start(job) {
    stop();
    worker = new Worker('work_worker.js');
    worker.onmessage = onWorker;
    // A worker that dies of memory says nothing but this.
    worker.onerror = () => fail('memory');
    worker.postMessage({ type: 'start', job: JSON.parse(job) });
  },
  ack(output, index) {
    if (!current || current.output !== output) return;
    if (index + 1 < current.total) {
      sendPiece(index + 1);
    } else {
      post({ type: 'outputDone', output, bytes: current.bytes.length });
    }
  },
  ackOutput(output) {
    if (!current || current.output !== output) return;
    current = null;
    worker.postMessage({ type: 'next' });
  },
  cancel: stop,
};

post({ type: 'ready' });
