/* אופליין גיים — כל המשחקים והגרפיקה כלולים מקומית */
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const gameInfo = {
    runner: { name: "חתול רץ", help: "געו במסך או לחצו על קפיצה כדי לדלג מעל המכשולים." },
    breakout: { name: "שובר לבנים", help: "גררו את המשטח ימינה ושמאלה כדי להחזיר את הכדור." },
    space: { name: "קפיצת חלל", help: "החזיקו ◀ או ▶ כדי לזוז. נחתו על הפלטפורמות ואל תיפלו!" },
    bubbles: { name: "יורה הבועות", help: "כוונו עם נגיעה במסך, ירו בועה וחברו שלוש בועות מאותו צבע." },
    memory: { name: "זיכרון", help: "מצאו זוגות תואמים. נסו לסיים בכמה שפחות מהלכים." },
    sudoku: { name: "סודוקו", help: "בחרו משבצת ריקה ומספר. המספרים הכחולים הם רמזים קבועים." },
    racer: { name: "מרוץ מכוניות", help: "החליפו נתיב כדי להתחמק מהמכוניות. לחצו על החצים למטה." },
    ttt: { name: "איקס עיגול", help: "אתם × והמחשב ○. צרו שלישייה לפני המחשב." }
  };
  let currentGame = "";
  let rafId = 0;
  let canvas = null, ctx = null, g = null;
  let memoryState = null, sudokuState = null, tttState = null;

  function storageGet(key, fallback) {
    try { const v = localStorage.getItem(key); return v === null ? fallback : v; }
    catch (_) { return fallback; }
  }
  function storageSet(key, value) {
    try { localStorage.setItem(key, String(value)); } catch (_) {}
  }
  function bestFor(id) { return Number(storageGet("offline-best-" + id, "0")) || 0; }
  function setScore(value) {
    const score = Math.max(0, Math.floor(Number(value) || 0));
    let best = bestFor(currentGame);
    if (score > best) { best = score; storageSet("offline-best-" + currentGame, best); }
    $("scorePill").textContent = "ניקוד " + score + " · שיא " + best;
  }
  function setHelp(text) { $("help").textContent = text; }
  function actionButton(label, fn, extra) {
    const b = document.createElement("button");
    b.type = "button"; b.className = "action-btn" + (extra ? " " + extra : "");
    b.textContent = label; b.addEventListener("click", fn);
    $("actions").appendChild(b);
    return b;
  }
  function clearFrame() { if (rafId) cancelAnimationFrame(rafId); rafId = 0; }
  function refreshLibraryRecords() {
    document.querySelectorAll("[data-best]").forEach(function (node) {
      const id = node.getAttribute("data-best"), value = bestFor(id);
      node.textContent = id === "sudoku" ? (value ? "התקדמות " + value : "טרם נפתר") : (value ? "שיא " + value : "שיא —");
    });
  }
  function showHome() {
    clearFrame(); currentGame = ""; g = null; canvas = null; ctx = null;
    $("play").hidden = true; $("home").hidden = false; $("about").hidden = true;
    $("stage").innerHTML = ""; $("actions").innerHTML = ""; setHelp("");
    refreshLibraryRecords();
  }
  function showAbout() {
    clearFrame(); currentGame = ""; g = null; canvas = null; ctx = null;
    $("home").hidden = true; $("play").hidden = true; $("about").hidden = false;
    $("stage").innerHTML = ""; $("actions").innerHTML = "";
  }
  window.showHome = showHome;
  $("backHome").addEventListener("click", showHome);
  $("aboutBack").addEventListener("click", showHome);
  $("brandHome").addEventListener("click", showHome);
  $("aboutButton").addEventListener("click", showAbout);
  refreshLibraryRecords();
  document.querySelectorAll("[data-game]").forEach(function (button) {
    button.addEventListener("click", function () { startGame(button.dataset.game); });
  });

  function startGame(id) {
    clearFrame(); currentGame = id; g = null; canvas = null; ctx = null;
    $("home").hidden = true; $("play").hidden = false;
    $("playingName").textContent = gameInfo[id].name;
    $("stage").innerHTML = ""; $("actions").innerHTML = "";
    setScore(0); setHelp(gameInfo[id].help);
    if (["runner", "breakout", "space", "bubbles", "racer"].includes(id)) initCanvasGame(id);
    else if (id === "memory") initMemory();
    else if (id === "sudoku") initSudoku();
    else if (id === "ttt") initTicTacToe();
  }

  function makeCanvas() {
    canvas = document.createElement("canvas");
    canvas.className = "game-canvas"; canvas.width = 360; canvas.height = 520;
    canvas.setAttribute("aria-label", "משטח המשחק");
    $("stage").appendChild(canvas);
    ctx = canvas.getContext("2d", { alpha: false });
    ctx.textAlign = "center"; ctx.textBaseline = "middle";
    ctx.lineJoin = "round"; ctx.lineCap = "round";
  }
  function pointerPos(e) {
    const r = canvas.getBoundingClientRect();
    return { x: (e.clientX - r.left) * 360 / r.width, y: (e.clientY - r.top) * 520 / r.height };
  }
  function initCanvasGame(id) {
    makeCanvas();
    if (id === "runner") initRunner();
    if (id === "breakout") initBreakout();
    if (id === "space") initSpace();
    if (id === "bubbles") initBubbles();
    if (id === "racer") initRacer();
    canvas.addEventListener("pointerdown", function (e) {
      e.preventDefault();
      const p = pointerPos(e);
      if (!g) return;
      if (g.over) { restartCanvasGame(); return; }
      if (id === "runner") jumpRunner();
      if (id === "breakout") { g.paddle.x = clamp(p.x - g.paddle.w / 2, 0, 360 - g.paddle.w); }
      if (id === "bubbles") shootBubble(p.x, p.y);
      if (id === "racer") { if (p.x < 120) moveCar(-1); else if (p.x > 240) moveCar(1); }
    });
    canvas.addEventListener("pointermove", function (e) {
      if (id === "breakout" && g && !g.over && (e.buttons || e.pressure > 0)) {
        const p = pointerPos(e); g.paddle.x = clamp(p.x - g.paddle.w / 2, 0, 360 - g.paddle.w);
      }
    });
    startLoop();
  }
  function restartCanvasGame() { clearFrame(); $('stage').innerHTML = ''; $('actions').innerHTML = ''; initCanvasGame(currentGame); }
  function startLoop() {
    clearFrame();
    let last = 0, shown = -1;
    function frame(time) {
      if (!g || !canvas || !ctx) return;
      const dt = last ? clamp((time - last) / 16.6667, 0.25, 2) : 1;
      last = time;
      if (!g.over) updateGame(dt);
      drawGame();
      const s = Math.floor(g.score || 0);
      if (s !== shown) { setScore(s); shown = s; }
      rafId = requestAnimationFrame(frame);
    }
    rafId = requestAnimationFrame(frame);
  }
  function clamp(n, a, b) { return Math.max(a, Math.min(b, n)); }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function rounded(x, y, w, h, r, fill, stroke) {
    const rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath(); ctx.moveTo(x + rr, y); ctx.lineTo(x + w - rr, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + rr); ctx.lineTo(x + w, y + h - rr);
    ctx.quadraticCurveTo(x + w, y + h, x + w - rr, y + h); ctx.lineTo(x + rr, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - rr); ctx.lineTo(x, y + rr);
    ctx.quadraticCurveTo(x, y, x + rr, y); ctx.closePath();
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    if (stroke) { ctx.strokeStyle = stroke; ctx.stroke(); }
  }
  function text(t, x, y, size, color, weight) {
    ctx.font = (weight || 700) + " " + size + "px system-ui, sans-serif";
    ctx.fillStyle = color || "#fff"; ctx.fillText(t, x, y);
  }
  function drawCat(x, baseY, scale, stride) {
    ctx.save(); ctx.translate(x, baseY); ctx.scale(scale || 1, scale || 1);
    const phase = stride || 0;
    ctx.fillStyle = "#112c3a30"; ctx.beginPath(); ctx.ellipse(0, 3, 22, 5, 0, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "#cbd8e8"; ctx.lineWidth = 6; ctx.lineCap = "round";
    ctx.beginPath(); ctx.moveTo(11, -13); ctx.bezierCurveTo(28, -30, 23, -43, 13, -39); ctx.stroke();
    ctx.fillStyle = "#dce5ef"; ctx.beginPath(); ctx.ellipse(0, -13, 15, 22, -.12, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#f2f6fb"; ctx.beginPath(); ctx.ellipse(-1, -11, 9, 15, -.15, 0, Math.PI * 2); ctx.fill();
    ctx.fillStyle = "#dce5ef"; ctx.beginPath(); ctx.moveTo(-15,-31);ctx.lineTo(-17,-51);ctx.lineTo(-3,-39);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.moveTo(3,-39);ctx.lineTo(18,-52);ctx.lineTo(16,-29);ctx.closePath();ctx.fill();
    ctx.fillStyle = "#ef9ea9";ctx.beginPath();ctx.moveTo(-13,-36);ctx.lineTo(-14,-45);ctx.lineTo(-6,-38);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.moveTo(7,-38);ctx.lineTo(14,-45);ctx.lineTo(13,-35);ctx.closePath();ctx.fill();
    ctx.fillStyle = "#f0f4fa";ctx.beginPath();ctx.arc(0,-29,18,0,Math.PI*2);ctx.fill();
    ctx.fillStyle = "#23374d";ctx.beginPath();ctx.ellipse(-7,-31,2.5,4,0,0,Math.PI*2);ctx.ellipse(7,-31,2.5,4,0,0,Math.PI*2);ctx.fill();
    ctx.fillStyle = "#fff";ctx.beginPath();ctx.arc(-7.6,-32.5,1,0,Math.PI*2);ctx.arc(6.4,-32.5,1,0,Math.PI*2);ctx.fill();
    ctx.fillStyle = "#ec8d9c";ctx.beginPath();ctx.moveTo(-2,-24);ctx.lineTo(2,-24);ctx.lineTo(0,-21);ctx.closePath();ctx.fill();
    ctx.strokeStyle="#566b80";ctx.lineWidth=1.3;ctx.beginPath();ctx.moveTo(0,-21);ctx.quadraticCurveTo(-4,-18,-7,-21);ctx.moveTo(0,-21);ctx.quadraticCurveTo(4,-18,7,-21);ctx.stroke();
    ctx.strokeStyle="#eef4fa";ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(-9,-19);ctx.lineTo(-21,-17);ctx.moveTo(9,-19);ctx.lineTo(21,-17);ctx.stroke();
    ctx.strokeStyle="#cbd8e8";ctx.lineWidth=5;ctx.beginPath();ctx.moveTo(-8,2);ctx.lineTo(-10+Math.sin(phase)*4,8);ctx.moveTo(8,2);ctx.lineTo(10-Math.sin(phase)*4,8);ctx.stroke();
    ctx.fillStyle="#55bfd4";ctx.beginPath();ctx.moveTo(-13,-10);ctx.lineTo(13,-10);ctx.lineTo(9,-4);ctx.lineTo(0,0);ctx.lineTo(-8,-4);ctx.closePath();ctx.fill();
    ctx.restore();
  }
  function drawCactus(x, baseY, h) {
    ctx.save(); ctx.translate(x, baseY); ctx.fillStyle="#245b50";
    ctx.beginPath();ctx.moveTo(-7,0);ctx.lineTo(-7,-h+8);ctx.quadraticCurveTo(-6,-h,0,-h);ctx.quadraticCurveTo(7,-h,7,-h+8);ctx.lineTo(7,0);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.moveTo(-6,-h*.5);ctx.lineTo(-15,-h*.5);ctx.lineTo(-15,-h*.72);ctx.quadraticCurveTo(-15,-h*.8,-10,-h*.8);ctx.lineTo(-9,-h*.68);ctx.lineTo(-6,-h*.68);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.moveTo(6,-h*.36);ctx.lineTo(14,-h*.36);ctx.lineTo(14,-h*.55);ctx.quadraticCurveTo(14,-h*.63,9,-h*.63);ctx.lineTo(8,-h*.5);ctx.lineTo(6,-h*.5);ctx.closePath();ctx.fill();
    ctx.strokeStyle="#6ce0a6";ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(-1,-h+7);ctx.lineTo(-1,-5);ctx.moveTo(4,-h*.7);ctx.lineTo(4,-h*.45);ctx.stroke();ctx.restore();
  }
  function drawCoin(x,y,r) {
    ctx.save();const grad=ctx.createRadialGradient(x-r*.35,y-r*.4,1,x,y,r);grad.addColorStop(0,"#fff6c0");grad.addColorStop(.45,"#ffdb6e");grad.addColorStop(1,"#c78b2c");
    ctx.fillStyle=grad;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();ctx.strokeStyle="#fff0a3";ctx.lineWidth=2;ctx.stroke();ctx.strokeStyle="#b97b21";ctx.lineWidth=1.4;ctx.beginPath();ctx.arc(x,y,r*.52,0,Math.PI*2);ctx.stroke();ctx.beginPath();ctx.moveTo(x,y-r*.34);ctx.lineTo(x,y+r*.34);ctx.stroke();ctx.restore();
  }
  function drawAstronaut(x,y,scale) {
    ctx.save();ctx.translate(x,y);ctx.scale(scale||1,scale||1);
    ctx.fillStyle="#13224a55";ctx.beginPath();ctx.ellipse(0,18,21,7,0,0,Math.PI*2);ctx.fill();
    ctx.strokeStyle="#d8efff";ctx.lineWidth=9;ctx.lineCap="round";ctx.beginPath();ctx.moveTo(-8,5);ctx.lineTo(-12,19);ctx.moveTo(8,5);ctx.lineTo(12,19);ctx.stroke();
    ctx.strokeStyle="#5284c9";ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(-12,17);ctx.lineTo(-15,20);ctx.moveTo(12,17);ctx.lineTo(15,20);ctx.stroke();
    rounded(-14,-9,28,25,8,"#e9f4ff","#91c6ef");rounded(-6,-4,12,13,3,"#4ba2e1","#b8e9ff");
    ctx.strokeStyle="#e8f4ff";ctx.lineWidth=6;ctx.beginPath();ctx.moveTo(-13,-4);ctx.lineTo(-21,5);ctx.moveTo(13,-4);ctx.lineTo(21,5);ctx.stroke();
    ctx.strokeStyle="#8fcaf7";ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(-20,5);ctx.lineTo(-22,8);ctx.moveTo(20,5);ctx.lineTo(22,8);ctx.stroke();
    ctx.fillStyle="#eaf5ff";ctx.beginPath();ctx.arc(0,-18,17,0,Math.PI*2);ctx.fill();ctx.strokeStyle="#9ac9ee";ctx.lineWidth=2;ctx.stroke();
    rounded(-12,-25,24,15,7,"#152a4a","#6fd9f7");ctx.strokeStyle="#6fd9f7";ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(-7,-19);ctx.lineTo(6,-19);ctx.stroke();
    ctx.fillStyle="#ffe38b";ctx.beginPath();ctx.arc(0,-18,2,0,Math.PI*2);ctx.fill();ctx.restore();
  }
  function drawCar(x,y,scale,color) {
    ctx.save();ctx.translate(x,y);ctx.scale(scale||1,scale||1);
    ctx.fillStyle="#0004";ctx.beginPath();ctx.ellipse(0,14,19,6,0,0,Math.PI*2);ctx.fill();
    rounded(-19,-2,8,20,3,"#131d2c");rounded(11,-2,8,20,3,"#131d2c");
    ctx.fillStyle=color||"#e34f69";ctx.beginPath();ctx.moveTo(-15,7);ctx.lineTo(-12,-9);ctx.lineTo(-6,-18);ctx.quadraticCurveTo(0,-21,6,-18);ctx.lineTo(12,-9);ctx.lineTo(15,7);ctx.quadraticCurveTo(0,13,-15,7);ctx.closePath();ctx.fill();
    ctx.strokeStyle="#ffffff70";ctx.lineWidth=1.2;ctx.stroke();
    ctx.fillStyle="#203c58";ctx.beginPath();ctx.moveTo(-9,-9);ctx.lineTo(-5,-16);ctx.quadraticCurveTo(0,-18,5,-16);ctx.lineTo(9,-9);ctx.closePath();ctx.fill();
    ctx.strokeStyle="#9fe7ff";ctx.lineWidth=1.4;ctx.beginPath();ctx.moveTo(-7,-9);ctx.lineTo(7,-9);ctx.stroke();
    rounded(-11,3,5,3,1,"#fff0b8");rounded(6,3,5,3,1,"#fff0b8");
    ctx.strokeStyle="#fff";ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(-8,-1);ctx.lineTo(8,-1);ctx.stroke();ctx.restore();
  }
  function background(top, bottom) {
    const grad = ctx.createLinearGradient(0, 0, 0, 520);
    grad.addColorStop(0, top); grad.addColorStop(1, bottom);
    ctx.fillStyle = grad; ctx.fillRect(0, 0, 360, 520);
  }
  function stars(count, offset) {
    for (let i = 0; i < count; i++) {
      const x = (i * 71 + (offset || 0) * 13) % 360;
      const y = (i * 113 + 37) % 500;
      ctx.globalAlpha = 0.3 + (i % 4) * 0.16;
      ctx.fillStyle = i % 3 === 0 ? "#a0ecff" : "#fff1a6";
      ctx.beginPath(); ctx.arc(x, y, i % 4 === 0 ? 2.1 : 1.2, 0, Math.PI * 2); ctx.fill();
    }
    ctx.globalAlpha = 1;
  }
  function finishGame(title, subtitle) {
    if (!g || g.over) return;
    g.over = true;
    const s = Math.floor(g.score || 0);
    if (s > bestFor(currentGame)) storageSet("offline-best-" + currentGame, s);
    setScore(s); setHelp(title + " — " + subtitle);
  }
  function drawOverlay(title, subtitle) {
    ctx.fillStyle = "rgba(4,10,24,.73)"; ctx.fillRect(0, 0, 360, 520);
    rounded(26, 176, 308, 166, 23, "#102847", "rgba(99,220,255,.65)");
    text(title, 180, 218, 26, "#fff", 900);
    text(subtitle, 180, 257, 14, "#bcefff", 650);
    rounded(82, 282, 196, 39, 15, "#168ddd", "#61e6ff");
    text("געו כדי לשחק שוב", 180, 301, 14, "#fff", 850);
  }

  /* 1 — חתול רץ */
  function initRunner() {
    g = { t: 0, y: 389, vy: 0, grounded: true, obstacles: [], score: 0, over: false, speed: 4 };
  }
  function jumpRunner() { if (g && !g.over && g.grounded) { g.vy = -11.2; g.grounded = false; } }
  function updateRunner(dt) {
    g.t += dt; g.speed = Math.min(7.4, 4 + g.score / 180);
    g.vy += 0.58 * dt; g.y += g.vy * dt;
    if (g.y >= 389) { g.y = 389; g.vy = 0; g.grounded = true; }
    if (g.t > 56) { g.t = 0; g.obstacles.push({ x: 380, w: rand(24, 35), h: rand(35, 47), kind: "cactus" }); }
    g.obstacles.forEach(o => o.x -= g.speed * dt);
    g.obstacles = g.obstacles.filter(o => o.x > -50);
    g.score += 0.13 * dt;
    for (const o of g.obstacles) {
      if (o.x < 84 && o.x + o.w > 40 && g.y + 22 > 424 - o.h && g.y - 23 < 424) { finishGame("אוי, נתקלתם!", "הניקוד נשמר — נסו שוב"); break; }
    }
  }
  function drawRunner() {
    background("#9bdded", "#ddf6ef");
    ctx.fillStyle="#f4fbf8";
    [[54,84,19],[166,109,14],[269,72,21]].forEach(([x,y,r])=>{ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.arc(x+r*.8,y-5,r*.75,0,Math.PI*2);ctx.arc(x+r*1.5,y,r*.65,0,Math.PI*2);ctx.fill();});
    ctx.fillStyle="#789aaf";ctx.beginPath();ctx.moveTo(0,333);ctx.lineTo(54,281);ctx.lineTo(105,333);ctx.lineTo(168,267);ctx.lineTo(236,333);ctx.lineTo(290,291);ctx.lineTo(360,330);ctx.lineTo(360,520);ctx.lineTo(0,520);ctx.closePath();ctx.fill();
    ctx.fillStyle="#5ca28d";ctx.beginPath();ctx.moveTo(0,375);ctx.quadraticCurveTo(96,337,175,374);ctx.quadraticCurveTo(275,335,360,364);ctx.lineTo(360,520);ctx.lineTo(0,520);ctx.closePath();ctx.fill();
    ctx.fillStyle="#c9b995";ctx.fillRect(0,424,360,96);ctx.fillStyle="#fff1ce";ctx.fillRect(0,424,360,5);ctx.fillStyle="#8a9b91";ctx.fillRect(0,431,360,2);
    for(let x=0;x<400;x+=46){ctx.fillStyle="#f9e8be";ctx.fillRect(x-Math.floor(g.score*2)%46,467,22,3);}
    g.obstacles.forEach(o=>drawCactus(o.x+o.w*.5,424,o.h));
    drawCoin(284,268+Math.sin(g.t*.05)*3,10);
    drawCat(63,g.y+20,0.88,Math.sin(g.t*.18));
    rounded(15,15,128,33,9,"#eef9fa","#bad7d8");text("מרחק  "+Math.floor(g.score),79,32,13,"#234b60",850);
    if(g.over)drawOverlay("הריצה הסתיימה", "מרחק: "+Math.floor(g.score));
  }

    /* 2 — שובר לבנים */
  function initBreakout() {
    const bricks=[];
    for(let r=0;r<5;r++) for(let c=0;c<7;c++) bricks.push({x:13+c*48,y:60+r*28,w:42,h:17,alive:true,color:["#ff668e","#ffad57","#ffe36c","#54e7a4","#48d9ff"][r]});
    g={score:0,over:false,bricks,ball:{x:180,y:350,vx:2.8,vy:-4.1,r:7},paddle:{x:132,y:472,w:96,h:13},lives:3};
    actionButton("◀",()=>{if(g&&!g.over)g.paddle.x=clamp(g.paddle.x-38,0,360-g.paddle.w);});
    actionButton("התחלה מחדש",restartCanvasGame,"primary");
    actionButton("▶",()=>{if(g&&!g.over)g.paddle.x=clamp(g.paddle.x+38,0,360-g.paddle.w);});
  }
  function updateBreakout(dt) {
    const b=g.ball,p=g.paddle;
    b.x+=b.vx*dt;b.y+=b.vy*dt;
    if(b.x<b.r){b.x=b.r;b.vx=Math.abs(b.vx);}
    if(b.x>360-b.r){b.x=360-b.r;b.vx=-Math.abs(b.vx);}
    if(b.y<b.r){b.y=b.r;b.vy=Math.abs(b.vy);}
    if(b.vy>0 && b.y+b.r>=p.y && b.y-b.r<=p.y+p.h && b.x>=p.x-2 && b.x<=p.x+p.w+2){
      b.y=p.y-b.r;b.vy=-Math.abs(b.vy);b.vx=clamp(b.vx+(b.x-(p.x+p.w/2))*.045,-5,5);
    }
    for(const brick of g.bricks){if(brick.alive && b.x+b.r>brick.x && b.x-b.r<brick.x+brick.w && b.y+b.r>brick.y && b.y-b.r<brick.y+brick.h){brick.alive=false;b.vy*=-1;g.score+=10;break;}}
    if(b.y>530){g.lives--;if(g.lives<=0){finishGame("המשחק נגמר", "ניקוד: "+g.score);}else{b.x=p.x+p.w/2;b.y=420;b.vx=rand(-2.8,2.8);b.vy=-4.2;}}
    if(g.bricks.every(bk=>!bk.alive)) finishGame("ניצחון!", "שברתם את כל הלבנים!");
  }
  function drawBreakout(){
    background("#101d4c","#070f25");stars(35,Math.floor(g.score));
    text("נשארו "+g.lives+" חיים",180,29,14,"#a3dfff",850);
    g.bricks.forEach(b=>{if(b.alive){rounded(b.x,b.y,b.w,b.h,5,b.color,"#ffffff40");ctx.fillStyle="#ffffff45";ctx.fillRect(b.x+4,b.y+3,b.w-8,2);}});
    const pg=ctx.createLinearGradient(0,0,0,500);pg.addColorStop(0,"#a2f5ff");pg.addColorStop(1,"#2385ff");
    rounded(g.paddle.x,g.paddle.y,g.paddle.w,g.paddle.h,7,pg,"#fff");
    ctx.shadowBlur=18;ctx.shadowColor="#78e8ff";ctx.fillStyle="#f4fdff";ctx.beginPath();ctx.arc(g.ball.x,g.ball.y,g.ball.r,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;
    text("ניקוד "+g.score,180,505,13,"#8beaff",850);
    if(g.over)drawOverlay("כל הכבוד!", "ניקוד סופי "+g.score);
  }

  /* 3 — קפיצת חלל */
  function initSpace(){
    g={x:180,y:385,vy:-7.3,dir:0,platforms:[],score:0,over:false,t:0};
    for(let i=0;i<8;i++)g.platforms.push({x:rand(16,258),y:454-i*62,w:rand(67,95),h:12});
    const left=actionButton("◀",()=>{if(g)g.dir=-1;});
    const right=actionButton("▶",()=>{if(g)g.dir=1;});
    [[left,-1],[right,1]].forEach(([b,d])=>{b.addEventListener("pointerdown",e=>{e.preventDefault();if(g)g.dir=d;});["pointerup","pointercancel","pointerleave"].forEach(ev=>b.addEventListener(ev,()=>{if(g&&g.dir===d)g.dir=0;}));});
    actionButton("שחרור",()=>{if(g)g.dir=0;});
  }
  function updateSpace(dt){
    const oldY=g.y;
    g.x+=g.dir*4.6*dt;if(g.x<-10)g.x=370;if(g.x>370)g.x=-10;
    g.vy+=.3*dt;g.y+=g.vy*dt;
    if(g.vy>0){for(const p of g.platforms){if(oldY+18<=p.y && g.y+18>=p.y && g.x+13>p.x && g.x-13<p.x+p.w){g.y=p.y-18;g.vy=-8.1;break;}}}
    if(g.y<210){const sh=210-g.y;g.y=210;g.platforms.forEach(p=>p.y+=sh);g.score+=sh*.16;}
    g.platforms.forEach(p=>{if(p.y>525){p.y=rand(-40,15);p.x=rand(8,270);p.w=rand(66,95);}});
    g.t+=dt;if(g.y>525)finishGame("נפלתם מהחלל!", "נסו לקפוץ גבוה יותר");
  }
  function drawSpace(){
    background("#1b275c","#070f20");stars(48,Math.floor(g.score));
    for(let i=0;i<5;i++){ctx.strokeStyle="#8da4ec15";ctx.lineWidth=1;ctx.beginPath();ctx.arc((i*83+42)%360,(i*101+70)%480,30+i%3*12,0,Math.PI*2);ctx.stroke();}
    g.platforms.forEach((p,i)=>{rounded(p.x,p.y,p.w,p.h,5,i%2?"#62d9bf":"#6cbde7","#dcfff7");ctx.fillStyle="#ffffff60";ctx.fillRect(p.x+9,p.y+2,p.w-18,2);ctx.fillStyle="#142c4b";ctx.fillRect(p.x+12,p.y+p.h-1,p.w-24,3);});
    drawAstronaut(g.x,g.y,0.9);
    rounded(14,14,123,32,9,"#1d3159","#566aa5");text("גובה  "+Math.floor(g.score),75,30,12,"#d5e8ff",850);
    if(g.over)drawOverlay("המסע נעצר", "גובה שהושג: "+Math.floor(g.score));
  }

    /* 4 — יורה הבועות: כיוון, קליע, התנגשויות ופיצוץ קבוצות */
  const bubbleColors=["#ff6294","#ffc84e","#43e4be","#58c7ff","#bc87ff"];
  function randomBubbleColor(){return Math.floor(Math.random()*bubbleColors.length);}
  function initBubbles(){
    const grid=[];for(let r=0;r<7;r++){const row=[];for(let c=0;c<7;c++)row.push(r<4?randomBubbleColor():null);grid.push(row);}
    g={grid,score:0,over:false,shot:{x:180,y:465,vx:0,vy:0,color:randomBubbleColor(),flying:false},next:randomBubbleColor(),aim:{x:180,y:120},shots:0};
    setHelp("געו בחלק העליון של הלוח כדי לכוון ולירות. חברו שלוש בועות מאותו צבע לפחות.");
    actionButton("משחק חדש",restartCanvasGame,"primary");
  }
  function shootBubble(x,y){
    if(!g||g.over)return;
    const s=g.shot;if(s.flying)return;
    const dx=x-s.x,dy=y-s.y;
    if(dy>0)return;
    const length=Math.max(1,Math.sqrt(dx*dx+dy*dy));
    s.vx=dx/length*7.3;s.vy=dy/length*7.3;s.flying=true;g.aim={x,y};
  }
  function attachShot(){
    const s=g.shot;
    let c=clamp(Math.round((s.x-36)/48),0,6),r=clamp(Math.round((s.y-62)/38),0,6);
    if(g.grid[r][c]!==null){
      let best=null,dist=Infinity;
      for(let rr=0;rr<7;rr++)for(let cc=0;cc<7;cc++)if(g.grid[rr][cc]===null){const d=(rr-r)*(rr-r)+(cc-c)*(cc-c);if(d<dist){dist=d;best=[rr,cc];}}
      if(best){r=best[0];c=best[1];}else{finishGame("הלוח מלא","הניקוד שלכם: "+g.score);return;}
    }
    g.grid[r][c]=s.color;g.shots++;
    const stack=[[r,c]],seen=new Set([r+","+c]),group=[];
    while(stack.length){const [rr,cc]=stack.pop();group.push([rr,cc]);[[rr-1,cc],[rr+1,cc],[rr,cc-1],[rr,cc+1]].forEach(([nr,nc])=>{const k=nr+","+nc;if(nr>=0&&nr<7&&nc>=0&&nc<7&&!seen.has(k)&&g.grid[nr][nc]===s.color){seen.add(k);stack.push([nr,nc]);}});}
    if(group.length>=3){
      group.forEach(([rr,cc])=>g.grid[rr][cc]=null);g.score+=group.length*15+Math.max(0,group.length-3)*10;
      const connected=new Set(),queue=[];
      for(let cc=0;cc<7;cc++)if(g.grid[0][cc]!==null){queue.push([0,cc]);connected.add("0,"+cc);}
      while(queue.length){const [rr,cc]=queue.shift();[[rr+1,cc],[rr,cc-1],[rr,cc+1],[rr-1,cc]].forEach(([nr,nc])=>{const key=nr+","+nc;if(nr>=0&&nr<7&&nc>=0&&nc<7&&g.grid[nr][nc]!==null&&!connected.has(key)){connected.add(key);queue.push([nr,nc]);}});}
      for(let rr=0;rr<7;rr++)for(let cc=0;cc<7;cc++)if(g.grid[rr][cc]!==null&&!connected.has(rr+","+cc)){g.grid[rr][cc]=null;g.score+=5;}
      setHelp("פגיעה! "+group.length+" בועות התפוצצו.");
    }else setHelp("הבועה נצמדה ללוח. נסו לתכנן את הירייה הבאה.");
    s.flying=false;s.x=180;s.y=465;s.vx=0;s.vy=0;s.color=g.next;g.next=randomBubbleColor();
    if(g.grid.every(row=>row.every(v=>v===null)))finishGame("ניקוי מושלם","הסרתם את כל הבועות!");
    else if(g.grid.some(row=>row.some(v=>v!==null))&&g.grid[6].some(v=>v!==null))finishGame("הבועות הגיעו לתחתית","נסו שוב ולכוונו גבוה יותר");
  }
  function updateBubbles(dt){
    const s=g.shot;if(!s.flying)return;
    s.x+=s.vx*dt;s.y+=s.vy*dt;
    if(s.x<20){s.x=20;s.vx=Math.abs(s.vx);}
    if(s.x>340){s.x=340;s.vx=-Math.abs(s.vx);}
    let hit=s.y<42;
    for(let r=0;r<7&&!hit;r++)for(let c=0;c<7&&!hit;c++)if(g.grid[r][c]!==null){const bx=36+c*48,by=62+r*38;if(Math.hypot(s.x-bx,s.y-by)<29)hit=true;}
    if(hit)attachShot();
  }
  function drawBubble(x,y,color,r){
    const grad=ctx.createRadialGradient(x-r*.37,y-r*.42,1,x,y,r);
    grad.addColorStop(0,"#ffffff");grad.addColorStop(.18,bubbleColors[color]);grad.addColorStop(1,bubbleColors[color]+"c0");
    ctx.fillStyle=grad;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();
    ctx.strokeStyle="#f6ffffa0";ctx.lineWidth=1.2;ctx.stroke();
    ctx.fillStyle="#ffffffa0";ctx.beginPath();ctx.ellipse(x-r*.28,y-r*.32,r*.16,r*.1,-.5,0,Math.PI*2);ctx.fill();
  }
  function drawBubbles(){
    background("#192a57","#0a1529");
    for(let i=0;i<22;i++){ctx.fillStyle="#d1eaff22";ctx.beginPath();ctx.arc((i*79+24)%360,(i*113+50)%520,1+(i%3),0,Math.PI*2);ctx.fill();}
    rounded(12,12,336,31,9,"#162e4d","#2b4968");text("חברו לפחות 3 בועות",180,27,12,"#d5f7ff",800);
    for(let r=0;r<7;r++)for(let c=0;c<7;c++){const v=g.grid[r][c];if(v!==null)drawBubble(36+c*48,62+r*38,v,15.8);}
    const s=g.shot;
    if(!s.flying){ctx.save();ctx.setLineDash([4,5]);ctx.strokeStyle="#c9e6ff88";ctx.lineWidth=1.5;const dx=g.aim.x-s.x,dy=g.aim.y-s.y,len=Math.max(1,Math.hypot(dx,dy));ctx.beginPath();ctx.moveTo(s.x,s.y);ctx.lineTo(s.x+dx/len*140,s.y+dy/len*140);ctx.stroke();ctx.restore();}
    drawBubble(s.x,s.y,s.color,16.5);drawBubble(218,480,g.next,9);
    rounded(18,500,324,1,1,"#42617d");text("הבא",245,480,9,"#95aac5",700);
    if(g.over)drawOverlay("הסיבוב הסתיים", "ניקוד: "+g.score);
  }

  /* 7 — מרוץ מכוניות */
  function initRacer(){
    g={lane:1,obstacles:[],tick:0,score:0,speed:3.1,lives:3,over:false};
    const left=actionButton("◀ שמאלה",()=>moveCar(-1));
    const right=actionButton("ימינה ▶",()=>moveCar(1));
    actionButton("התחלה מחדש",restartCanvasGame,"primary");
    [left,right].forEach(b=>{b.addEventListener("pointerdown",e=>e.preventDefault());});
  }
  function moveCar(d){if(g&&!g.over)g.lane=clamp(g.lane+d,0,2);}
  function updateRacer(dt){
    g.tick+=dt;g.speed=Math.min(7.5,3.1+g.score/220);
    if(g.tick>Math.max(32,66-g.score/12)){g.tick=0;let lane=Math.floor(Math.random()*3);g.obstacles.push({lane,y:-58,color:["#58bed0","#e1b45e","#8c93d8","#e9eef2"][Math.floor(Math.random()*4)],hit:false});}
    g.obstacles.forEach(o=>{o.y+=g.speed*dt;if(!o.hit&&o.lane===g.lane&&o.y>405&&o.y<478){o.hit=true;g.lives--;if(g.lives<=0)finishGame("סוף המרוץ!", "הניקוד שלכם: "+Math.floor(g.score));else{setHelp("התנגשות! נשארו "+g.lives+" ניסיונות.");o.y=520;}}});
    g.obstacles=g.obstacles.filter(o=>o.y<560);g.score+=.12*dt;
  }
  function drawRacer(){
    background("#94c7bf","#e6eee0");
    ctx.fillStyle="#72998c";ctx.fillRect(0,0,360,520);
    for(let i=0;i<7;i++){const y=(i*94+Math.floor(g.score*11)%94)-20;ctx.fillStyle="#466e60";ctx.beginPath();ctx.arc(22+(i%2)*22,y+15,17,0,Math.PI*2);ctx.fill();ctx.beginPath();ctx.arc(326-(i%2)*24,y+34,19,0,Math.PI*2);ctx.fill();}
    ctx.fillStyle="#343c4c";ctx.beginPath();ctx.moveTo(77,0);ctx.lineTo(283,0);ctx.lineTo(350,520);ctx.lineTo(10,520);ctx.closePath();ctx.fill();
    ctx.fillStyle="#c6d4d9";ctx.beginPath();ctx.moveTo(72,0);ctx.lineTo(77,0);ctx.lineTo(17,520);ctx.lineTo(10,520);ctx.closePath();ctx.fill();ctx.beginPath();ctx.moveTo(283,0);ctx.lineTo(288,0);ctx.lineTo(350,520);ctx.lineTo(342,520);ctx.closePath();ctx.fill();
    for(let i=0;i<11;i++){const y=(i*63+Math.floor(g.score*17)%63)-30;const xL=134-y*.035,xR=226+y*.035;ctx.fillStyle="#f0e7bc";ctx.fillRect(xL,y,4,24);ctx.fillRect(xR,y,4,24);}
    const xs=[105,180,255];
    g.obstacles.forEach(o=>drawCar(xs[o.lane],o.y,.82,o.color));
    drawCar(xs[g.lane],453,1.06,"#e45167");
    rounded(12,14,110,35,9,"#14283ced","#556b7c");text("חיים  "+g.lives,67,31,12,"#f5e4e2",850);
    rounded(235,14,113,35,9,"#14283ced","#556b7c");text("מרחק "+Math.floor(g.score),291,31,12,"#e1f7ed",850);
    if(g.over)drawOverlay("המרוץ הסתיים", "המרחק שלכם: "+Math.floor(g.score));
  }

    function updateGame(dt){
    if(currentGame==="runner")updateRunner(dt);
    else if(currentGame==="breakout")updateBreakout(dt);
    else if(currentGame==="space")updateSpace(dt);
    else if(currentGame==="racer")updateRacer(dt);
    else if(currentGame==="bubbles")updateBubbles(dt);
  }
  function drawGame(){
    if(currentGame==="runner")drawRunner();
    else if(currentGame==="breakout")drawBreakout();
    else if(currentGame==="space")drawSpace();
    else if(currentGame==="bubbles")drawBubbles();
    else if(currentGame==="racer")drawRacer();
  }

  /* 5 — זיכרון */
  function initMemory(){
    const faces=["🐱","🐸","🦊","🐼","🐵","🐧","🦄","🐙"];
    const cards=faces.concat(faces).map((face,i)=>({face,key:i,open:false,matched:false}));
    for(let i=cards.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[cards[i],cards[j]]=[cards[j],cards[i]];}
    memoryState={cards,opened:[],moves:0,matches:0,locked:false};
    const board=document.createElement("div");board.className="memory-board";board.id="memoryBoard";
    const meta=document.createElement("div");meta.className="memory-meta";meta.innerHTML='<span id="memoryMoves">מהלכים: 0</span><span id="memoryPairs">זוגות: 0 / 8</span>';
    $("stage").append(board,meta);renderMemory();
    actionButton("משחק חדש",()=>startGame("memory"),"primary");
  }
  function renderMemory(){
    if(currentGame!=="memory"||!memoryState)return;
    const board=$("memoryBoard");if(!board)return;board.innerHTML="";
    memoryState.cards.forEach((card,index)=>{
      const b=document.createElement("button");b.type="button";
      b.className="memory-card"+(card.open?" open":"")+(card.matched?" matched":"")+(card.open||card.matched?"":" back");
      b.textContent=card.open||card.matched?card.face:"?";
      b.setAttribute("aria-label",card.open||card.matched?card.face:"קלף מוסתר");
      b.addEventListener("click",()=>flipMemory(index));board.appendChild(b);
    });
    $("memoryMoves").textContent="מהלכים: "+memoryState.moves;
    $("memoryPairs").textContent="זוגות: "+memoryState.matches+" / 8";
    setScore(memoryState.matches*20);
  }
  function flipMemory(i){
    const s=memoryState;if(!s||s.locked)return;const c=s.cards[i];if(c.open||c.matched)return;
    c.open=true;s.opened.push(i);if(s.opened.length===2){s.moves++;const [a,b]=s.opened.map(n=>s.cards[n]);if(a.face===b.face){a.matched=b.matched=true;s.matches++;s.opened=[];renderMemory();if(s.matches===8)setHelp("🎉 מצאתם את כל הזוגות ב-"+s.moves+" מהלכים!");return;}s.locked=true;renderMemory();setTimeout(()=>{if(currentGame!=="memory"||memoryState!==s)return;s.cards[s.opened[0]].open=false;s.cards[s.opened[1]].open=false;s.opened=[];s.locked=false;renderMemory();},720);return;}
    renderMemory();
  }

  /* 6 — סודוקו */
  const sudokuPuzzle=[
    [5,3,0,0,7,0,0,0,0],[6,0,0,1,9,5,0,0,0],[0,9,8,0,0,0,0,6,0],
    [8,0,0,0,6,0,0,0,3],[4,0,0,8,0,3,0,0,1],[7,0,0,0,2,0,0,0,6],
    [0,6,0,0,0,0,2,8,0],[0,0,0,4,1,9,0,0,5],[0,0,0,0,8,0,0,7,9]
  ];
  const sudokuSolution=[
    [5,3,4,6,7,8,9,1,2],[6,7,2,1,9,5,3,4,8],[1,9,8,3,4,2,5,6,7],
    [8,5,9,7,6,1,4,2,3],[4,2,6,8,5,3,7,9,1],[7,1,3,9,2,4,8,5,6],
    [9,6,1,5,3,7,2,8,4],[2,8,7,4,1,9,6,3,5],[3,4,5,2,8,6,1,7,9]
  ];
  function initSudoku(){
    sudokuState={values:sudokuPuzzle.map(row=>row.slice()),selected:40,wrong:new Set()};
    const wrap=document.createElement("div");wrap.className="sudoku-wrap";wrap.innerHTML='<div class="sudoku-board" id="sudokuBoard"></div><div class="sudoku-numbers" id="sudokuNumbers"></div>';
    $("stage").appendChild(wrap);renderSudoku();
    actionButton("מחיקת מספר",()=>sudokuInput(0),"");
    actionButton("התחלה מחדש",()=>startGame("sudoku"),"primary");
  }
  function renderSudoku(){
    if(currentGame!=="sudoku"||!sudokuState)return;
    const board=$("sudokuBoard"),pad=$("sudokuNumbers");if(!board||!pad)return;board.innerHTML="";pad.innerHTML="";
    const s=sudokuState,sel=s.selected,selVal=s.values[Math.floor(sel/9)][sel%9];
    for(let i=0;i<81;i++){
      const r=Math.floor(i/9),c=i%9,val=s.values[r][c],given=sudokuPuzzle[r][c]!==0,b=document.createElement("button");
      b.type="button";b.className="sudoku-cell"+(given?" given":"")+(sel===i?" selected":"")+(selVal&&val===selVal?" same-num":"")+(s.wrong.has(i)?" wrong":"");
      b.textContent=val||"";b.setAttribute("aria-label","שורה "+(r+1)+", עמודה "+(c+1)+": "+(val||"ריק"));
      b.addEventListener("click",()=>{s.selected=i;renderSudoku();});board.appendChild(b);
    }
    for(let n=1;n<=9;n++){const b=document.createElement("button");b.type="button";b.className="sudoku-num";b.textContent=n;b.addEventListener("click",()=>sudokuInput(n));pad.appendChild(b);}
    setScore(81-s.values.flat().filter(v=>v!==0).length);
  }
  function sudokuInput(n){
    const s=sudokuState;if(!s)return;const r=Math.floor(s.selected/9),c=s.selected%9;if(sudokuPuzzle[r][c]){setHelp("זוהי משבצת רמז קבועה. בחרו משבצת ריקה.");return;}
    s.values[r][c]=n;if(n===0||n===sudokuSolution[r][c])s.wrong.delete(s.selected);else s.wrong.add(s.selected);
    renderSudoku();
    if(s.values.every((row,rr)=>row.every((v,cc)=>v===sudokuSolution[rr][cc]))){setHelp("🎉 פתרתם את הסודוקו! כל הכבוד!");setScore(100);}
    else if(n&&n!==sudokuSolution[r][c])setHelp("המספר לא מתאים למשבצת הזו. אפשר לתקן אותו.");
    else setHelp("יפה! המשיכו למלא את הלוח.");
  }

  /* 8 — איקס עיגול מול מחשב */
  const winLines=[[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
  function initTicTacToe(){
    tttState={board:Array(9).fill(""),done:false,win:[]};
    const wrap=document.createElement("div");wrap.className="ttt-wrap";wrap.innerHTML='<div class="ttt-status" id="tttStatus">התור שלכם — ×</div><div class="ttt-board" id="tttBoard"></div>';
    $("stage").appendChild(wrap);renderTtt();
    actionButton("משחק חדש",()=>startGame("ttt"),"primary");
  }
  function tttWinner(board){
    for(const line of winLines){const [a,b,c]=line;if(board[a]&&board[a]===board[b]&&board[b]===board[c])return {winner:board[a],line};}
    return board.every(Boolean)?{winner:"draw",line:[]}:null;
  }
  function minimax(board,isMax,depth){
    const result=tttWinner(board);if(result){if(result.winner==="O")return 10-depth;if(result.winner==="X")return depth-10;return 0;}
    let best=isMax?-Infinity:Infinity;
    for(let i=0;i<9;i++){if(board[i])continue;board[i]=isMax?"O":"X";const score=minimax(board,!isMax,depth+1);board[i]="";best=isMax?Math.max(best,score):Math.min(best,score);}
    return best;
  }
  function playHuman(i){
    const s=tttState;if(!s||s.done||s.board[i])return;s.board[i]="X";
    let res=tttWinner(s.board);if(res){s.done=true;s.win=res.line;renderTtt();return;}
    renderTtt();$("tttStatus").textContent="המחשב חושב…";
    setTimeout(()=>{if(currentGame!=="ttt"||tttState!==s||s.done)return;let best=-Infinity,move=-1;for(let j=0;j<9;j++){if(s.board[j])continue;s.board[j]="O";const v=minimax(s.board,false,0);s.board[j]="";if(v>best){best=v;move=j;}}if(move>=0)s.board[move]="O";res=tttWinner(s.board);if(res){s.done=true;s.win=res.line;}renderTtt();},240);
  }
  function renderTtt(){
    const s=tttState;if(!s||currentGame!=="ttt")return;const board=$("tttBoard");if(!board)return;board.innerHTML="";
    s.board.forEach((v,i)=>{const b=document.createElement("button");b.type="button";b.className="ttt-cell"+(v==="O"?" o":"")+(s.win.includes(i)?" win":"");b.textContent=v==="X"?"×":v==="O"?"○":"";b.setAttribute("aria-label",v||"משבצת ריקה");b.addEventListener("click",()=>playHuman(i));board.appendChild(b);});
    const res=tttWinner(s.board);if(res){s.done=true;s.win=res.line;$("tttStatus").textContent=res.winner==="X"?"🎉 ניצחתם!":res.winner==="O"?"הפעם המחשב ניצח":"תיקו — משחקים שוב?";setHelp($("tttStatus").textContent);}
    else if(s.board.filter(Boolean).length===0)$("tttStatus").textContent="התור שלכם — ×";
    else if(s.board.filter(Boolean).length%2===1)$("tttStatus").textContent="תור המחשב — ○";
    else $("tttStatus").textContent="התור שלכם — ×";
  }
})();
