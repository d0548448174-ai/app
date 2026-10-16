(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);
  const state = {
    worker: null, ready: false, archiveName: "", articleCount: 0,
    mode: "browse", activeQueries: [""], browseLabel: "כל הערכים",
    queryOffset: 0, total: 0, results: [], selectedPath: "",
    selectedTitle: "", pageBusy: false, searchSequence: 0,
    resourceUrls: new Map(), toastTimer: null, hasMore: false
  };
  const hebrewLetters = Array.from("אבגדהוזחטיכלמנסעפצקרשת");
  const latinLetters = Array.from("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
  const pageSize = 80;
  const collator = new Intl.Collator(["he", "en"], { numeric: true, sensitivity: "base" });
  const requestTimeout = 180000;

  function showToast(message) {
    const toast = $("toast");
    toast.textContent = message;
    toast.classList.add("show");
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => toast.classList.remove("show"), 3600);
  }

  function busy(show, title, detail) {
    $("busyOverlay").hidden = !show;
    if (title) $("busyTitle").textContent = title;
    if (detail) $("busyText").textContent = detail;
  }

  function status(message, isError) {
    $("welcomeStatus").textContent = message || "";
    $("welcomeStatus").style.color = isError ? "#a04a37" : "";
  }

  function setActiveNav() {
    $("browseAllBtn").classList.toggle("is-active", state.mode === "browse" && state.activeQueries.length === 1 && state.activeQueries[0] === "");
    $("searchTitlesBtn").classList.toggle("is-active", state.mode === "titles");
    $("searchTextBtn").classList.toggle("is-active", state.mode === "text");
    document.querySelectorAll(".alphabet-button").forEach((button) => {
      button.classList.toggle("is-active", state.mode === "browse" && button.dataset.queries === state.activeQueries.join("|"));
    });
  }

  function buildAlphabet(targetId, letters) {
    const target = $(targetId);
    target.replaceChildren();
    letters.forEach((letter) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "alphabet-button";
      button.textContent = letter;
      button.dataset.queries = letter;
      button.setAttribute("aria-label", "עיון באות " + letter);
      button.addEventListener("click", () => browsePrefix(letter, "האות " + letter, [letter]));
      target.appendChild(button);
    });
  }

  function activateWorkspace() {
    $("welcomeView").hidden = true;
    $("workspaceView").hidden = false;
  }

  function showPageHeading(title, subtitle, label) {
    $("pageTitle").textContent = title;
    $("pageSubtitle").textContent = subtitle;
    $("breadcrumbLabel").textContent = label || title;
    $("activeBrowseLabel").textContent = label || title;
    $("resultsHeading").textContent = title;
  }

  function request(action, payload, timeoutMs) {
    return new Promise((resolve, reject) => {
      if (!state.worker) {
        reject(new Error("קורא ZIM לא הופעל"));
        return;
      }
      const channel = new MessageChannel();
      const timeout = setTimeout(() => {
        channel.port1.close();
        reject(new Error("הפעולה ארכה זמן רב מדי. נסה שוב, או בחר ארכיון קטן יותר."));
      }, timeoutMs || requestTimeout);
      channel.port1.onmessage = (event) => {
        clearTimeout(timeout);
        channel.port1.close();
        resolve(event.data);
      };
      channel.port1.onmessageerror = () => {
        clearTimeout(timeout);
        channel.port1.close();
        reject(new Error("התקבלה תשובה לא תקינה מהקורא."));
      };
      try {
        state.worker.postMessage(Object.assign({ action: action }, payload || {}), [channel.port2]);
      } catch (error) {
        clearTimeout(timeout);
        channel.port1.close();
        reject(error);
      }
    });
  }

  function cleanObjectUrls() {
    state.resourceUrls.forEach((url) => URL.revokeObjectURL(url));
    state.resourceUrls.clear();
  }

  function baseArchiveName(filename) {
    return filename.replace(/\.zim[a-z]{2}$/i, ".zim");
  }

  function validZimName(filename) {
    return /\.zim(?:[a-z]{2})?$/i.test(filename);
  }

  function setArchiveDetails(name, count, fileCount) {
    $("archiveName").textContent = name;
    $("archiveName").title = name;
    $("archiveMeta").textContent = Number(count).toLocaleString("he-IL") + " ערכים" + (fileCount > 1 ? " · " + fileCount + " חלקי קובץ" : "");
    $("archiveState").classList.add("ready");
  }

  async function loadArchive(fileList) {
    const files = Array.from(fileList || []);
    if (!files.length) return;
    const invalid = files.filter((file) => !validZimName(file.name));
    if (invalid.length) {
      status("הקובץ חייב להיות בפורמט ZIM (.zim).", true);
      showToast("נבחר קובץ שאינו בפורמט ZIM.");
      return;
    }
    const bases = Array.from(new Set(files.map((file) => baseArchiveName(file.name).toLowerCase())));
    if (bases.length !== 1) {
      status("אפשר לטעון ארכיון אחד בכל פעם. אם הארכיון מפוצל לחלקים, בחר את כל החלקים של אותו קובץ.", true);
      showToast("נבחרו כמה ארכיונים שונים. בחר ארכיון אחד.");
      return;
    }

    const firstName = baseArchiveName(files[0].name);
    state.searchSequence += 1;
    state.pageBusy = false;
    state.ready = false;
    state.selectedPath = "";
    state.selectedTitle = "";
    state.archiveName = firstName;
    state.activeQueries = [""];
    state.mode = "browse";
    state.queryOffset = 0;
    state.total = 0;
    state.results = [];
    state.hasMore = false;
    cleanObjectUrls();
    setActiveNav();
    busy(true, "טוען את הארכיון", "הקובץ נשאר במכשיר שלך. קובץ גדול עשוי להיפתח במשך זמן מה.");
    status("");
    $("topImportBtn").disabled = true;
    $("heroImportBtn").disabled = true;
    $("archiveName").textContent = firstName;
    $("archiveMeta").textContent = "מתחבר לקובץ...";
    $("archiveState").classList.remove("ready");

    try {
      if (state.worker) state.worker.terminate();
      state.worker = new Worker(new URL("vendor/libzim-wasm.js", document.baseURI));
      state.worker.addEventListener("error", () => showToast("קורא ZIM נתקל בשגיאה. רענן את העמוד ונסה שוב."));
      await request("init", { files: files }, 600000);
      busy(true, "הארכיון נטען", "בודק כמה ערכים קיימים בקובץ...");
      const count = await request("getArticleCount", {}, 120000);
      state.articleCount = Number(count) || 0;
      state.ready = true;
      state.archiveName = firstName;
      setArchiveDetails(firstName, state.articleCount, files.length);
      activateWorkspace();
      $("searchInput").value = "";
      $("searchInput").placeholder = "חיפוש בכותרות...";
      busy(false);
      browsePrefix("", "כל הערכים", [""]);
      showToast("הארכיון נטען בהצלחה.");
    } catch (error) {
      busy(false);
      state.ready = false;
      $("archiveMeta").textContent = "הטעינה נכשלה";
      $("archiveState").classList.remove("ready");
      const message = error && error.message ? error.message : "שגיאה לא ידועה";
      status("לא הצלחנו לפתוח את הארכיון. " + message, true);
      $("welcomeView").hidden = false;
      $("workspaceView").hidden = true;
      showToast("טעינת הארכיון נכשלה: " + message);
      if (state.worker) {
        state.worker.terminate();
        state.worker = null;
      }
    } finally {
      $("topImportBtn").disabled = false;
      $("heroImportBtn").disabled = false;
      $("zimInput").value = "";
    }
  }

  function browsePrefix(query, label, queries) {
    if (!state.ready) {
      showToast("תחילה יש לייבא קובץ ZIM.");
      return;
    }
    state.searchSequence += 1;
    state.mode = "browse";
    state.activeQueries = queries || [query || ""];
    state.browseLabel = label || "כל הערכים";
    state.queryOffset = 0;
    state.hasMore = false;
    $("searchInput").value = "";
    $("clearSearchBtn").hidden = true;
    $("searchInput").placeholder = "חיפוש בכותרות...";
    $("searchModeNote").textContent = "בחר אות כדי לעיין בתוכן, או השתמש בחיפוש כדי למצוא ערך.";
    showPageHeading(label === "כל הערכים" ? "תוכן עניינים" : "ערכים לפי " + label, "הערכים מוצגים מתוך הארכיון שבחרת.", label || "כל הערכים");
    setActiveNav();
    activateWorkspace();
    loadTitlePage(true);
  }

  function setMode(mode) {
    if (!state.ready) {
      showToast("ייבא תחילה קובץ ZIM כדי להתחיל.");
      return;
    }
    state.searchSequence += 1;
    state.mode = mode;
    state.queryOffset = 0;
    state.activeQueries = [""];
    const query = $("searchInput").value.trim();
    if (mode === "browse") {
      browsePrefix("", "כל הערכים", [""]);
      return;
    }
    if (mode === "titles") {
      $("searchInput").placeholder = "חיפוש בכותרות...";
      $("searchModeNote").textContent = "חיפוש לפי שם הערך. אפשר גם לבחור אות ברשימת הא״ב.";
      showPageHeading("חיפוש בכותרות", "מצא ערכים לפי שם, או עיין בתוכן העניינים.", "חיפוש בכותרות");
    } else {
      $("searchInput").placeholder = "חיפוש בתוך הטקסט...";
      $("searchModeNote").textContent = "החיפוש בתוך הטקסט זמין רק בארכיונים הכוללים אינדקס חיפוש מלא.";
      showPageHeading("חיפוש בתוך הטקסט", "מחפש מילים בגוף הערכים, אם הארכיון תומך בכך.", "חיפוש בתוך הטקסט");
    }
    setActiveNav();
    activateWorkspace();
    if (query) scheduleSearch();
    else {
      state.results = [];
      state.total = 0;
      state.hasMore = false;
      renderResults();
      setResultsLoading(false);
      setEmptyMessage(mode === "text" ? "הקלד מילים לחיפוש" : "הקלד שם של ערך", mode === "text" ? "חיפוש מלא יעבוד בארכיון עם אינדקס מתאים." : "תוצאות הכותרות יופיעו כאן.");
    }
    $("searchInput").focus();
  }

  function scheduleSearch() {
    clearTimeout(window.__zipediaSearchTimer);
    window.__zipediaSearchTimer = setTimeout(() => runSearch(), 300);
  }

  async function runSearch() {
    if (!state.ready) return;
    const query = $("searchInput").value.trim();
    $("clearSearchBtn").hidden = !query;
    state.searchSequence += 1;
    const seq = state.searchSequence;
    state.queryOffset = 0;
    state.activeQueries = [query];
    if (!query) {
      if (state.mode === "text") {
        state.results = [];
        state.total = 0;
        state.hasMore = false;
        renderResults();
        setResultsLoading(false);
        setEmptyMessage("הקלד מילים לחיפוש", "חיפוש בתוך הטקסט זמין בארכיונים עם אינדקס מלא.");
      } else {
        browsePrefix("", "כל הערכים", [""]);
      }
      return;
    }
    if (state.mode === "browse") state.mode = "titles";
    setActiveNav();
    setResultsLoading(true);
    $("resultsError").hidden = true;
    $("resultsHeading").textContent = state.mode === "text" ? "תוצאות חיפוש בטקסט" : "תוצאות לפי כותרת";
    $("activeBrowseLabel").textContent = "חיפוש: " + query;
    try {
      if (state.mode === "text") {
        const response = await request("search", { text: query, numResults: 150 }, 180000);
        if (seq !== state.searchSequence) return;
        state.results = (response && response.results ? response.results : []).map(normalizeResult);
        state.total = state.results.length;
        state.queryOffset = state.results.length;
        state.hasMore = false;
        renderResults();
        setResultsLoading(false);
        if (!state.results.length) setEmptyMessage("לא נמצאו תוצאות", "ייתכן שהארכיון אינו כולל אינדקס לחיפוש בתוך הטקסט.");
      } else {
        await loadTitlePage(true, query, seq);
      }
    } catch (error) {
      if (seq !== state.searchSequence) return;
      setResultsLoading(false);
      showResultsError(error.message || "החיפוש נכשל.");
    }
  }

  function normalizeResult(item) {
    return { title: (item && item.title) || (item && item.path) || "ערך ללא שם", path: (item && item.path) || "" };
  }

  async function loadTitlePage(reset, forcedQuery, searchSeq) {
    if (!state.ready || (state.pageBusy && !reset)) return;
    const currentMode = state.mode;
    const requestSequence = searchSeq !== undefined ? searchSeq : state.searchSequence;
    const query = forcedQuery !== undefined ? forcedQuery : (currentMode === "titles" ? $("searchInput").value.trim() : "");
    const queries = currentMode === "browse" ? state.activeQueries : [query];
    if (reset) {
      state.queryOffset = 0;
      state.results = [];
      state.total = 0;
      state.hasMore = false;
    }
    const start = reset ? 0 : state.queryOffset;
    const perQuery = queries.length > 1 ? 18 : pageSize;
    state.pageBusy = true;
    setResultsLoading(true);
    $("resultsError").hidden = true;
    try {
      const pages = await Promise.all(queries.map((q) => request("listTitles", { query: q, start: start, count: perQuery, range: currentMode === "browse" }, requestTimeout)));
      if (requestSequence !== state.searchSequence) return;
      const additions = [];
      let estimated = 0;
      let canContinue = false;
      pages.forEach((page) => {
        const results = page && Array.isArray(page.results) ? page.results : [];
        additions.push.apply(additions, results.map(normalizeResult));
        estimated += Number(page && page.total) || 0;
        if (page && page.error) throw new Error(page.error);
        if (results.length >= perQuery || (Number(page && page.total) || 0) > start + results.length) canContinue = true;
      });
      if (reset) state.results = [];
      const seen = new Set(state.results.map((item) => item.path));
      additions.forEach((item) => {
        if (item.path && !seen.has(item.path)) {
          seen.add(item.path);
          state.results.push(item);
        }
      });
      state.results.sort((a, b) => collator.compare(a.title, b.title) || collator.compare(a.path, b.path));
      state.total = Math.max(estimated, state.results.length);
      state.queryOffset = start + perQuery;
      state.hasMore = canContinue && pages.some((page) => {
        const results = page && Array.isArray(page.results) ? page.results : [];
        return results.length >= perQuery || (Number(page && page.total) || 0) > start + results.length;
      });
      renderResults();
      if (!state.results.length) {
        setEmptyMessage(query ? "לא נמצאו ערכים מתאימים" : "בחר אות כדי לעיין בערכים", query ? "בדוק את האיות או נסה חיפוש בתוך הטקסט." : "אפשר לבחור אות בעברית או באנגלית בתפריט האותיות.");
      }
      if (currentMode === "browse") {
        $("resultsHeading").textContent = state.browseLabel === "כל הערכים" ? "תוכן עניינים" : state.browseLabel;
        $("activeBrowseLabel").textContent = state.browseLabel;
      } else {
        $("resultsHeading").textContent = "תוצאות לפי כותרת";
        $("activeBrowseLabel").textContent = query ? "חיפוש: " + query : "כל הערכים";
      }
    } catch (error) {
      if (requestSequence !== state.searchSequence) return;
      showResultsError(error.message || "לא הצלחנו לקרוא את רשימת הערכים.");
    } finally {
      if (requestSequence === state.searchSequence) {
        state.pageBusy = false;
        setResultsLoading(false);
      }
    }
  }

  function setResultsLoading(isLoading) {
    $("loadingSpinner").hidden = !isLoading;
    $("loadMoreBtn").disabled = isLoading;
    if (isLoading) $("resultsFooter").hidden = true;
    else updateResultsFooter();
  }

  function updateResultsFooter() {
    const canMore = Boolean(state.hasMore) && (state.mode === "browse" || state.mode === "titles");
    $("resultsFooter").hidden = !canMore || !state.results.length;
    $("resultsProgress").textContent = state.total > 0
      ? "מציג " + state.results.length.toLocaleString("he-IL") + " מתוך כ־" + state.total.toLocaleString("he-IL")
      : "הוצגו " + state.results.length.toLocaleString("he-IL") + " ערכים";
  }

  function renderResults() {
    const list = $("resultList");
    list.replaceChildren();
    $("resultsCount").textContent = state.total > 0 ? "כ־" + state.total.toLocaleString("he-IL") : "";
    if (!state.results.length) return;
    const frag = document.createDocumentFragment();
    state.results.forEach((item, index) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "result-item" + (item.path === state.selectedPath ? " is-selected" : "");
      const number = document.createElement("span");
      number.className = "result-number";
      number.textContent = (index + 1).toLocaleString("he-IL");
      const copy = document.createElement("span");
      copy.className = "result-copy";
      const title = document.createElement("span");
      title.className = "result-title";
      title.textContent = item.title;
      const path = document.createElement("span");
      path.className = "result-path";
      path.textContent = item.path;
      copy.append(title, path);
      button.append(number, copy);
      button.addEventListener("click", () => openArticle(item));
      frag.appendChild(button);
    });
    list.appendChild(frag);
  }

  function setEmptyMessage(title, message) {
    const box = document.createElement("div");
    box.className = "empty-state";
    const symbol = document.createElement("span");
    symbol.className = "empty-symbol";
    symbol.textContent = "☷";
    const heading = document.createElement("strong");
    heading.textContent = title;
    const detail = document.createElement("p");
    detail.textContent = message;
    box.append(symbol, heading, detail);
    $("resultList").replaceChildren(box);
    $("resultsFooter").hidden = true;
    $("resultsCount").textContent = "";
  }

  function showResultsError(message) {
    $("resultsError").textContent = message;
    $("resultsError").hidden = false;
  }

  function resolveZimPath(url, fromPath) {
    if (!url) return null;
    const raw = String(url).trim();
    if (!raw || raw.startsWith("#") || /^(data:|blob:|javascript:|mailto:|tel:|https?:|\/\/)/i.test(raw)) return null;
    const baseDir = (fromPath || "").split("/").slice(0, -1).join("/");
    try {
      const base = "https://zim.invalid/" + (baseDir ? baseDir.replace(/^\/+|\/+$/g, "") + "/" : "");
      const resolved = new URL(raw.split("#")[0].split("?")[0], base);
      let path = resolved.pathname.replace(/^\/+/, "");
      try { path = decodeURIComponent(path); } catch (_) {}
      return path || null;
    } catch (_) {
      return null;
    }
  }

  function mimeForPath(path) {
    const ext = (String(path).split(".").pop() || "").toLowerCase();
    const map = { jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png", gif: "image/gif", webp: "image/webp", svg: "image/svg+xml", ico: "image/x-icon", css: "text/css", js: "text/plain", mp3: "audio/mpeg", ogg: "audio/ogg", mp4: "video/mp4", webm: "video/webm", woff: "font/woff", woff2: "font/woff2", ttf: "font/ttf" };
    return map[ext] || "application/octet-stream";
  }

  function contentBytes(content) {
    if (content instanceof Uint8Array) return content;
    if (content instanceof ArrayBuffer) return new Uint8Array(content);
    return new Uint8Array(content || []);
  }

  async function getResourceUrl(path) {
    if (state.resourceUrls.has(path)) return state.resourceUrls.get(path);
    const response = await request("getEntryByPath", { path: path, follow: true }, 90000);
    if (!response || !response.content || !contentBytes(response.content).length) return null;
    const bytes = contentBytes(response.content);
    const blob = new Blob([bytes], { type: response.mimetype || mimeForPath(path) });
    const url = URL.createObjectURL(blob);
    state.resourceUrls.set(path, url);
    return url;
  }

  async function rewriteCss(css, cssPath) {
    const matches = Array.from(css.matchAll(/url\(\s*(['"]?)(.*?)\1\s*\)/gi));
    for (const match of matches) {
      const raw = match[2].trim();
      if (!raw || /^(data:|blob:|https?:|\/\/|#)/i.test(raw)) continue;
      const path = resolveZimPath(raw, cssPath);
      if (!path) continue;
      try {
        const url = await getResourceUrl(path);
        if (url) css = css.replace(match[0], 'url("' + url + '")');
      } catch (_) {}
    }
    return css;
  }

  async function prepareArticleHtml(html, currentPath) {
    const parsed = new DOMParser().parseFromString(html, "text/html");
    parsed.querySelectorAll("script, iframe, object, embed, form, base, meta[http-equiv='refresh']").forEach((node) => node.remove());
    parsed.querySelectorAll("*").forEach((node) => {
      Array.from(node.attributes).forEach((attr) => {
        if (/^on/i.test(attr.name) || attr.name.toLowerCase() === "srcdoc") node.removeAttribute(attr.name);
      });
    });
    const head = parsed.head || parsed.documentElement.insertBefore(parsed.createElement("head"), parsed.documentElement.firstChild);
    const defaultStyle = parsed.createElement("style");
    defaultStyle.textContent = "body{font-family:Arial,'Noto Sans Hebrew','Segoe UI',sans-serif;color:#202a36;line-height:1.75;font-size:16px;padding:18px 22px;margin:0 auto;max-width:1050px;overflow-wrap:anywhere}img,video{max-width:100%;height:auto}table{max-width:100%;border-collapse:collapse;display:block;overflow:auto}th,td{border:1px solid #d9dfe8;padding:6px 9px}a{color:#36c;text-decoration:none}a:hover{text-decoration:underline}pre{max-width:100%;overflow:auto;white-space:pre-wrap}h1,h2,h3{line-height:1.35}blockquote{border-right:3px solid #d5deeb;margin-right:0;padding-right:14px;color:#566479}";
    head.appendChild(defaultStyle);

    const styleLinks = Array.from(parsed.querySelectorAll("link[rel~='stylesheet'][href]"));
    for (const link of styleLinks) {
      const resourcePath = resolveZimPath(link.getAttribute("href"), currentPath);
      if (!resourcePath) { link.remove(); continue; }
      try {
        const result = await request("getEntryByPath", { path: resourcePath, follow: true }, 90000);
        if (result && result.content) {
          const css = await rewriteCss(new TextDecoder("utf-8").decode(contentBytes(result.content)), resourcePath);
          const style = parsed.createElement("style");
          style.textContent = css;
          link.replaceWith(style);
        } else link.remove();
      } catch (_) { link.remove(); }
    }

    const images = Array.from(parsed.querySelectorAll("img[src]")).slice(0, 80);
    await Promise.all(images.map(async (image) => {
      const path = resolveZimPath(image.getAttribute("src"), currentPath);
      if (!path) return;
      try {
        const url = await getResourceUrl(path);
        if (url) image.setAttribute("src", url);
        else image.removeAttribute("src");
      } catch (_) {
        image.removeAttribute("src");
        image.setAttribute("alt", image.getAttribute("alt") || "מדיה לא זמינה בארכיון");
      }
    }));

    parsed.querySelectorAll("a[href]").forEach((anchor) => {
      const raw = anchor.getAttribute("href") || "";
      if (raw.startsWith("#")) return;
      if (/^(https?:|mailto:|tel:|\/\/)/i.test(raw)) {
        if (/^(https?:|\/\/)/i.test(raw)) {
          anchor.setAttribute("target", "_blank");
          anchor.setAttribute("rel", "noopener noreferrer");
        }
        return;
      }
      const path = resolveZimPath(raw, currentPath);
      if (path) {
        anchor.setAttribute("href", "#");
        anchor.dataset.zimPath = path;
      } else {
        anchor.removeAttribute("href");
      }
    });

    const body = parsed.body || parsed.documentElement;
    body.querySelectorAll("video[src],audio[src],source[src]").forEach((media) => {
      const path = resolveZimPath(media.getAttribute("src"), currentPath);
      if (path) media.setAttribute("data-zim-resource", path);
    });
    body.querySelectorAll("[srcset]").forEach((node) => node.removeAttribute("srcset"));
    return "<!doctype html>" + parsed.documentElement.outerHTML;
  }

  async function openArticle(item) {
    if (!state.ready || !item || !item.path) return;
    state.selectedPath = item.path;
    state.selectedTitle = item.title || item.path;
    $("readerPlaceholder").hidden = true;
    $("readerHeader").hidden = false;
    $("readerTitle").textContent = state.selectedTitle;
    $("readerLoading").hidden = false;
    $("readerError").hidden = true;
    $("articleFrame").hidden = true;
    renderResults();
    try {
      const response = await request("getEntryByPath", { path: item.path, follow: true }, 120000);
      if (!response || response.error || !response.content) throw new Error("לא נמצא תוכן לערך הזה בארכיון.");
      const bytes = contentBytes(response.content);
      const mime = response.mimetype || "text/html";
      let srcdoc;
      if (mime.startsWith("image/")) {
        const url = await getResourceUrl(item.path);
        srcdoc = "<!doctype html><html><body style='margin:0;background:#fff;display:grid;place-items:center;min-height:100vh'><img style='max-width:100%;max-height:95vh;object-fit:contain' src='" + (url || "") + "' alt='" + escapeAttribute(state.selectedTitle) + "'></body></html>";
      } else if (mime.startsWith("text/") || mime.includes("html") || mime.includes("xml")) {
        srcdoc = await prepareArticleHtml(new TextDecoder("utf-8").decode(bytes), item.path);
      } else {
        srcdoc = "<!doctype html><html lang='he' dir='rtl'><body><pre style='white-space:pre-wrap;overflow-wrap:anywhere;font:14px/1.7 monospace;padding:20px'>" + escapeHtml(new TextDecoder("utf-8").decode(bytes)) + "</pre></body></html>";
      }
      $("articleFrame").onload = () => {
        try {
          const doc = $("articleFrame").contentDocument;
          doc.addEventListener("click", (event) => {
            const anchor = event.target && event.target.closest ? event.target.closest("a[data-zim-path]") : null;
            if (!anchor) return;
            event.preventDefault();
            const nextPath = anchor.dataset.zimPath;
            const hash = (anchor.getAttribute("href") || "").split("#")[1];
            openArticle({ path: nextPath, title: anchor.textContent.trim() || nextPath }).then(() => {
              if (hash) {
                let target;
                try { target = $("articleFrame").contentDocument.getElementById(decodeURIComponent(hash)); } catch (_) {}
                if (target) target.scrollIntoView();
              }
            });
          });
        } catch (_) {}
      };
      $("articleFrame").srcdoc = srcdoc;
      $("articleFrame").hidden = false;
      $("readerLoading").hidden = true;
      if (window.innerWidth < 780) $("readerHeader").scrollIntoView({ block: "start", behavior: "smooth" });
    } catch (error) {
      $("readerLoading").hidden = true;
      $("readerError").hidden = false;
      $("readerError").textContent = error.message || "לא ניתן להציג את הערך.";
    }
  }

  function escapeHtml(text) {
    return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function escapeAttribute(text) {
    return escapeHtml(text);
  }

  async function loadMore() {
    if (state.pageBusy || state.mode === "text") return;
    await loadTitlePage(false);
  }

  function importDialog() {
    $("zimInput").click();
  }

  $("topImportBtn").addEventListener("click", importDialog);
  $("heroImportBtn").addEventListener("click", importDialog);
  $("changeArchiveBtn").addEventListener("click", importDialog);
  $("dropZone").addEventListener("click", importDialog);
  $("zimInput").addEventListener("change", (event) => loadArchive(event.target.files));
  $("browseAllBtn").addEventListener("click", () => browsePrefix("", "כל הערכים", [""]));
  $("searchTitlesBtn").addEventListener("click", () => setMode("titles"));
  $("searchTextBtn").addEventListener("click", () => setMode("text"));
  $("searchSubmitBtn").addEventListener("click", () => runSearch());
  $("clearSearchBtn").addEventListener("click", () => {
    $("searchInput").value = "";
    $("clearSearchBtn").hidden = true;
    if (state.mode === "text") {
      state.results = [];
      state.total = 0;
      state.hasMore = false;
      renderResults();
      setResultsLoading(false);
      setEmptyMessage("הקלד מילים לחיפוש", "חיפוש בתוך הטקסט זמין בארכיונים עם אינדקס מלא.");
    } else if (state.mode === "titles") loadTitlePage(true, "");
    else browsePrefix("", "כל הערכים", [""]);
    $("searchInput").focus();
  });
  $("searchInput").addEventListener("input", () => {
    $("clearSearchBtn").hidden = !$("searchInput").value.trim();
    if (state.mode === "browse" && $("searchInput").value.trim()) {
      state.mode = "titles";
      $("searchInput").placeholder = "חיפוש בכותרות...";
      setActiveNav();
    }
    scheduleSearch();
  });
  $("searchInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      clearTimeout(window.__zipediaSearchTimer);
      runSearch();
    }
  });
  $("loadMoreBtn").addEventListener("click", loadMore);
  $("backToListBtn").addEventListener("click", () => $("resultList").scrollIntoView({ block: "start", behavior: "smooth" }));
  $("copyLinkBtn").addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(state.selectedTitle || state.selectedPath);
      showToast("שם הערך הועתק.");
    } catch (_) {
      showToast(state.selectedTitle || state.selectedPath);
    }
  });
  $("numbersBtn").addEventListener("click", () => browsePrefix("", "מספרים וסימנים", Array.from("0123456789")));
  buildAlphabet("hebrewAlphabet", hebrewLetters);
  buildAlphabet("latinAlphabet", latinLetters);

  ["dragenter", "dragover"].forEach((eventName) => {
    document.addEventListener(eventName, (event) => {
      if (!event.dataTransfer || !Array.from(event.dataTransfer.types || []).includes("Files")) return;
      event.preventDefault();
      $("dropZone").classList.add("drag-active");
    });
  });
  ["dragleave", "drop"].forEach((eventName) => {
    document.addEventListener(eventName, (event) => {
      if (eventName === "drop") {
        event.preventDefault();
        if (event.dataTransfer && event.dataTransfer.files && event.dataTransfer.files.length) loadArchive(event.dataTransfer.files);
      }
      $("dropZone").classList.remove("drag-active");
    });
  });

  setActiveNav();
})();