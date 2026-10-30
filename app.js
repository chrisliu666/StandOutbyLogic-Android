(function () {
  "use strict";

  const STORAGE_RECORDS = "stand-out-by-logic.records";
  const STORAGE_SEEDS = "stand-out-by-logic.seeds";
  const STORAGE_GUIDE_SEEN = "stand-out-by-logic.guideSeen";
  const MAX_SAVED_SEEDS = 60;
  const SEED_RETRY_PASSES = 3;

  const difficultyBlanks = {
    easy: 36,
    medium: 44,
    hard: 52,
    expert: 58,
  };

  const difficultyNames = {
    easy: "简单",
    medium: "普通",
    hard: "困难",
    expert: "专家",
  };

  const els = {
    board: document.getElementById("board"),
    numberProgress: document.getElementById("numberProgress"),
    timer: document.getElementById("timer"),
    mistakes: document.getElementById("mistakes"),
    hints: document.getElementById("hints"),
    seedInput: document.getElementById("seedInput"),
    loadSeedBtn: document.getElementById("loadSeedBtn"),
    difficulty: document.getElementById("difficulty"),
    newGameBtn: document.getElementById("newGameBtn"),
    confirmBtn: document.getElementById("confirmBtn"),
    clearBtn: document.getElementById("clearBtn"),
    hintBtn: document.getElementById("hintBtn"),
    completeBtn: document.getElementById("completeBtn"),
    undoBtn: document.getElementById("undoBtn"),
    redoBtn: document.getElementById("redoBtn"),
    resetBtn: document.getElementById("resetBtn"),
    undoResetBtn: document.getElementById("undoResetBtn"),
    againBtn: document.getElementById("againBtn"),
    clearRecordsBtn: document.getElementById("clearRecordsBtn"),
    recordsList: document.getElementById("recordsList"),
    mascotLine: document.getElementById("mascotLine"),
    toast: document.getElementById("toast"),
    guidePanel: document.getElementById("guidePanel"),
    guideToggle: document.getElementById("guideToggle"),
    guideBody: document.getElementById("guideBody"),
  };

  const state = {
    puzzle: [],
    solution: [],
    values: [],
    statuses: [],
    selected: null,
    highlightedValue: 0,
    mistakes: 0,
    hints: 3,
    seed: "",
    difficulty: "medium",
    startedAt: Date.now(),
    ended: false,
    timerId: null,
    undoStack: [],
    redoStack: [],
    resetSnapshot: null,
    records: loadJson(STORAGE_RECORDS, []),
    seedStore: loadSeedStore(),
    longPressTimer: null,
    longPressTriggered: false,
    hiddenAt: null,
    guideExpanded: false,
  };

  const mascotLines = {
    ready: "选一个格子，我会帮你盯住同样的数字。",
    good: "答案正确，逻辑闪闪发光。",
    bad: "这个数字不对，再观察一下宫和列。",
    hint: "提示已经填好啦，继续保持节奏。",
    fail: "这局失败了，换个种子重新来过。",
    win: "通关成功，今天的推理也很漂亮。",
  };

  init();

  function init() {
    buildBoard();
    buildNumberProgress();
    bindActions();
    bindVisibilityPause();
    bindKeyboardInput();
    initGuide();
    renderRecords();
    startGame();
  }

  function buildBoard() {
    els.board.innerHTML = "";
    for (let index = 0; index < 81; index += 1) {
      const cell = document.createElement("button");
      cell.type = "button";
      cell.className = "cell";
      cell.dataset.index = String(index);
      cell.setAttribute("aria-label", `第 ${Math.floor(index / 9) + 1} 行第 ${(index % 9) + 1} 列`);
      if ((index % 9) === 2 || (index % 9) === 5) {
        cell.classList.add("box-right");
      }
      if (Math.floor(index / 9) === 2 || Math.floor(index / 9) === 5) {
        cell.classList.add("box-bottom");
      }
      cell.addEventListener("click", handleCellClick);
      cell.addEventListener("dblclick", handleCellDoubleClick);
      cell.addEventListener("contextmenu", (event) => event.preventDefault());
      cell.addEventListener("mousedown", handleCellMouseDown);
      cell.addEventListener("mouseup", handleCellMouseUp);
      cell.addEventListener("mouseleave", clearLongPress);
      cell.addEventListener("wheel", handleCellWheel, { passive: false });
      els.board.appendChild(cell);
    }
  }

  function buildNumberProgress() {
    els.numberProgress.innerHTML = "";
    for (let value = 1; value <= 9; value += 1) {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "number-progress-item";
      item.dataset.value = String(value);
      item.setAttribute("aria-label", `填入数字 ${value}`);
      item.innerHTML = `<strong>${value}</strong><span>0/9</span>`;
      item.addEventListener("click", () => applyNumberInput(value));
      els.numberProgress.appendChild(item);
    }
  }

  function bindActions() {
    els.newGameBtn.addEventListener("click", () => startGame());
    els.againBtn.addEventListener("click", () => startGame());
    els.confirmBtn.addEventListener("click", confirmSelected);
    els.clearBtn.addEventListener("click", clearSelectedCell);
    els.hintBtn.addEventListener("click", useHint);
    els.completeBtn.addEventListener("click", completeGame);
    els.undoBtn.addEventListener("click", undo);
    els.redoBtn.addEventListener("click", redo);
    els.resetBtn.addEventListener("click", resetGame);
    els.undoResetBtn.addEventListener("click", undoReset);
    els.loadSeedBtn.addEventListener("click", () => {
      const seed = els.seedInput.value.trim();
      if (!seed) {
        showToast("请输入种子码");
        return;
      }
      startGame(seed);
    });
    els.clearRecordsBtn.addEventListener("click", () => {
      state.records = [];
      saveJson(STORAGE_RECORDS, state.records);
      renderRecords();
    });
    els.guideToggle.addEventListener("click", () => setGuideExpanded(!state.guideExpanded));
  }

  function initGuide() {
    let seen = true;
    try {
      seen = localStorage.getItem(STORAGE_GUIDE_SEEN) === "1";
      if (!seen) {
        localStorage.setItem(STORAGE_GUIDE_SEEN, "1");
      }
    } catch {
      seen = false;
    }
    setGuideExpanded(!seen);
  }

  function setGuideExpanded(expanded) {
    state.guideExpanded = expanded;
    els.guidePanel.classList.toggle("collapsed", !expanded);
    els.guideToggle.setAttribute("aria-expanded", String(expanded));
    els.guideBody.setAttribute("aria-hidden", String(!expanded));
    els.guideBody.inert = !expanded;
  }

  function bindVisibilityPause() {
    document.addEventListener("visibilitychange", handleVisibilityChange);
  }

  function bindKeyboardInput() {
    document.addEventListener("keydown", handleKeydown);
  }

  function applyNumberInput(value) {
    if (state.ended) {
      return;
    }
    if (state.selected === null) {
      showToast("请先选择一个格子");
      return;
    }
    if (!canEdit(state.selected)) {
      showToast("该格不可修改");
      return;
    }
    setCellValue(state.selected, value);
  }

  function handleKeydown(event) {
    if (event.repeat || event.ctrlKey || event.altKey || event.metaKey) {
      return;
    }
    const target = event.target;
    const targetEl = target instanceof HTMLElement ? target : null;
    if (targetEl) {
      const tag = targetEl.tagName;
      if (tag === "INPUT" || tag === "SELECT" || tag === "TEXTAREA" || targetEl.isContentEditable) {
        return;
      }
    }
    if (isArrowKey(event.key)) {
      if (
        !targetEl ||
        targetEl === document.body ||
        targetEl === document.documentElement ||
        targetEl.classList.contains("cell")
      ) {
        event.preventDefault();
        moveBoardSelection(event.key);
        return;
      }
      if (targetEl.classList.contains("number-progress-item")) {
        event.preventDefault();
        moveNumberBarFocus(event.key);
        return;
      }
      if (targetEl.closest(".control-panel")) {
        event.preventDefault();
        moveControlPanelFocus(event.key);
      }
      return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      focusSelectedCell();
      return;
    }
    if (event.key === "Enter") {
      if (targetEl && targetEl.tagName === "BUTTON" && !targetEl.classList.contains("cell")) {
        return;
      }
      event.preventDefault();
      confirmSelected();
      return;
    }
    if (event.key === "Delete" || event.key === "Backspace") {
      event.preventDefault();
      clearSelectedCell();
      return;
    }
    if (event.key === "t" || event.key === "T") {
      event.preventDefault();
      useHint();
      return;
    }
    if (!/^[1-9]$/.test(event.key)) {
      return;
    }
    event.preventDefault();
    applyNumberInput(Number(event.key));
  }

  function clearSelectedCell() {
    if (state.ended) {
      return;
    }
    if (state.selected === null) {
      showToast("请先选择一个格子");
      return;
    }
    if (!canEdit(state.selected)) {
      showToast("该格不可修改");
      return;
    }
    clearCell(state.selected);
  }

  function isArrowKey(key) {
    return key === "ArrowUp" || key === "ArrowDown" || key === "ArrowLeft" || key === "ArrowRight";
  }

  function moveBoardSelection(key) {
    if (key === "ArrowRight" && state.selected !== null && state.selected % 9 === 8) {
      focusControlPanel();
      return;
    }
    const start = state.selected === null ? 40 : state.selected;
    const next = stepBoardIndex(start, key);
    selectCell(next);
    els.board.children[next].focus();
  }

  function focusControlPanel() {
    const panel = document.querySelector(".control-panel");
    if (!panel) {
      return;
    }
    const items = [...panel.querySelectorAll("button:not(:disabled)")].filter(
      (item) => item.getClientRects().length > 0
    );
    if (items.length > 0) {
      items[0].focus();
    }
  }

  function stepBoardIndex(index, key) {
    const row = Math.floor(index / 9);
    const col = index % 9;
    if (key === "ArrowUp") {
      return row > 0 ? index - 9 : index;
    }
    if (key === "ArrowDown") {
      return row < 8 ? index + 9 : index;
    }
    if (key === "ArrowLeft") {
      return col > 0 ? index - 1 : index;
    }
    return col < 8 ? index + 1 : index;
  }

  function focusSelectedCell() {
    if (state.selected === null) {
      selectCell(40);
    }
    const cell = els.board.children[state.selected];
    if (cell) {
      cell.focus();
    }
  }

  function moveNumberBarFocus(key) {
    const items = [...els.numberProgress.querySelectorAll(".number-progress-item")];
    if (items.length === 0) {
      return;
    }
    const currentIndex = items.indexOf(document.activeElement);
    if (currentIndex === -1) {
      items[0].focus();
      return;
    }
    const direction = key === "ArrowLeft" || key === "ArrowUp" ? -1 : 1;
    const nextIndex = Math.min(items.length - 1, Math.max(0, currentIndex + direction));
    items[nextIndex].focus();
  }

  function moveControlPanelFocus(key) {
    const panel = document.querySelector(".control-panel");
    if (!panel) {
      return;
    }
    const items = [...panel.querySelectorAll("button:not(:disabled)")].filter(
      (item) => item.getClientRects().length > 0
    );
    if (items.length === 0) {
      return;
    }
    const current = document.activeElement;
    if (!items.includes(current)) {
      items[0].focus();
      return;
    }
    const neighbor = findDirectionalNeighbor(items, current, key);
    if (neighbor) {
      neighbor.focus();
      return;
    }
    if (key === "ArrowLeft") {
      focusSelectedCell();
    }
  }

  function findDirectionalNeighbor(items, current, key) {
    const rect = current.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const vertical = key === "ArrowUp" || key === "ArrowDown";
    const candidates = [];
    items.forEach((item) => {
      if (item === current) {
        return;
      }
      const other = item.getBoundingClientRect();
      if (!other.width || !other.height) {
        return;
      }
      const dx = other.left + other.width / 2 - cx;
      const dy = other.top + other.height / 2 - cy;
      let primary;
      if (key === "ArrowUp") {
        primary = -dy;
      } else if (key === "ArrowDown") {
        primary = dy;
      } else if (key === "ArrowLeft") {
        primary = -dx;
      } else {
        primary = dx;
      }
      if (primary <= 1) {
        return;
      }
      const secondary = vertical ? Math.abs(dx) : Math.abs(dy);
      const aligned = vertical
        ? rect.left < other.right && other.left < rect.right
        : rect.top < other.bottom && other.top < rect.bottom;
      candidates.push({ item, primary, secondary, aligned });
    });
    if (candidates.length === 0) {
      return null;
    }
    const aligned = candidates.filter((candidate) => candidate.aligned);
    const pool = aligned.length > 0 ? aligned : candidates;
    pool.sort((a, b) => a.primary - b.primary || a.secondary - b.secondary);
    return pool[0].item;
  }

  function handleVisibilityChange() {
    if (document.hidden) {
      if (state.ended) {
        return;
      }
      state.hiddenAt = Date.now();
      window.clearInterval(state.timerId);
      state.timerId = null;
      return;
    }
    if (state.hiddenAt !== null) {
      state.startedAt += Date.now() - state.hiddenAt;
      state.hiddenAt = null;
    }
    if (state.ended) {
      els.timer.textContent = formatTime(elapsedSeconds());
    } else {
      restartTimer();
    }
  }

  function startGame(requestedSeed) {
    state.difficulty = els.difficulty.value;
    state.seed = requestedSeed || createSeed(state.difficulty);
    state.selected = null;
    state.highlightedValue = 0;
    state.mistakes = 0;
    state.hints = 3;
    state.ended = false;
    state.undoStack = [];
    state.redoStack = [];
    state.resetSnapshot = null;
    state.startedAt = Date.now();
    state.hiddenAt = document.hidden ? Date.now() : null;
    els.againBtn.classList.add("hidden");
    els.seedInput.value = state.seed;
    setMascot("ready");
    showToast("正在准备题目");

    const stored = state.seedStore[state.seed];
    let game = stored || null;
    if (!game) {
      game = createSeededGame(state.seed, state.difficulty);
      game.savedAt = Date.now();
      game.lastUsedAt = game.savedAt;
      state.seedStore[state.seed] = game;
    } else {
      if (difficultyNames[game.difficulty]) {
        state.difficulty = game.difficulty;
        els.difficulty.value = game.difficulty;
      } else {
        game.difficulty = state.difficulty;
      }
      game.lastUsedAt = Date.now();
    }
    saveSeedStore();

    state.puzzle = game.puzzle.slice();
    state.solution = game.solution.slice();
    state.values = game.puzzle.slice();
    state.statuses = game.puzzle.map((value) => (value ? "given" : "empty"));
    restartTimer();
    render();
  }

  function createSeededGame(seed, difficulty) {
    const rng = mulberry32(hashString(seed));
    const base = [
      1, 2, 3, 4, 5, 6, 7, 8, 9,
      4, 5, 6, 7, 8, 9, 1, 2, 3,
      7, 8, 9, 1, 2, 3, 4, 5, 6,
      2, 3, 4, 5, 6, 7, 8, 9, 1,
      5, 6, 7, 8, 9, 1, 2, 3, 4,
      8, 9, 1, 2, 3, 4, 5, 6, 7,
      3, 4, 5, 6, 7, 8, 9, 1, 2,
      6, 7, 8, 9, 1, 2, 3, 4, 5,
      9, 1, 2, 3, 4, 5, 6, 7, 8,
    ];
    const digitMap = shuffle([1, 2, 3, 4, 5, 6, 7, 8, 9], rng);
    let solution = base.map((value) => digitMap[value - 1]);
    solution = shuffleRowsAndColumns(solution, rng);
    const holes = difficultyBlanks[difficulty] || difficultyBlanks.medium;
    const puzzle = createPuzzleWithUniqueSolution(solution, holes, rng);
    return { puzzle, solution, difficulty };
  }

  function createPuzzleWithUniqueSolution(solution, targetHoles, rng) {
    let best = null;
    for (let pass = 0; pass < SEED_RETRY_PASSES; pass += 1) {
      const puzzle = solution.slice();
      let removed = 0;
      const positions = shuffle([...Array(81).keys()], rng);
      for (const index of positions) {
        if (removed >= targetHoles) {
          break;
        }
        const value = puzzle[index];
        puzzle[index] = 0;
        if (countSolutions(puzzle) === 1) {
          removed += 1;
        } else {
          puzzle[index] = value;
        }
      }
      if (!best || removed > best.removed) {
        best = { puzzle, removed };
      }
      if (removed >= targetHoles) {
        break;
      }
    }
    return best.puzzle;
  }

  function countSolutions(puzzle) {
    const grid = puzzle.slice();
    const rows = new Array(9).fill(0);
    const cols = new Array(9).fill(0);
    const boxes = new Array(9).fill(0);
    for (let index = 0; index < 81; index += 1) {
      const value = grid[index];
      if (!value) {
        continue;
      }
      const row = Math.floor(index / 9);
      const col = index % 9;
      const box = Math.floor(row / 3) * 3 + Math.floor(col / 3);
      const bit = 1 << (value - 1);
      if ((rows[row] & bit) || (cols[col] & bit) || (boxes[box] & bit)) {
        return 0;
      }
      rows[row] |= bit;
      cols[col] |= bit;
      boxes[box] |= bit;
    }
    return searchSolutions(grid, rows, cols, boxes, 2);
  }

  function searchSolutions(grid, rows, cols, boxes, limit) {
    if (limit <= 0) {
      return 0;
    }
    let bestIndex = -1;
    let bestMask = 0;
    let bestCount = 10;
    for (let index = 0; index < 81; index += 1) {
      if (grid[index]) {
        continue;
      }
      const row = Math.floor(index / 9);
      const col = index % 9;
      const box = Math.floor(row / 3) * 3 + Math.floor(col / 3);
      const mask = ~(rows[row] | cols[col] | boxes[box]) & 0x1ff;
      if (mask === 0) {
        return 0;
      }
      let count = 0;
      let bits = mask;
      while (bits) {
        count += 1;
        bits &= bits - 1;
      }
      if (count < bestCount) {
        bestIndex = index;
        bestMask = mask;
        bestCount = count;
        if (count === 1) {
          break;
        }
      }
    }
    if (bestIndex === -1) {
      return 1;
    }
    const row = Math.floor(bestIndex / 9);
    const col = bestIndex % 9;
    const box = Math.floor(row / 3) * 3 + Math.floor(col / 3);
    let total = 0;
    let bits = bestMask;
    while (bits) {
      const bit = bits & -bits;
      bits ^= bit;
      grid[bestIndex] = 32 - Math.clz32(bit);
      rows[row] |= bit;
      cols[col] |= bit;
      boxes[box] |= bit;
      total += searchSolutions(grid, rows, cols, boxes, limit - total);
      grid[bestIndex] = 0;
      rows[row] ^= bit;
      cols[col] ^= bit;
      boxes[box] ^= bit;
      if (total >= limit) {
        break;
      }
    }
    return total;
  }

  function shuffleRowsAndColumns(grid, rng) {
    const rowBands = shuffle([0, 1, 2], rng);
    const colBands = shuffle([0, 1, 2], rng);
    const rows = rowBands.flatMap((band) => shuffle([0, 1, 2], rng).map((row) => band * 3 + row));
    const cols = colBands.flatMap((band) => shuffle([0, 1, 2], rng).map((col) => band * 3 + col));
    const output = [];
    rows.forEach((row) => {
      cols.forEach((col) => output.push(grid[row * 9 + col]));
    });
    return output;
  }

  function handleCellClick(event) {
    if (state.longPressTriggered) {
      state.longPressTriggered = false;
      return;
    }
    const index = Number(event.currentTarget.dataset.index);
    selectCell(index);
  }

  function handleCellDoubleClick(event) {
    const index = Number(event.currentTarget.dataset.index);
    selectCell(index);
    setCellValue(index, 1);
  }

  function handleCellMouseDown(event) {
    const index = Number(event.currentTarget.dataset.index);
    selectCell(index);
    if (event.button === 2) {
      event.preventDefault();
      startLongPress(() => confirmCell(index));
      return;
    }
    if (event.button === 0) {
      startLongPress(() => clearCell(index));
    }
  }

  function handleCellMouseUp(event) {
    const index = Number(event.currentTarget.dataset.index);
    const wasLongPress = state.longPressTriggered;
    clearLongPress();
    if (event.button === 2 && !wasLongPress) {
      event.preventDefault();
      changeCell(index, -1);
    } else if (event.button === 2 && wasLongPress) {
      state.longPressTriggered = false;
    }
  }

  function handleCellWheel(event) {
    event.preventDefault();
    const index = Number(event.currentTarget.dataset.index);
    selectCell(index);
    changeCell(index, event.deltaY < 0 ? 1 : -1);
  }

  function startLongPress(action) {
    clearLongPress();
    state.longPressTriggered = false;
    state.longPressTimer = window.setTimeout(() => {
      state.longPressTriggered = true;
      action();
    }, 620);
  }

  function clearLongPress() {
    if (state.longPressTimer) {
      window.clearTimeout(state.longPressTimer);
      state.longPressTimer = null;
    }
  }

  function selectCell(index) {
    state.selected = index;
    state.highlightedValue = state.values[index] || 0;
    render();
  }

  function changeCell(index, delta) {
    if (!canEdit(index)) {
      return;
    }
    const next = cycleValue(state.values[index] + delta);
    setCellValue(index, next);
  }

  function setCellValue(index, value) {
    if (!canEdit(index)) {
      return;
    }
    const before = snapshotCell(index);
    state.values[index] = value;
    state.statuses[index] = value ? "draft" : "empty";
    state.highlightedValue = value;
    pushHistory({ type: "cell", index, before, after: snapshotCell(index) });
    state.redoStack = [];
    render();
    animateCell(index, "bump");
  }

  function clearCell(index) {
    if (!canEdit(index)) {
      return;
    }
    const before = snapshotCell(index);
    state.values[index] = 0;
    state.statuses[index] = "empty";
    state.highlightedValue = 0;
    pushHistory({ type: "cell", index, before, after: snapshotCell(index) });
    state.redoStack = [];
    showToast("已清除");
    render();
  }

  function confirmSelected() {
    if (state.selected === null) {
      showToast("请先选择一个格子");
      return;
    }
    confirmCell(state.selected);
  }

  function confirmCell(index) {
    if (state.ended || state.statuses[index] === "given" || state.statuses[index] === "hinted") {
      return;
    }
    let confirmed = false;
    const value = state.values[index];
    if (!value) {
      showToast("空格不能确认");
      return;
    }
    if (value === state.solution[index]) {
      const before = snapshotCell(index);
      state.statuses[index] = "confirmed";
      pushHistory({ type: "cell", index, before, after: snapshotCell(index) });
      state.redoStack = [];
      confirmed = true;
      setMascot("good");
      showToast("正确");
      checkWin();
    } else {
      state.mistakes += 1;
      setMascot("bad");
      showToast(`错误，还剩 ${Math.max(0, 3 - state.mistakes)} 次机会`);
      if (state.mistakes >= 3) {
        finishGame("失败");
      }
    }
    render();
    if (confirmed) {
      animateCell(index, "spark");
    }
  }

  function useHint() {
    if (state.ended) {
      return;
    }
    if (state.hints <= 0) {
      showToast("提示次数已用完");
      return;
    }
    const index = pickHintIndex();
    if (index === null) {
      showToast("没有可提示的格子");
      return;
    }
    const before = snapshotCell(index);
    state.values[index] = state.solution[index];
    state.statuses[index] = "hinted";
    state.hints -= 1;
    state.selected = index;
    state.highlightedValue = state.values[index];
    pushHistory({ type: "hint", index, before, after: snapshotCell(index), hintsBefore: state.hints + 1, hintsAfter: state.hints });
    state.redoStack = [];
    setMascot("hint");
    showToast("提示已填入");
    checkWin();
    render();
    animateCell(index, "spark");
  }

  function pickHintIndex() {
    if (state.selected !== null && canEdit(state.selected)) {
      return state.selected;
    }
    return state.values.findIndex((value, index) => value !== state.solution[index] && state.statuses[index] !== "given");
  }

  function completeGame() {
    if (state.ended) {
      return;
    }
    const before = snapshotGame();
    state.values = state.solution.slice();
    state.statuses = state.solution.map((_, index) => (state.puzzle[index] ? "given" : "confirmed"));
    state.selected = null;
    state.highlightedValue = 0;
    pushHistory({ type: "game", before, after: snapshotGame() });
    state.redoStack = [];
    showToast("已一键完成");
    finishGame("成功");
    render();
  }

  function resetGame() {
    const wasEnded = state.ended;
    state.resetSnapshot = snapshotGame();
    state.values = state.puzzle.slice();
    state.statuses = state.puzzle.map((value) => (value ? "given" : "empty"));
    state.selected = null;
    state.highlightedValue = 0;
    state.undoStack = [];
    state.redoStack = [];
    if (wasEnded) {
      state.hints = 3;
      state.mistakes = 0;
      state.ended = false;
      state.startedAt = Date.now();
      state.hiddenAt = document.hidden ? Date.now() : null;
      els.againBtn.classList.add("hidden");
      restartTimer();
      setMascot("ready");
      showToast("已重置并重新计时，可撤销重置");
    } else {
      showToast("已清空填数，错误与提示次数保留");
    }
    render();
  }

  function undoReset() {
    if (!state.resetSnapshot) {
      showToast("没有可撤销的重置");
      return;
    }
    restoreGame(state.resetSnapshot);
    state.resetSnapshot = null;
    showToast("已撤销重置");
    render();
  }

  function undo() {
    const action = state.undoStack.pop();
    if (!action || state.ended) {
      return;
    }
    applyHistory(action, "before");
    state.redoStack.push(action);
    render();
  }

  function redo() {
    const action = state.redoStack.pop();
    if (!action || state.ended) {
      return;
    }
    applyHistory(action, "after");
    state.undoStack.push(action);
    render();
  }

  function applyHistory(action, side) {
    if (action.type === "game") {
      restoreGame(action[side]);
      return;
    }
    if (action.type === "hint") {
      state.hints = side === "before" ? action.hintsBefore : action.hintsAfter;
    }
    state.values[action.index] = action[side].value;
    state.statuses[action.index] = action[side].status;
    state.selected = action.index;
    state.highlightedValue = state.values[action.index];
  }

  function checkWin() {
    const solved = state.values.every((value, index) => value === state.solution[index]);
    if (solved) {
      finishGame("成功");
    }
  }

  function finishGame(result) {
    if (state.ended) {
      return;
    }
    state.ended = true;
    state.hiddenAt = null;
    window.clearInterval(state.timerId);
    const seconds = elapsedSeconds();
    state.records.unshift({
      result,
      seconds,
      seed: state.seed,
      difficulty: state.difficulty,
      at: new Date().toISOString(),
    });
    state.records = state.records.slice(0, 20);
    saveJson(STORAGE_RECORDS, state.records);
    renderRecords();
    if (result === "失败") {
      els.againBtn.classList.remove("hidden");
      setMascot("fail");
    } else {
      setMascot("win");
    }
  }

  function render() {
    [...els.board.children].forEach((cell, index) => {
      const value = state.values[index];
      const status = state.statuses[index];
      cell.textContent = "";
      if (value) {
        const digit = document.createElement("span");
        digit.className = `digit d${value}`;
        digit.textContent = String(value);
        cell.appendChild(digit);
      }
      cell.disabled = state.ended;
      cell.setAttribute("aria-disabled", String(status === "given" || status === "confirmed" || status === "hinted"));
      cell.className = "cell";
      if ((index % 9) === 2 || (index % 9) === 5) {
        cell.classList.add("box-right");
      }
      if (Math.floor(index / 9) === 2 || Math.floor(index / 9) === 5) {
        cell.classList.add("box-bottom");
      }
      if (status !== "empty") {
        cell.classList.add(status);
      }
      if (index === state.selected) {
        cell.classList.add("selected");
      }
      if (state.selected !== null && isRelated(index, state.selected)) {
        cell.classList.add("related");
      }
      if (state.highlightedValue && value === state.highlightedValue) {
        cell.classList.add("same");
      }
    });
    els.mistakes.textContent = `错误 ${state.mistakes}/3`;
    els.hints.textContent = `提示 ${state.hints}`;
    els.hintBtn.disabled = state.hints <= 0 || state.ended;
    els.confirmBtn.disabled = state.ended;
    els.completeBtn.disabled = state.ended;
    els.undoBtn.disabled = state.undoStack.length === 0 || state.ended;
    els.redoBtn.disabled = state.redoStack.length === 0 || state.ended;
    els.undoResetBtn.disabled = !state.resetSnapshot || state.ended;
    renderNumberProgress();
  }

  function renderNumberProgress() {
    const counts = Array(10).fill(0);
    state.values.forEach((value, index) => {
      const fixed = state.statuses[index] === "given" || state.statuses[index] === "confirmed" || state.statuses[index] === "hinted";
      if (fixed && value >= 1 && value <= 9) {
        counts[value] += 1;
      }
    });
    [...els.numberProgress.children].forEach((item) => {
      const value = Number(item.dataset.value);
      const count = counts[value];
      item.querySelector("span").textContent = `${count}/9`;
      item.classList.toggle("filled", count >= 9);
    });
  }

  function renderRecords() {
    els.recordsList.innerHTML = "";
    if (state.records.length === 0) {
      const empty = document.createElement("li");
      empty.textContent = "暂无记录";
      els.recordsList.appendChild(empty);
      return;
    }
    state.records.forEach((record) => {
      const item = document.createElement("li");
      item.textContent = `${record.result}｜${difficultyNames[record.difficulty] || record.difficulty}｜${formatTime(record.seconds)}｜${record.seed}`;
      item.title = "点击复制种子码";
      item.addEventListener("click", async () => {
        els.seedInput.value = record.seed;
        try {
          await navigator.clipboard.writeText(record.seed);
          showToast("种子码已复制");
        } catch {
          showToast("种子码已填入输入框");
        }
      });
      els.recordsList.appendChild(item);
    });
  }

  function restartTimer() {
    window.clearInterval(state.timerId);
    state.timerId = window.setInterval(() => {
      els.timer.textContent = formatTime(elapsedSeconds());
    }, 500);
    els.timer.textContent = formatTime(elapsedSeconds());
  }

  function elapsedSeconds() {
    return Math.floor((Date.now() - state.startedAt) / 1000);
  }

  function formatTime(totalSeconds) {
    const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
    const seconds = String(totalSeconds % 60).padStart(2, "0");
    return `${minutes}:${seconds}`;
  }

  function canEdit(index) {
    return !state.ended && state.statuses[index] !== "given" && state.statuses[index] !== "confirmed" && state.statuses[index] !== "hinted";
  }

  function cycleValue(value) {
    if (value > 9) {
      return 0;
    }
    if (value < 0) {
      return 9;
    }
    return value;
  }

  function isRelated(a, b) {
    if (a === b) {
      return false;
    }
    const rowA = Math.floor(a / 9);
    const rowB = Math.floor(b / 9);
    const colA = a % 9;
    const colB = b % 9;
    const boxA = Math.floor(rowA / 3) * 3 + Math.floor(colA / 3);
    const boxB = Math.floor(rowB / 3) * 3 + Math.floor(colB / 3);
    return rowA === rowB || colA === colB || boxA === boxB;
  }

  function snapshotCell(index) {
    return {
      value: state.values[index],
      status: state.statuses[index],
    };
  }

  function snapshotGame() {
    return {
      values: state.values.slice(),
      statuses: state.statuses.slice(),
      selected: state.selected,
      highlightedValue: state.highlightedValue,
      hints: state.hints,
      mistakes: state.mistakes,
      ended: state.ended,
      startedAt: state.startedAt,
    };
  }

  function restoreGame(snapshot) {
    state.values = snapshot.values.slice();
    state.statuses = snapshot.statuses.slice();
    state.selected = snapshot.selected;
    state.highlightedValue = snapshot.highlightedValue;
    state.hints = snapshot.hints;
    state.mistakes = snapshot.mistakes;
    if (typeof snapshot.startedAt === "number") {
      state.startedAt = snapshot.startedAt;
    }
    state.ended = Boolean(snapshot.ended);
    if (state.ended) {
      state.hiddenAt = null;
      window.clearInterval(state.timerId);
      state.timerId = null;
      els.timer.textContent = formatTime(elapsedSeconds());
      els.againBtn.classList.toggle("hidden", state.mistakes < 3);
    } else {
      state.hiddenAt = document.hidden ? Date.now() : null;
      els.againBtn.classList.add("hidden");
      restartTimer();
    }
  }

  function pushHistory(action) {
    const before = JSON.stringify(action.before);
    const after = JSON.stringify(action.after);
    if (before !== after) {
      state.undoStack.push(action);
    }
  }

  function animateCell(index, className) {
    const cell = els.board.children[index];
    if (!cell) {
      return;
    }
    cell.classList.remove(className);
    void cell.offsetWidth;
    cell.classList.add(className);
    window.setTimeout(() => cell.classList.remove(className), 560);
  }

  function setMascot(kind) {
    els.mascotLine.textContent = mascotLines[kind] || mascotLines.ready;
  }

  function showToast(message) {
    els.toast.textContent = message;
    els.toast.classList.add("show");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => {
      els.toast.classList.remove("show");
    }, 1800);
  }

  function createSeed(difficulty) {
    const date = new Date();
    const stamp = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
    return `${difficulty.toUpperCase()}-${stamp}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
  }

  function shuffle(items, rng) {
    const output = items.slice();
    for (let index = output.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(rng() * (index + 1));
      [output[index], output[swapIndex]] = [output[swapIndex], output[index]];
    }
    return output;
  }

  function hashString(input) {
    let hash = 2166136261;
    for (let index = 0; index < input.length; index += 1) {
      hash ^= input.charCodeAt(index);
      hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
  }

  function mulberry32(seed) {
    return function next() {
      let value = seed += 0x6d2b79f5;
      value = Math.imul(value ^ (value >>> 15), value | 1);
      value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
      return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
    };
  }

  function loadJson(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  }

  function saveJson(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }

  function loadSeedStore() {
    const stored = loadJson(STORAGE_SEEDS, {});
    return stored && typeof stored === "object" && !Array.isArray(stored) ? stored : {};
  }

  function seedRecency(game) {
    return (game && (game.lastUsedAt || game.savedAt)) || 0;
  }

  function pruneSeedStore() {
    const seeds = Object.keys(state.seedStore);
    if (seeds.length <= MAX_SAVED_SEEDS) {
      return;
    }
    seeds.sort((a, b) => seedRecency(state.seedStore[a]) - seedRecency(state.seedStore[b]));
    seeds.slice(0, seeds.length - MAX_SAVED_SEEDS).forEach((seed) => {
      delete state.seedStore[seed];
    });
  }

  function saveSeedStore() {
    pruneSeedStore();
    try {
      saveJson(STORAGE_SEEDS, state.seedStore);
      return;
    } catch {
      const seeds = Object.keys(state.seedStore).sort(
        (a, b) => seedRecency(state.seedStore[a]) - seedRecency(state.seedStore[b])
      );
      seeds.slice(0, Math.ceil(seeds.length / 2)).forEach((seed) => {
        delete state.seedStore[seed];
      });
    }
    try {
      saveJson(STORAGE_SEEDS, state.seedStore);
    } catch {
      state.seedStore = {};
    }
  }
})();
