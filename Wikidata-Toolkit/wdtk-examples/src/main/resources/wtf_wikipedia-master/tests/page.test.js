'use strict';
var path = require('path');
var fs = require('fs');
var test = require('tape');
const wtf_wikipedia = require('../src/index');

const str_equal = function(have, want, t) {
  var msg = "'" + have + "' == '" + want + "'";
  t.equal(have, want, msg);
  return;
};

//read cached file
var fetch = function(file) {
  return fs.readFileSync(path.join(__dirname, 'cache', file + '.txt'), 'utf-8');
};

var findSection = function(data, title) {
  return data.sections.find(function(s) {
    return s.title === title;
  });
};

test('royal_cinema', t => {
  var data = wtf_wikipedia.parse(fetch('royal_cinema'));
  str_equal(data.infobox.opened.text, 1939, t);
  str_equal(data.infobox_template, 'venue', t);
  // str_equal(data.text.Intro.length, 10, t);
  str_equal(data.sections[0].sentences.length, 10, t);
  str_equal(data.categories.length, 4, t);
  t.end();
});

test('toronto_star', t => {
  var data = wtf_wikipedia.parse(fetch('toronto_star'));
  str_equal(data.infobox.publisher.text, 'John D. Cruickshank', t);
  str_equal(data.infobox_template, 'newspaper', t);
  // str_equal(data.text.History.length, 21, t);
  var section = findSection(data, 'History');
  str_equal(section.sentences.length, 21, t);
  str_equal(data.categories.length, 6, t);
  // str_equal(data.text['Notable cartoonists'], undefined, t);
  t.end();
});

test('toronto_star with list', t => {
  var data = wtf_wikipedia.parse(fetch('toronto_star'), { ignoreLists: false });
  str_equal(data.infobox.publisher.text, 'John D. Cruickshank', t);
  str_equal(data.infobox_template, 'newspaper', t);
  var section = findSection(data, 'History');
  t.equal(section.sentences.length, 21, 'history-length');
  t.equal(data.categories.length, 6, 'cat-length');
  section = findSection(data, 'Notable cartoonists');
  t.equal(section.list.length, 10, 'cartoonist-length');
  t.end();
});

test('jodie_emery', t => {
  var data = wtf_wikipedia.parse(fetch('jodie_emery'));
  str_equal(data.infobox.nationality.text, 'Canadian', t);
  str_equal(data.infobox_template, 'person', t);
  // str_equal(data.text.Intro.length >= 1).should.be.true;
  // (data.text['Political career'].length >= 5).should.be.true;
  str_equal(data.categories.length, 8, t);
  str_equal(data.images.length, 1, t);
  t.end();
});

test('redirect', t => {
  var data = wtf_wikipedia.parse(fetch('redirect'));
  str_equal(data.type, 'redirect', t);
  str_equal(data.redirect, 'Toronto', t);
  str_equal(data.infobox, undefined, t);
  str_equal(data.infobox_template, undefined, t);
  t.end();
});

test('statoil', t => {
  var data = wtf_wikipedia.parse(fetch('statoil'));
  str_equal(data.infobox.namn.text, 'Statoil ASA', t);
  str_equal(data.infobox_template, 'verksemd', t);
  // (data.text.Intro.length >= 1).should.be.true;
  str_equal(data.categories.length, 4, t);
  str_equal(data.images.length, 1, t);
  str_equal(data.images[0].file, 'Fil:Statoil-Estonia.jpg', t);
  str_equal(data.images[0].url, 'https://upload.wikimedia.org/wikipedia/commons/8/87/Statoil-Estonia.jpg', t);
  t.end();
});

test('raith rovers', t => {
  var data = wtf_wikipedia.parse(fetch('raith_rovers'));
  str_equal(data.infobox.clubname.text, 'Raith Rovers', t);
  str_equal(data.categories.length, 10, t);
  str_equal(data.images.length, 2, t);
  str_equal(data.images[0].file, "File:Stark's Park - geograph.org.uk - 204446.jpg", t);
  str_equal(
    data.images[0].url,
    "https://upload.wikimedia.org/wikipedia/commons/3/38/Stark's_Park_-_geograph.org.uk_-_204446.jpg",
    t
  );
  t.end();
});
