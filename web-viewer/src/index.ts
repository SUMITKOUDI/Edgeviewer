const img = document.getElementById("frame") as HTMLImageElement;
const stats = document.getElementById("stats") as HTMLDivElement;

// Placeholder: you will save a processed frame screenshot as sample-frame.png
// and put it next to index.js in the dist folder.
img.src = "./sample-frame.png";

let fps = 15;
let resolution = "640x480";

function updateStats() {
  stats.textContent = `FPS: ${fps} | Resolution: ${resolution}`;
}

updateStats();
