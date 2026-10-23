/* אופליין גיים — כל המשחקים והגרפיקה כלולים מקומית */
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const gameInfo = {
    runner: { name: "חתול רץ", help: "געו במסך או לחצו על קפיצה כדי לדלג מעל המכשולים." },
    breakout: { name: "שובר לבנים", help: "גררו את המשטח ימינה ושמאלה כדי להחזיר את הכדור." },
    space: { name: "קפיצת חלל", help: "החזיקו ◀ או ▶ כדי לזוז. נחתו על הפלטפורמות ואל תיפלו!" },
    bubbles: { name: "פוצץ בועות", help: "געו בקבוצה של לפחות שתי בועות מאותו צבע כדי לפוצץ אותן." },
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
  function showHome() {
    clearFrame(); currentGame = ""; g = null; canvas = null; ctx = null;
    $("play").hidden = true; $("home").hidden = false;
    $("stage").innerHTML = ""; $("actions").innerHTML = ""; setHelp("");
  }
  window.showHome = showHome;
  $("backHome").addEventListener("click", showHome);
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
      if (id === "bubbles") popBubble(p.x, p.y);
      if (id === "racer") { if (p.x < 120) moveCar(-1); else if (p.x > 240) moveCar(1); }
    });
    canvas.addEventListener("pointermove", function (e) {
      if (id === "breakout" && g && !g.over && (e.buttons || e.pressure > 0)) {
        const p = pointerPos(e); g.paddle.x = clamp(p.x - g.paddle.w / 2, 0, 360 - g.paddle.w);
      }
    });
    startLoop();
  }
  function restartCanvasGame() { initCanvasGame(currentGame); }
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
    ctx.beginPath(); ctx.roundRect(x, y, w, h, r);
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    if (stroke) { ctx.strokeStyle = stroke; ctx.stroke(); }
  }
  function text(t, x, y, size, color, weight) {
    ctx.font = (weight || 700) + " " + size + "px system-ui, sans-serif";
    ctx.fillStyle = color || "#fff"; ctx.fillText(t, x, y);
  }
  function emoji(t, x, y, size) {
    ctx.font = size + "px system-ui, 'Noto Color Emoji', sans-serif";
    ctx.fillText(t, x, y);
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
    if (g.t > 56) { g.t = 0; g.obstacles.push({ x: 380, w: rand(24, 35), h: rand(35, 47), emoji: Math.random() < .5 ? "🌵" : "🪵" }); }
    g.obstacles.forEach(o => o.x -= g.speed * dt);
    g.obstacles = g.obstacles.filter(o => o.x > -50);
    g.score += 0.13 * dt;
    for (const o of g.obstacles) {
      if (o.x < 84 && o.x + o.w > 40 && g.y + 22 > 424 - o.h && g.y - 23 < 424) { finishGame("אוי, נתקלתם!", "הניקוד נשמר — נסו שוב"); break; }
    }
  }
  function drawRunner() {
    background("#8fe6ff", "#d0f7ff");
    ctx.fillStyle = "#ffffff9c";
    [[55,80,25],[160,116,19],[278,69,24]].forEach(([x,y,r])=>{ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.arc(x+r,y-4,r*.7,0,Math.PI*2);ctx.arc(x+r*1.8,y,r*.8,0,Math.PI*2);ctx.fill();});
    ctx.fillStyle = "#65d99a"; ctx.beginPath(); ctx.moveTo(0,361); ctx.quadraticCurveTo(90,270,175,356); ctx.quadraticCurveTo(273,290,360,345); ctx.lineTo(360,520);ctx.lineTo(0,520);ctx.fill();
    ctx.fillStyle="#2eaa70";ctx.fillRect(0,422,360,98);ctx.fillStyle="#7eed9c";ctx.fillRect(0,422,360,7);
    for(let x=0;x<360;x+=37){ctx.fillStyle="#218f69";ctx.fillRect(x,446,20,5);}
    emoji("🐱",63,g.y+1,44); emoji("🪙",280,260,23);
    g.obstacles.forEach(o=>{emoji(o.emoji,o.x+o.w/2,424-o.h/2+4,35);});
    rounded(15,15,125,34,12,"#ffffffb8");
    text("מרחק " + Math.floor(g.score),77,32,14,"#17456d",900);
    if(g.over) drawOverlay("סוף הריצה!", "הצלחתם להגיע ל-" + Math.floor(g.score));
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
    background("#121e55","#050d23");stars(54,Math.floor(g.score));
    for(let i=0;i<4;i++){ctx.fillStyle="#b9caff15";ctx.beginPath();ctx.arc((i*107+30)%360,(i*139+55)%500,22+i%3*8,0,Math.PI*2);ctx.fill();}
    g.platforms.forEach((p,i)=>{rounded(p.x,p.y,p.w,p.h,6,i%2?"#58ecc0":"#59cfff","#bafff3");ctx.fillStyle="#ffffff66";ctx.fillRect(p.x+9,p.y+2,p.w-18,2);});
    emoji("👨‍🚀",g.x,g.y+1,34);
    rounded(14,14,118,34,12,"#122b55cc","rgba(110,210,255,.5)");text("גובה "+Math.floor(g.score),73,31,13,"#bcf5ff",850);
    if(g.over)drawOverlay("אופס, נפלתם!", "הגובה שלכם: "+Math.floor(g.score));
  }

  /* 4 — פוצץ בועות */
  const bubbleColors=["#ff6294","#ffc84e","#43e4be","#58c7ff","#bc87ff"];
  function initBubbles(){
    const grid=[];for(let r=0;r<8;r++){const row=[];for(let c=0;c<7;c++)row.push(Math.floor(Math.random()*bubbleColors.length));grid.push(row);}
    g={grid,score:0,over:false};setHelp("לחצו על קבוצה של שתי בועות לפחות באותו צבע. קבוצה גדולה = יותר נקודות.");
    actionButton("ערבוב חדש",restartCanvasGame,"primary");
  }
  function popBubble(x,y){
    const c=Math.round((x-24)/52),r=Math.round((y-70)/45);
    if(r<0||r>=8||c<0||c>=7||g.grid[r][c]===null)return;
    const color=g.grid[r][c],stack=[[r,c]],seen=new Set([r+","+c]),group=[];
    while(stack.length){const [rr,cc]=stack.pop();group.push([rr,cc]);[[rr-1,cc],[rr+1,cc],[rr,cc-1],[rr,cc+1]].forEach(([nr,nc])=>{const k=nr+","+nc;if(nr>=0&&nr<8&&nc>=0&&nc<7&&!seen.has(k)&&g.grid[nr][nc]===color){seen.add(k);stack.push([nr,nc]);}});}
    if(group.length<2){setHelp("כמעט! נסו ללחוץ על שתי בועות או יותר מאותו צבע.");return;}
    group.forEach(([rr,cc])=>g.grid[rr][cc]=null);g.score+=group.length*10+Math.max(0,group.length-3)*5;setScore(g.score);
    if(g.grid.every(row=>row.every(v=>v===null))){g.score+=100;finishGame("ניקוי מושלם!", "קיבלתם 100 נקודות בונוס!");}
    else setHelp("פיצוץ של "+group.length+" בועות! המשיכו לחפש קבוצות.");
  }
  function drawBubbles(){
    background("#121f59","#09132f");stars(25,Math.floor(g.score));
    for(let r=0;r<8;r++)for(let c=0;c<7;c++){const val=g.grid[r][c];if(val===null)continue;const x=24+c*52,y=70+r*45;const grad=ctx.createRadialGradient(x-7,y-8,2,x,y,21);grad.addColorStop(0,"#ffffff");grad.addColorStop(.22,bubbleColors[val]);grad.addColorStop(1,bubbleColors[val]+"aa");ctx.fillStyle=grad;ctx.beginPath();ctx.arc(x,y,19,0,Math.PI*2);ctx.fill();ctx.strokeStyle="#ffffff70";ctx.lineWidth=1.7;ctx.stroke();ctx.fillStyle="#ffffff9c";ctx.beginPath();ctx.arc(x-6,y-7,4,0,Math.PI*2);ctx.fill();}
    rounded(15,16,330,31,11,"#142a5c");text("לחצו על בועות תואמות",180,31,13,"#d4f8ff",800);
    if(g.over)drawOverlay("וואו, ניקוי מושלם!", "בונוס הניקוי כבר בניקוד");
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
    if(g.tick>Math.max(32,66-g.score/12)){g.tick=0;let lane=Math.floor(Math.random()*3);g.obstacles.push({lane,y:-58,emoji:Math.random()<.5?"🚙":"🚕",hit:false});}
    g.obstacles.forEach(o=>{o.y+=g.speed*dt;if(!o.hit&&o.lane===g.lane&&o.y>405&&o.y<478){o.hit=true;g.lives--;if(g.lives<=0)finishGame("סוף המרוץ!", "הניקוד שלכם: "+Math.floor(g.score));else{setHelp("התנגשות! נשארו "+g.lives+" ניסיונות.");o.y=520;}}});
    g.obstacles=g.obstacles.filter(o=>o.y<560);g.score+=.12*dt;
  }
  function drawRacer(){
    background("#84dfff","#e1f8ff");
    ctx.fillStyle="#8dd3a2";ctx.fillRect(0,0,360,520);
    ctx.fillStyle="#343b51";ctx.beginPath();ctx.moveTo(75,0);ctx.lineTo(285,0);ctx.lineTo(345,520);ctx.lineTo(15,520);ctx.closePath();ctx.fill();
    ctx.fillStyle="#e4edf7";ctx.beginPath();ctx.moveTo(70,0);ctx.lineTo(77,0);ctx.lineTo(18,520);ctx.lineTo(8,520);ctx.fill();ctx.beginPath();ctx.moveTo(283,0);ctx.lineTo(290,0);ctx.lineTo(352,520);ctx.lineTo(342,520);ctx.fill();
    for(let i=0;i<9;i++){let y=(i*75+(g.score*13)%75)-20;ctx.fillStyle="#ffffffbd";ctx.fillRect(148,y,5,34);ctx.fillRect(207,y,5,34);}
    const xs=[105,180,255];
    g.obstacles.forEach(o=>{emoji(o.emoji,xs[o.lane],o.y+5,34);});
    emoji("🏎️",xs[g.lane],453,42);
    rounded(12,14,135,38,12,"#142441dd");text("❤️".repeat(g.lives),80,33,17,"#fff",800);
    rounded(235,14,113,38,12,"#142441dd");text("מרחק "+Math.floor(g.score),291,33,13,"#fff",800);
    if(g.over)drawOverlay("מרוץ הסתיים!", "עברתם מרחק "+Math.floor(g.score));
  }

  function updateGame(dt){
    if(currentGame==="runner")updateRunner(dt);
    else if(currentGame==="breakout")updateBreakout(dt);
    else if(currentGame==="space")updateSpace(dt);
    else if(currentGame==="racer")updateRacer(dt);
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
    actionButton("משחק חדש",initMemory,"primary");
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
    actionButton("התחלה מחדש",initSudoku,"primary");
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
    actionButton("משחק חדש",initTicTacToe,"primary");
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

  function restartCanvasGame(){initCanvasGame(currentGame);}
})();
