const demoCases = [
  {
    question: "该医疗保险的牙科报销上限是多少？是否包含根管治疗？",
    intents: ["报销额度", "牙科保障范围"],
    expectedIds: ["med_dental_limit"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  },
  {
    question: "高可用模式的心跳间隔是多少秒？",
    intents: ["高可用参数", "心跳间隔"],
    expectedIds: ["tech_ha_heartbeat"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  },
  {
    question: "负载均衡需要配置哪些参数？",
    intents: ["负载均衡配置"],
    expectedIds: ["tech_lb_config"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  },
  {
    question: "云服务器支持哪些操作系统？有哪些计费方式？",
    intents: ["操作系统支持", "计费方式"],
    expectedIds: ["cloud_os_support", "cloud_pricing"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  },
  {
    question: "API 调用的频率限制是多少？如何申请提升配额？",
    intents: ["API限制", "配额管理"],
    expectedIds: ["api_rate_limit", "api_quota"],
    filters: {},
    labelStatus: "HUMAN_VERIFIED"
  }
];

// 首次访问引导
const ONBOARDING_KEY = "recallmaster_onboarding_shown";
function initOnboarding() {
  const card = document.getElementById("onboarding-card");
  const closeBtn = document.getElementById("closeOnboarding");
  if (!card) return;
  if (!localStorage.getItem(ONBOARDING_KEY)) {
    card.style.display = "flex";
  }
  closeBtn.addEventListener("click", () => {
    card.style.display = "none";
    localStorage.setItem(ONBOARDING_KEY, "true");
  });
  card.addEventListener("click", (e) => {
    if (e.target === card) {
      card.style.display = "none";
      localStorage.setItem(ONBOARDING_KEY, "true");
    }
  });
}

// Demo 警告提示
function showDemoWarning() {
  const runsEl = document.querySelector("#runs");
  const existingWarning = runsEl.querySelector(".demo-warning");
  if (existingWarning) return;

  const warning = document.createElement("div");
  warning.className = "demo-warning";
  warning.innerHTML = `
    <span style="font-size: 20px;">⚠️</span>
    <div>
      <strong>Demo 模式说明</strong>
      <p>当前使用 Hash Embedding（无语义理解能力），仅适合功能验证。</p>
      <p>正式评测请配置真实 Embedding 模型（如 OpenAI、BGE、Ollama）。</p>
    </div>
  `;
  runsEl.parentElement.insertBefore(warning, runsEl);
}

function renderCaseDetail(result) {
  const detail = document.querySelector("#caseDetail");
  if (!result || !result.caseInfo) {
    detail.replaceChildren();
    return;
  }
  const ci = result.caseInfo;
  const rm = result.retrievalMetrics ?? {};
  const ai = result.aiAnalysis ?? {};

  const header = document.createElement("div");
  header.className = "detail-header";
  const badge = document.createElement("span");
  badge.className = `status-badge ${result.status}`;
  badge.textContent = result.status;
  const q = document.createElement("strong");
  q.textContent = ci.question;
  header.appendChild(badge);
  header.appendChild(q);
  detail.replaceChildren(header);

  const grid = document.createElement("div");
  grid.className = "detail-grid";

  // Recall card
  const recallCard = document.createElement("div");
  recallCard.className = "detail-card";
  recallCard.appendChild(makeH4("召回指标", "评估向量数据库的检索质量"));
  recallCard.appendChild(makeDL([
    ["Recall@K", formatPct(rm.recallRate)],
    ["Hit@K", rm.hitRate != null ? formatPct(rm.hitRate) : "N/A"],
    ["Precision@K", rm.precisionAtK != null ? formatPct(rm.precisionAtK) : "N/A"],
    ["MRR", rm.mrr != null ? rm.mrr.toFixed(3) : "N/A"],
    ["nDCG", rm.ndcg != null ? rm.ndcg.toFixed(3) : "N/A"]
  ]));
  grid.appendChild(recallCard);

  // Intent card
  const intentCard = document.createElement("div");
  intentCard.className = "detail-card";
  intentCard.appendChild(makeH4("意图分析", "Judge 分析召回内容的意图覆盖和噪声比例"));
  intentCard.appendChild(makeDL([
    ["意图覆盖", formatPct(ai.intentCoverage)],
    ["噪音比", formatPct(ai.noiseRatio)],
    ["需人工审核", ai.needsHumanReview ? "是" : "否"]
  ]));
  if (ai.summary) {
    const p = document.createElement("p");
    p.className = "detail-summary";
    p.textContent = ai.summary;
    intentCard.appendChild(p);
  }
  grid.appendChild(intentCard);

  // ID card
  const idCard = document.createElement("div");
  idCard.className = "detail-card";
  idCard.appendChild(makeH4("预期 vs 实际", "对比期望召回的文档 ID 和实际召回结果"));
  const idList = document.createElement("div");
  idList.className = "id-list";
  idList.appendChild(makeIdRow("预期 ID:", ci.expectedIds ?? []));
  idList.appendChild(makeIdRow("子意图:", ci.intents ?? []));
  idCard.appendChild(idList);
  grid.appendChild(idCard);

  detail.appendChild(grid);

  // 行动指引
  detail.appendChild(makeActionAdvice(result));
}

function makeH4(text, helpText) {
  const h4 = document.createElement("h4");
  h4.textContent = text;
  if (helpText) {
    h4.className = "metric-help";
    h4.textContent = text + " ❓";
    const tooltip = document.createElement("span");
    tooltip.className = "tooltip";
    tooltip.textContent = helpText;
    h4.appendChild(tooltip);
  }
  return h4;
}

function makeDL(items) {
  const dl = document.createElement("dl");
  for (const [dtText, ddText] of items) {
    const dt = document.createElement("dt");
    dt.textContent = dtText;
    const dd = document.createElement("dd");
    dd.textContent = ddText;
    dl.appendChild(dt);
    dl.appendChild(dd);
  }
  return dl;
}

function makeIdRow(label, ids) {
  const row = document.createElement("div");
  const span = document.createElement("span");
  span.className = "label";
  span.textContent = label;
  row.appendChild(span);
  for (const id of ids) {
    const code = document.createElement("code");
    code.textContent = id;
    row.appendChild(code);
  }
  return row;
}

// 评测结果行动指引
function makeActionAdvice(result) {
  const advice = document.createElement("div");
  advice.className = "action-advice";

  const rm = result.retrievalMetrics ?? {};
  const recall = rm.recallRate ?? 0;
  const intentCoverage = result.aiAnalysis?.intentCoverage ?? 0;
  const noiseRatio = result.aiAnalysis?.noiseRatio ?? 0;

  const suggestions = [];

  if (recall < 0.8) {
    suggestions.push(`<li><strong>Recall@K 过低 (${Math.round(recall * 100)}%)</strong>：尝试增大 topK，或检查 chunk size 是否合适</li>`);
  }
  if (intentCoverage < 0.7) {
    suggestions.push(`<li><strong>意图覆盖不足 (${Math.round(intentCoverage * 100)}%)</strong>：检查召回内容是否包含完整意图，可尝试改善 chunk 策略</li>`);
  }
  if (noiseRatio > 0.3) {
    suggestions.push(`<li><strong>噪声比例过高 (${Math.round(noiseRatio * 100)}%)</strong>：召回了无关内容，可考虑增加 rerank 或使用更精确的 embedding</li>`);
  }
  if (suggestions.length === 0) {
    suggestions.push("<li>评测结果良好，无需特殊优化</li>");
  }

  advice.innerHTML = `
    <h4>💡 优化建议</h4>
    <ul>${suggestions.join("")}</ul>
  `;
  return advice;
}

function makeStat(label, value) {
  const span = document.createElement("span");
  span.textContent = `${label} `;
  const strong = document.createElement("strong");
  strong.textContent = value;
  span.appendChild(strong);
  return span;
}

function escHtml(str) {
  const div = document.createElement("div");
  div.textContent = str;
  return div.innerHTML;
}

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
const loadExampleCases = document.querySelector("#loadExampleCases");
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

runDemo.addEventListener("click", withLoading(runDemo, async () => {
  const response = await fetch("/api/runs", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({database: "demo-memory", topK: 5, cases: demoCases})
  });
  const run = await response.json();
  await loadRuns();
  watchRun(run.id);
  showDemoWarning();
}));

refresh.addEventListener("click", loadRuns);
healthCheck.addEventListener("click", withLoading(healthCheck, loadConnectorHealth));
compareLatest.addEventListener("click", withLoading(compareLatest, async () => {
  const runs = await fetchJson("/api/runs");
  if (runs.length < 2) {
    compareResult.textContent = "至少需要两次运行结果。";
    return;
  }
  await compare(runs[1].id, runs[0].id);
}));

compareRuns.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(compareRuns);
  await compare(form.get("baselineId"), form.get("candidateId"));
});

loadExampleCases.addEventListener("click", withLoading(loadExampleCases, async () => {
  try {
    const response = await fetch("/api/runs/examples");
    if (!response.ok) throw new Error("加载失败");
    const cases = await response.json();
    importResult.textContent = `已加载 ${cases.length} 个示例 Cases，可以复制到下方"新建评测"的 Case JSON 中使用。`;
    document.querySelector('textarea[name="caseJson"]').value = JSON.stringify(cases, null, 2);
  } catch (e) {
    importResult.textContent = "加载示例失败: " + e.message;
  }
}));

importCases.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(importCases);
  const file = form.get("file");
  let payload = form.get("caseJson");
  let filename = "data.json";
  if (file && typeof file !== "string" && file.size > 0) {
    payload = await file.text();
    filename = file.name;
  }
  const response = await fetch("/api/cases/import", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Filename": filename
    },
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
  runsEl.replaceChildren();
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
  const headLeft = document.createElement("div");
  const dbName = document.createElement("strong");
  dbName.textContent = run.database;
  const runId = document.createElement("small");
  runId.textContent = run.id;
  headLeft.appendChild(dbName);
  headLeft.appendChild(runId);
  const headRight = document.createElement("span");
  headRight.textContent = `${run.status} · ${run.completed}/${run.total} · TopK ${run.topK}`;
  head.appendChild(headLeft);
  head.appendChild(headRight);
  article.appendChild(head);

  const metrics = summarize(run);
  const stats = document.createElement("div");
  stats.className = "stat-grid";
  stats.appendChild(makeStat("Avg Recall", formatPct(metrics.avgRecall)));
  stats.appendChild(makeStat("Intent", formatPct(metrics.avgCoverage)));
  stats.appendChild(makeStat("Noise", formatPct(metrics.avgNoise)));
  stats.appendChild(makeStat("Review", metrics.review));
  article.appendChild(stats);

  const matrix = document.createElement("div");
  matrix.className = "matrix";
  for (const result of run.results ?? []) {
    const cell = document.createElement("button");
    cell.className = `cell ${result.status}`;
    cell.title = `${result.caseInfo.question}\n${result.aiAnalysis.summary}`;
    cell.addEventListener("click", () => {
      singleResult.textContent = JSON.stringify(result, null, 2);
      renderCaseDetail(result);
    });
    matrix.appendChild(cell);
  }
  article.appendChild(matrix);

  const links = document.createElement("div");
  links.className = "run-actions";
  const csvLink = document.createElement("a");
  csvLink.href = `/api/runs/${encodeURIComponent(run.id)}/report.csv`;
  csvLink.textContent = "CSV 报告";
  const caseLink = document.createElement("a");
  caseLink.href = `/api/runs/${encodeURIComponent(run.id)}/cases.json`;
  caseLink.textContent = "导出 Case";
  const jsonBtn = document.createElement("button");
  jsonBtn.className = "secondary";
  jsonBtn.textContent = "查看 JSON";
  jsonBtn.addEventListener("click", () => {
    singleResult.textContent = JSON.stringify(run, null, 2);
    renderCaseDetail(null);
  });
  links.appendChild(csvLink);
  links.appendChild(caseLink);
  links.appendChild(jsonBtn);
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
  connectorsEl.replaceChildren();
  // Populate new-run database selector
  newRunDatabase.replaceChildren();
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
  const nameEl = document.createElement("strong");
  nameEl.textContent = connector.name;
  const typeEl = document.createElement("span");
  typeEl.textContent = `${connector.type} · ${connector.available ? "可用" : "需补配置"}`;
  const hintEl = document.createElement("small");
  hintEl.textContent = connector.hints?.required ?? "";
  const codeEl = document.createElement("code");
  codeEl.textContent = connector.config?.table !== "-" ? connector.config.table
    : connector.config?.collection !== "-" ? connector.config.collection
    : connector.config?.index ?? "";
  card.appendChild(nameEl);
  card.appendChild(typeEl);
  card.appendChild(hintEl);
  card.appendChild(codeEl);
  return card;
}

async function compare(baselineId, candidateId) {
  if (!baselineId || !candidateId) {
    compareResult.textContent = "请填写两个 Run ID。";
    return;
  }
  const comparison = await fetchJson(`/api/runs/${baselineId}/compare/${candidateId}`);
  compareResult.textContent = JSON.stringify(comparison, null, 2);
  const detail = document.querySelector("#compareDetail");
  detail.replaceChildren();
  const metrics = comparison.metricDeltas ?? comparison;
  if (Array.isArray(metrics)) {
    for (const m of metrics) {
      const row = document.createElement("div");
      row.className = "compare-row";
      const delta = m.delta ?? 0;
      const cls = delta > 0 ? "positive" : delta < 0 ? "negative" : "neutral";
      const span1 = document.createElement("span");
      span1.textContent = m.question ?? m.metric ?? "";
      const span2 = document.createElement("span");
      span2.className = cls;
      span2.textContent = `${delta > 0 ? "+" : ""}${(delta * 100).toFixed(1)}%`;
      row.appendChild(span1);
      row.appendChild(span2);
      detail.appendChild(row);
    }
  }
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
    const text = await response.text();
    throw new Error(text);
  }
  return response.json();
}

function withLoading(button, fn) {
  return async (...args) => {
    const orig = button.textContent;
    button.disabled = true;
    button.textContent = "加载中...";
    try {
      await fn(...args);
    } catch (e) {
      showToast(e.message, "error");
    } finally {
      button.disabled = false;
      button.textContent = orig;
    }
  };
}

function showToast(message, type = "info") {
  const existing = document.querySelector(".toast");
  if (existing) existing.remove();
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message.length > 200 ? message.substring(0, 200) + "..." : message;
  document.body.appendChild(toast);
  setTimeout(() => toast.remove(), 5000);
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
initOnboarding();
