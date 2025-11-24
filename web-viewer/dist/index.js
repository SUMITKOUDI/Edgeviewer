"use strict";
var img = document.getElementById("frame");
var stats = document.getElementById("stats");
// Placeholder: you will save a processed frame screenshot as sample-frame.png
// and put it next to index.js in the dist folder.
img.src = "./sample-frame1.png";
var fps = 15;
var resolution = "640x480";
function updateStats() {
    stats.textContent = "FPS: ".concat(fps, " | Resolution: ").concat(resolution);
}
updateStats();
