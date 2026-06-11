const demoCases = [
  {
    question: "该医疗保险的牙科报销上限是多少？是否包含根管治疗？",
    intents: ["报销额度", "牙科保障范围"],
    expectedIds: ["med_dental_limit"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  },
  {
    question: "如何配置负载均衡？高可用模式下心跳间隔是多少？",
    intents: ["负载均衡配置", "高可用参数"],
    expectedIds: ["tech_lb_config", "tech_ha_heartbeat"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  }
];

const runsEl = document.querySelector("#runs");
const connectorsEl = document.querySelector("#connectors");
const refresh = document.querySelector("#refresh");
const runDemo = document.querySelector("#runDemo");
const healthCheck = document.querySelector("#healthCheck");
const compareLatest = document.querySelector("#compareLatest");
const compareRuns = document.querySelector("#compareRuns");
const compareResult = document.querySelector("#compareResult");
const importCases = document.querySelector("#importCases");
const importResult = document.querySelector("#importResult");
const singleCase = document.querySelector("#singleCase");
const singleResult = document.querySelector("#singleResult");

const newRun = document.querySelector("#newRun");
const newRunResult = document.querySelector("#newRunResult");
const newRunDatabase = document.querySelector("#newRunDatabase");
const startNewRunBtn = document.querySelector("#startNewRun");

newRun.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(newRun);
  let cases;
  try {
    cases = JSON.parse(form.get("cases"));
    if (!Array.isArray(cases) || cases.length === 0) {
      throw new Error("cases must be a non-empty array");
    }
  } catch (e) {
    newRunResult.textContent = "Case JSON 格式错误: " + e.message;
    return;
  }
  startNewRunBtn.disabled = true;
  startNewRunBtn.textContent = "评测中...";
  try {
    const response = await fetch("/api/runs", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        database: form.get("database"),
        topK: Number(form.get("topK")),
        cases
      })
    });
    if (!response.ok) {
      const err = await response.text();
      newRunResult.textContent = "创建失败: " + err;
      return;
    }
    const run = await response.json();
    newRunResult.textContent = "评测已创建: " + run.id;
    await loadRuns();
    watchRun(run.id);
  } catch (e) {
    newRunResult.textContent = "请求失败: " + e.message;
  } finally {
    startNewRunBtn.disabled = false;
    startNewRunBtn.textContent = "开始评测";
  }
});

runDemo.addEventListener("click", async () => {
  const response = await fetch("/api/runs", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({database: "demo-memory", topK: 5, cases: demoCases})
  });
  const run = await response.json();
  await loadRuns();
  watchRun(run.id);
});

refresh.addEventListener("click", loadRuns);
healthCheck.addEventListener("click", loadConnectorHealth);
compareLatest.addEventListener("click", async () => {
  const runs = await fetchJson("/api/runs");
  if (runs.length < 2) {
    compareResult.textContent = "至少需要两次运行结果。";
    return;
  }
  await compare(runs[1].id, runs[0].id);
});

compareRuns.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(compareRuns);
  await compare(form.get("baselineId"), form.get("candidateId"));
});

importCases.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(importCases);
  const file = form.get("file");
  let payload = form.get("caseJson");
  if (file && typeof file !== "string" && file.size > 0) {
    payload = await file.text();
  }
  const response = await fetch("/api/cases/import", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: payload
  });
  if (!response.ok) {
    importResult.textContent = await response.text();
    return;
  }
  const data = await response.json();
  importResult.textContent = JSON.stringify(data, null, 2);
});

singleCase.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(singleCase);
  const caseInfo = {
    question: form.get("question"),
    intents: splitCsv(form.get("intents")),
    expectedIds: splitCsv(form.get("expectedIds")),
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  };
  const response = await fetch("/api/runs", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({
      database: form.get("database"),
      topK: Number(form.get("topK")),
      cases: [caseInfo]
    })
  });
  const run = await response.json();
  singleResult.textContent = JSON.stringify(run, null, 2);
  watchRun(run.id, true);
});

async function loadRuns() {
  const runs = await fetchJson("/api/runs");
  runsEl.innerHTML = "";
  for (const run of runs) {
    runsEl.appendChild(renderRun(run));
  }
}

async function watchRun(id, writeSingle = false) {
  const timer = setInterval(async () => {
    const run = await fetchJson(`/api/runs/${id}`);
    await loadRuns();
    if (writeSingle) {
      singleResult.textContent = JSON.stringify(run.results?.[0] ?? run, null, 2);
    }
    if (run.status === "COMPLETED" || run.status === "COMPLETED_WITH_ERRORS" || run.status === "FAILED") {
      clearInterval(timer);
    }
  }, 900);
}

function renderRun(run) {
  const article = document.createElement("article");
  article.className = "run";
  const head = document.createElement("div");
  head.className = "run-head";
  head.innerHTML = `<div><strong>${run.database}</strong><small>${run.id}</small></div><span>${run.status} · ${run.completed}/${run.total} · TopK ${run.topK}</span>`;
  article.appendChild(head);

  const metrics = summarize(run);
  const stats = document.createElement("div");
  stats.className = "stat-grid";
  stats.innerHTML = `
    <span>Avg Recall <strong>${formatPct(metrics.avgRecall)}</strong></span>
    <span>Intent <strong>${formatPct(metrics.avgCoverage)}</strong></span>
    <span>Noise <strong>${formatPct(metrics.avgNoise)}</strong></span>
    <span>Review <strong>${metrics.review}</strong></span>
  `;
  article.appendChild(stats);

  const matrix = document.createElement("div");
  matrix.className = "matrix";
  for (const result of run.results ?? []) {
    const cell = document.createElement("button");
    cell.className = `cell ${result.status}`;
    cell.title = `${result.caseInfo.question}\n${result.aiAnalysis.summary}`;
    cell.addEventListener("click", () => {
      singleResult.textContent = JSON.stringify(result, null, 2);
    });
    matrix.appendChild(cell);
  }
  article.appendChild(matrix);

  const links = document.createElement("div");
  links.className = "run-actions";
  links.innerHTML = `
    <a href="/api/runs/${run.id}/report.csv">CSV 报告</a>
    <a href="/api/runs/${run.id}/cases.json">导出 Case</a>
    <button class="secondary" data-run-id="${run.id}">查看 JSON</button>
  `;
  links.querySelector("button").addEventListener("click", () => {
    singleResult.textContent = JSON.stringify(run, null, 2);
  });
  article.appendChild(links);

  const last = run.results?.[run.results.length - 1];
  if (last) {
    const summary = document.createElement("div");
    summary.className = "summary";
    summary.textContent = `Live: ${last.caseInfo.question} | ${last.aiAnalysis.summary}`;
    article.appendChild(summary);
  }
  if (run.error) {
    const error = document.createElement("div");
    error.className = "summary";
    error.textContent = run.error;
    article.appendChild(error);
  }
  return article;
}

async function loadConnectors() {
  const connectors = await fetchJson("/api/connectors");
  connectorsEl.innerHTML = "";
  // Populate new-run database selector
  newRunDatabase.innerHTML = "";
  for (const connector of connectors) {
    const option = document.createElement("option");
    option.value = connector.name;
    option.textContent = `${connector.name} (${connector.type})`;
    newRunDatabase.appendChild(option);
  }
  for (const connector of connectors) {
    connectorsEl.appendChild(renderConnector(connector));
  }
  connectorsEl.querySelectorAll(".connector").forEach(card => {
    card.addEventListener("click", async () => {
      const health = await fetchJson(`/api/connectors/${card.dataset.name}/health`, {method: "POST"});
      card.querySelector("small").textContent = `${health.ok ? "健康" : "异常"} · ${health.latencyMs}ms · ${health.message}`;
      card.classList.toggle("bad", !health.ok);
    });
  });
}

async function loadConnectorHealth() {
  const health = await fetchJson("/api/connectors/health", {method: "POST"});
  const byName = new Map(health.map(item => [item.name, item]));
  for (const card of connectorsEl.querySelectorAll(".connector")) {
    const item = byName.get(card.dataset.name);
    if (item) {
      card.querySelector("small").textContent = `${item.ok ? "健康" : "异常"} · ${item.latencyMs}ms · ${item.message}`;
      card.classList.toggle("bad", !item.ok);
    }
  }
}

function renderConnector(connector) {
  const card = document.createElement("article");
  card.className = "connector";
  card.dataset.name = connector.name;
  card.innerHTML = `
    <strong>${connector.name}</strong>
    <span>${connector.type} · ${connector.available ? "可用" : "需补配置"}</span>
    <small>${connector.hints?.required ?? ""}</small>
    <code>${connector.config?.table !== "-" ? connector.config.table : connector.config?.collection !== "-" ? connector.config.collection : connector.config?.index ?? ""}</code>
  `;
  return card;
}

async function compare(baselineId, candidateId) {
  if (!baselineId || !candidateId) {
    compareResult.textContent = "请填写两个 Run ID。";
    return;
  }
  const comparison = await fetchJson(`/api/runs/${baselineId}/compare/${candidateId}`);
  compareResult.textContent = JSON.stringify(comparison, null, 2);
}

function summarize(run) {
  const results = run.results ?? [];
  const denominator = Math.max(1, results.length);
  return {
    avgRecall: results.reduce((sum, item) => sum + item.retrievalMetrics.recallRate, 0) / denominator,
    avgCoverage: results.reduce((sum, item) => sum + item.aiAnalysis.intentCoverage, 0) / denominator,
    avgNoise: results.reduce((sum, item) => sum + item.aiAnalysis.noiseRatio, 0) / denominator,
    review: results.filter(item => item.aiAnalysis.needsHumanReview).length
  };
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

function formatPct(value) {
  return `${Math.round((value || 0) * 100)}%`;
}

function splitCsv(value) {
  return String(value ?? "")
    .split(",")
    .map(item => item.trim())
    .filter(Boolean);
}

loadConnectors();
loadRuns();
