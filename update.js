const fs = require('fs');

const PROJECT_IDS = [1467882,1477390,1466474,1467149,1466988,1513084,1520494,1523424,1531337,1533392,1536096,1537001,1557359,1557372,1559473,1562507,1575268,1466267,1539449,1539446,1595371,1567434,1470746,1577770,1597582,1575082,1540947,1467899,1481724,1522400,1467259,1613658,1615010,1540112,1466129,1563781,1620356,1607069,1620685,1620547,1616604,1545661,1548535,1563827,1552205,1559273,1550318,1551098,1613696,1607071,1536985,1598590,1566315,1567455,1621859,1624794,1547559,1541444,1538025,1620638];

const DATA_FILE = 'data.json';
const HISTORY_RETENTION_MS = 35 * 24 * 60 * 60 * 1000;

async function fetchProject(id) {
  try {
    const res = await fetch(`https://api.cfwidget.com/${id}`);
    if (!res.ok) return null;
    return await res.json();
  } catch (err) {
    return null;
  }
}

function loadData() {
  if (fs.existsSync(DATA_FILE)) {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  }
  return { updatedAt: 0, projects: {} };
}

async function main() {
  const store = loadData();
  const now = Date.now();

  for (const id of PROJECT_IDS) {
    const idStr = String(id);
    const apiData = await fetchProject(id);

    if (!apiData || apiData.error) {
      if (!store.projects[idStr]) {
        store.projects[idStr] = { title: null, thumbnail: null, total: null, files: [], history: [], rawDaily: 0, rawWeekly: 0, error: true };
      } else {
        store.projects[idStr].error = true;
      }
      continue;
    }

    const total = apiData.downloads?.total || 0;
    const files = (apiData.files || []).slice(0, 15).map(f => ({ date: f.date }));

    if (!store.projects[idStr]) {
      store.projects[idStr] = { title: apiData.title || idStr, thumbnail: apiData.thumbnail || '', total, files, history: [], rawDaily: 0, rawWeekly: 0, error: false };
    }

    const entry = store.projects[idStr];
    entry.title = apiData.title || entry.title;
    entry.thumbnail = apiData.thumbnail || entry.thumbnail;
    entry.total = total;
    entry.files = files;
    entry.rawDaily = apiData.downloads?.daily || 0;
    entry.rawWeekly = apiData.downloads?.weekly || 0;
    entry.error = false;

    entry.history.push({ t: now, val: total });
    entry.history = entry.history.filter(h => h.t > now - HISTORY_RETENTION_MS);
  }

  store.updatedAt = now;
  fs.writeFileSync(DATA_FILE, JSON.stringify(store));
}

main();
