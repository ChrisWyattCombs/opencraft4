function callOpencraft(methodName) {
  if (typeof opencraft === "undefined" || !opencraft[methodName]) {
    return false;
  }
  var args = Array.prototype.slice.call(arguments, 1);
  try {
    if (args.length === 0) {
      return opencraft[methodName]();
    } else if (args.length === 1) {
      return opencraft[methodName](args[0]);
    } else if (args.length === 2) {
      return opencraft[methodName](args[0], args[1]);
    } else {
      return false;
    }
  } catch (err) {
    return false;
  }
}

function goMain() {
  callOpencraft("goMain");
}

function goSingleplayer() {
  callOpencraft("goSingleplayer");
}

function goCreateWorld() {
  callOpencraft("goCreateWorld");
}

function goMultiplayer() {
  callOpencraft("goMultiplayer");
}

function goOptions() {
  callOpencraft("goOptions");
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function refreshWorldList() {
  var list = document.getElementById("worldList");
  if (!list) {
    return;
  }
  var json = callOpencraft("listWorldsJson");
  var worlds = [];
  try {
    if (typeof json === "string" && json) {
      worlds = JSON.parse(json);
    }
  } catch (e) {
    worlds = [];
  }
  list.innerHTML = "";
  if (!worlds || worlds.length === 0) {
    var empty = document.createElement("p");
    empty.className = "hint";
    empty.id = "worldListEmpty";
    empty.textContent = "No worlds yet.";
    list.appendChild(empty);
    return;
  }
  for (var i = 0; i < worlds.length; i++) {
    (function (world) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn world-btn";
      var iconHtml = world.icon
        ? '<img class="world-icon" src="' +
          escapeHtml(world.icon) +
          '" alt="" width="72" height="48">'
        : '<span class="world-icon world-icon-empty" aria-hidden="true"></span>';
      btn.innerHTML =
        iconHtml +
        '<span class="world-text"><span class="world-name">' +
        escapeHtml(world.name || world.folder) +
        '</span><span class="world-meta">Seed: ' +
        escapeHtml(String(world.seed)) +
        "</span></span>";
      btn.onclick = function () {
        playWorld(world.folder);
      };
      list.appendChild(btn);
    })(worlds[i]);
  }
}

var loadSubmitted = false;

function playWorld(folder) {
  if (loadSubmitted || !folder) {
    return;
  }
  loadSubmitted = true;
  if (window.showLoading) {
    showLoading();
  }
  callOpencraft("setWorldFolder", folder);
  callOpencraft("loadWorld");
}

var createSubmitted = false;

function submitCreateWorld() {
  if (createSubmitted) {
    return;
  }
  var nameInput = document.getElementById("worldName");
  var seedInput = document.getElementById("worldSeed");
  var name = nameInput ? String(nameInput.value).replace(/^\s+|\s+$/g, "") : "";
  var seed = seedInput ? String(seedInput.value).replace(/^\s+|\s+$/g, "") : "";
  if (!name) {
    name = "New World";
  }
  var taken = callOpencraft("worldNameTaken", name);
  if (taken === true || taken === "true") {
    if (window.showCreateError) {
      showCreateError("A world with that name already exists");
    }
    return;
  }
  createSubmitted = true;
  var btn = document.getElementById("createBtn");
  if (btn) {
    btn.disabled = true;
    btn.onclick = null;
  }
  var err = document.getElementById("createError");
  if (err) {
    err.style.display = "none";
  }
  if (window.showLoading) {
    showLoading();
  }
  callOpencraft("setWorldName", name);
  callOpencraft("setWorldSeed", seed);
  callOpencraft("createWorld");
}
