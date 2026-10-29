import * as db from './db.js';
import { EFFECTS } from './scare.js';
import { compressImage, uid } from './media.js';

const $ = (id) => document.getElementById(id);

const DEFAULTS = [
  { name: 'Ghost', keyword: 'bhoot', effect: 'face', target: 'guest', duration: 3, delay: 0, flash: true, shake: true, vibrate: true },
  { name: 'Darkness', keyword: 'andhera', effect: 'eyes', target: 'both', duration: 5, delay: 1, flash: false, shake: false, vibrate: true },
  { name: 'Glitch', keyword: 'peeche dekh', effect: 'glitch', target: 'guest', duration: 3, delay: 0, flash: true, shake: true, vibrate: true }
];

const STORY_HELP = 'One message per line. Put a delay in seconds before the pipe: 3|Are you there?\nStart with ! for blood text, ~ for a whisper. Fire a saved scare with: 4|@scare Ghost';

export function initPanel(api) {
  let editing = null;

  function readFile(file, isImage) {
    return new Promise(async (resolve, reject) => {
      try {
        if (isImage) {
          const blob = await compressImage(file, 1600, 0.9);
          resolve({ mime: blob.type, data: await blob.arrayBuffer(), name: file.name });
        } else {
          resolve({ mime: file.type || 'audio/mpeg', data: await file.arrayBuffer(), name: file.name });
        }
      } catch (e) {
        reject(e);
      }
    });
  }

  function el(tag, props = {}, ...children) {
    const node = Object.assign(document.createElement(tag), props);
    children.forEach((c) => node.append(c));
    return node;
  }

  function button(label, onClick, cls = '') {
    const b = el('button', { textContent: label, className: cls });
    b.addEventListener('click', onClick);
    return b;
  }

  async function renderScares() {
    const pane = $('tab-scares');
    pane.innerHTML = '';
    if (editing) return pane.append(buildForm(editing));
    const list = await db.triggers.all();
    pane.append(button('New scare', () => {
      editing = { id: uid(), name: '', keyword: '', effect: 'face', target: 'guest', duration: 3, delay: 0, flash: true, shake: true, vibrate: true, enabled: true, image: null, sound: null };
      renderScares();
    }, 'btn primary'));
    pane.append(el('p', { className: 'hint', textContent: 'When a chat message contains a keyword, the scare plays on the chosen phone. You can also fire any scare by hand.' }));
    list.forEach((t) => {
      const card = el('div', { className: 'card' });
      const title = el('h3');
      title.append(el('b', { textContent: t.name || 'Untitled' }), el('span', { textContent: `${t.keyword || 'manual'} → ${t.target}` }));
      const actions = el('div', { className: 'actions' });
      actions.append(
        button('Fire on guest', () => api.fireTrigger({ ...t, delay: 0 }, 'guest')),
        button('Test here', () => api.playLocal(t)),
        button('Edit', () => {
          editing = t;
          renderScares();
        }),
        button(t.enabled === false ? 'Enable' : 'Pause', async () => {
          t.enabled = t.enabled === false;
          await db.triggers.put(t);
          api.syncTriggers();
          renderScares();
        })
      );
      card.append(title, actions);
      pane.append(card);
    });
  }

  function buildForm(t) {
    const form = el('div', { className: 'stack' });
    const name = el('input', { value: t.name, placeholder: 'Name, e.g. Ghost' });
    const keyword = el('input', { value: t.keyword, placeholder: 'Keywords, comma separated' });
    const effect = el('select');
    Object.entries(EFFECTS).forEach(([k, v]) => effect.append(el('option', { value: k, textContent: v, selected: t.effect === k })));
    const target = el('select');
    [['guest', 'Guest phone'], ['host', 'This phone'], ['both', 'Both phones']].forEach(([k, v]) => target.append(el('option', { value: k, textContent: v, selected: t.target === k })));
    const duration = el('input', { type: 'number', min: 1, max: 20, value: t.duration });
    const delay = el('input', { type: 'number', min: 0, max: 60, value: t.delay });

    const imageBtn = el('label', { className: `file-btn${t.image ? ' has' : ''}`, textContent: t.image ? `Image: ${t.image.name || 'chosen'}` : 'Pick scare image' });
    const imageIn = el('input', { type: 'file', accept: 'image/*', hidden: true });
    imageIn.addEventListener('change', async () => {
      if (!imageIn.files[0]) return;
      t.image = await readFile(imageIn.files[0], true);
      imageBtn.textContent = `Image: ${t.image.name}`;
      imageBtn.classList.add('has');
    });
    imageBtn.append(imageIn);

    const soundBtn = el('label', { className: `file-btn${t.sound ? ' has' : ''}`, textContent: t.sound ? `Sound: ${t.sound.name || 'chosen'}` : 'Pick scare sound' });
    const soundIn = el('input', { type: 'file', accept: 'audio/*', hidden: true });
    soundIn.addEventListener('change', async () => {
      if (!soundIn.files[0]) return;
      t.sound = await readFile(soundIn.files[0], false);
      soundBtn.textContent = `Sound: ${t.sound.name}`;
      soundBtn.classList.add('has');
    });
    soundBtn.append(soundIn);

    const checks = el('div', { className: 'checks' });
    const flags = {};
    [['flash', 'Strobe'], ['shake', 'Shake'], ['vibrate', 'Vibrate']].forEach(([k, label]) => {
      const box = el('input', { type: 'checkbox', checked: !!t[k] });
      flags[k] = box;
      checks.append(el('label', {}, box, document.createTextNode(label)));
    });

    const field = (label, node) => el('div', {}, el('label', { className: 'f', textContent: label }), node);
    const two = el('div', { className: 'field-row' });
    two.append(field('Show for (sec)', duration), field('Delay (sec)', delay));

    const save = button('Save', async () => {
      Object.assign(t, {
        name: name.value.trim() || 'Untitled',
        keyword: keyword.value.trim(),
        effect: effect.value,
        target: target.value,
        duration: Number(duration.value) || 3,
        delay: Number(delay.value) || 0,
        flash: flags.flash.checked,
        shake: flags.shake.checked,
        vibrate: flags.vibrate.checked
      });
      await db.triggers.put(t);
      editing = null;
      api.syncTriggers();
      renderScares();
    }, 'btn primary');
    const cancel = button('Cancel', () => {
      editing = null;
      renderScares();
    }, 'btn');
    const remove = button('Delete', async () => {
      await db.triggers.remove(t.id);
      editing = null;
      api.syncTriggers();
      renderScares();
    }, 'btn danger');

    form.append(field('Name', name), field('Keywords (typed in chat)', keyword), field('Effect', effect), imageBtn, soundBtn, field('Plays on', target), two, checks);
    const row = el('div', { className: 'row' });
    row.append(cancel, remove, save);
    form.append(row);
    return form;
  }

  async function renderStory() {
    const pane = $('tab-story');
    if (pane.dataset.ready) return;
    pane.dataset.ready = '1';
    const saved = (await db.meta.get('story')) || '3|Hello?\n4|Are you still awake?\n5|!Don\'t turn around\n3|@scare Ghost';
    const area = el('textarea', { className: 'plain', value: saved });
    area.addEventListener('input', () => db.meta.set('story', area.value));
    const row = el('div', { className: 'row' });
    row.append(
      button('Stop', () => api.stopStory(), 'btn danger'),
      button('Play on stream', () => {
        $('controlSheet').hidden = true;
        api.runStory(area.value);
      }, 'btn primary')
    );
    pane.append(el('p', { className: 'hint', textContent: STORY_HELP, style: 'white-space:pre-line' }), area, row);
  }

  function renderTools() {
    const pane = $('tab-tools');
    pane.innerHTML = '';
    const group = (title, ...nodes) => {
      const card = el('div', { className: 'card' });
      card.append(el('h3', {}, el('b', { textContent: title })));
      const actions = el('div', { className: 'actions' });
      nodes.forEach((n) => actions.append(n));
      card.append(actions);
      return card;
    };
    pane.append(
      group('Guest phone',
        button('Typing 4s', () => api.sendFx('typing', 4)),
        button('Typing 10s', () => api.sendFx('typing', 10)),
        button('Flicker', () => api.sendFx('flicker')),
        button('Shake', () => api.sendFx('shake', 2)),
        button('Blackout 5s', () => api.sendFx('blackout', 5)),
        button('Blackout 12s', () => api.sendFx('blackout', 12))
      ),
      group('This phone',
        button('Flicker', () => api.applyFx('flicker')),
        button('Blackout 5s', () => api.applyFx('blackout', 5))
      )
    );
    const ambient = el('input', { type: 'checkbox', checked: api.isAmbient() });
    ambient.addEventListener('change', () => {
      api.setAmbient(ambient.checked);
      api.sendFx('ambient', ambient.checked);
    });
    const ambientCard = el('div', { className: 'card' });
    ambientCard.append(el('div', { className: 'checks' }, el('label', {}, ambient, document.createTextNode('Ambient creep (random flickers on both phones)'))));
    pane.append(ambientCard, group('Chat', button('Export text', () => api.exportChat()), button('Switch role', () => api.switchRole())));
  }

  document.querySelectorAll('.tabs button').forEach((b) =>
    b.addEventListener('click', () => {
      document.querySelectorAll('.tabs button').forEach((x) => x.classList.toggle('on', x === b));
      document.querySelectorAll('.tabpane').forEach((p) => (p.hidden = p.id !== `tab-${b.dataset.tab}`));
      refresh();
    })
  );

  function refresh() {
    renderScares();
    renderStory();
    renderTools();
  }

  async function seedDefaults() {
    if (await db.meta.get('seeded')) return;
    for (const d of DEFAULTS) await db.triggers.put({ id: uid(), enabled: true, image: null, sound: null, ...d });
    await db.meta.set('seeded', true);
  }

  return { refresh, seedDefaults, setRole() {} };
}
