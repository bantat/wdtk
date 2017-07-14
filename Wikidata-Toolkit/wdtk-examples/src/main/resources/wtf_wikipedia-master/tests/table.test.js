"use strict";
var test = require("tape");
var path = require("path");
var fs = require("fs");
const wtf = require("../src/index");

//read cached file
var fetch = function(file) {
  return fs.readFileSync(path.join(__dirname, "cache", file + ".txt"), "utf-8");
};

test("big table", t => {
  var bluejays = fetch("bluejays");
  let arr = wtf.parse(bluejays).tables[0];
  t.equal(arr.length, 8);
  t.equal(arr[0]["Level"].text, "AAA", "level-col");
  t.equal(arr[0]["Team"].text, "Buffalo Bisons", "team-col");
  t.equal(arr[0]["League"].text, "International League", "league-col");
  t.equal(arr[1]["Location"].text, "Manchester, New Hampshire", "location-col");
  t.end();
});
