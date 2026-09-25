function callOpencraft(methodName, args) {
  if (typeof opencraft === "undefined" || typeof opencraft[methodName] !== "function") {
    console.log("opencraft bridge not ready for " + methodName);
    return;
  }
  if (!args || args.length === 0) {
    opencraft[methodName]();
  } else {
    opencraft[methodName].apply(opencraft, args);
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

function submitCreateWorld() {
  var nameInput = document.getElementById("worldName");
  var seedInput = document.getElementById("worldSeed");
  var name = nameInput ? nameInput.value.trim() : "";
  var seed = seedInput ? seedInput.value.trim() : "";
  if (!name) {
    name = "New World";
  }
  callOpencraft("createWorld", [name, seed]);
}
