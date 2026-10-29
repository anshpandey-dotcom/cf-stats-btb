import * as sound from './sound.js';

const root = document.createElement('div');
root.id = 'call';
root.hidden = true;
root.innerHTML = `
  <img class="call-dp" alt="">
  <div class="call-name">Unknown</div>
  <div class="call-state">WhatsApp voice call</div>
  <div class="call-actions">
    <button class="call-btn decline" aria-label="Decline"><svg viewBox="0 0 24 24"><path d="M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08a.99.99 0 0 1-.29-.7c0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.53-.29.71l-2.48 2.48c-.18.18-.43.29-.71.29-.27 0-.52-.11-.7-.28a11.27 11.27 0 0 0-2.67-1.85.996.996 0 0 1-.56-.9v-3.1C15.15 9.25 13.6 9 12 9z"/></svg></button>
    <button class="call-btn answer" aria-label="Answer"><svg viewBox="0 0 24 24"><path d="M6.6 10.8a15 15 0 0 0 6.6 6.6l2.2-2.2a1 1 0 0 1 1-.25 11.4 11.4 0 0 0 3.6.6 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.45.6 3.6a1 1 0 0 1-.25 1z"/></svg></button>
  </div>`;
document.body.append(root);

const dp = root.querySelector('.call-dp');
const stateEl = root.querySelector('.call-state');
let stopRing = () => {};
let ticker = 0;
let ringTimeout = 0;
let vibrateTimer = 0;

function end() {
  stopRing();
  clearInterval(ticker);
  clearInterval(vibrateTimer);
  clearTimeout(ringTimeout);
  if (navigator.vibrate) navigator.vibrate(0);
  root.hidden = true;
}

export function show(opts, hooks) {
  end();
  sound.unlock();
  dp.src = hooks.dpUrl;
  stateEl.textContent = 'WhatsApp voice call';
  root.classList.remove('live');
  root.hidden = false;
  stopRing = sound.ringtone();
  const buzz = () => navigator.vibrate && navigator.vibrate([700, 500, 700]);
  buzz();
  vibrateTimer = setInterval(buzz, 2400);
  ringTimeout = setTimeout(end, 30000);

  root.querySelector('.decline').onclick = () => {
    end();
    hooks.answered('declined');
  };

  root.querySelector('.answer').onclick = () => {
    stopRing();
    clearInterval(vibrateTimer);
    clearTimeout(ringTimeout);
    if (navigator.vibrate) navigator.vibrate(0);
    hooks.answered('answered');
    root.classList.add('live');
    let seconds = 0;
    stateEl.textContent = '00:00';
    sound.whisper(8);
    ticker = setInterval(() => {
      seconds++;
      stateEl.textContent = `00:${String(seconds).padStart(2, '0')}`;
      if (seconds === 6) {
        if (opts.scare) hooks.scare();
        else sound.knock();
      }
      if (seconds === 9) end();
    }, 1000);
  };
}
