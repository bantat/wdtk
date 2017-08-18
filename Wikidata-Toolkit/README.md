Wikidata Toolkit Project Usage
==============================

I cloned the whole wdtk library because it is easier to manage for project - editing some of the source files/configurations

IMPORTANT: Some POM files and WDTK config files are edited for this project specifically, so thats why there is a massive codebase supporting the three core java classes which provide functionality for this project

Original work located in wdtk-examples directory

Purpose and Components
-------------
For building an artist graph using wiki project data

* Wiki Artist Matcher

This class provides functionality for querying and verifying a given musical artist/group in the wiki dataset. Produces JSON with object properties which can be used to crossreference music service identifiers to WikiData QID identifiers.

* Wiki Artist Parser

This class provides functionality for parsing information from Wikipedia given an artist wiki entity. Functions use the EN wiki page for a given entity, and use the Wikipedia API to query specific information from articles and parse the information into string properties stored in JSON.

* Wiki Artist Network

Using information drawn from wikipedia, this class constructs a graph representation of artist, genre, category and music label relationships with JGraphT. With the graph constructed, this class produces an artist recommendation table based on a graph traversal algorithm starting with a given artist in the dataset.

Output
-------
artists.json files are json files representing source artists and attributes, which are used to crossreference between music service and wiki dataset

wiki-data.json files are json files representing the data gathered from wikipedia for a given artist/wiki entity's EN wikipedia page - note that all String identifiers in a given wikipedia realm (ENglish in this case) are unique and thus relationships between pages/categories etc are based on simple string matching.

artist-recommendations.csv is a weighted recommendation table derived from the wiki artist graph - it has *n* rows for the number of artists which are verified matches between the music service and wiki data, and a configurable number of columns for sorted artists recommended based on the source artist, and the associated weight of that recommendation (a float ratio with the top artist(s) having a weight of 1.0, and all subsequent recommendations being some value 1.0 > *w* > 0.0

NOTE:
Wikidata Toolkit is a Java library for accessing Wikidata and other Wikibase installations. It can be used to create bots, to perform data extraction tasks (e.g., convert all data in Wikidata to a new format), and to do large-scale analyses that are too complex for using a simple SPARQL query service.

Documentation
-------------

* [Wikidata Toolkit homepage](https://www.mediawiki.org/wiki/Wikidata_Toolkit): project homepage with basic user documentation, including guidelines on how to setup your Java IDE for using Maven and git.

Credit to Original Authors:

Authors: [Markus Kroetzsch](http://korrekt.org), [Julian Mendez](http://lat.inf.tu-dresden.de/~mendez/), [Fredo Erxleben](https://github.com/fer-rum), [Michael Guenther](https://github.com/guenthermi), [Markus Damm](https://github.com/mardam), and [other contributors](https://github.com/Wikidata/Wikidata-Toolkit/graphs/contributors)

License: [Apache 2.0](LICENSE.txt)

The development of Wikidata Toolkit has been partially funded by the Wikimedia Foundation under the [Wikibase Toolkit Individual Engagement Grant](https://meta.wikimedia.org/wiki/Grants:IEG/Wikidata_Toolkit), and by the German Research Foundation (DFG) under [Emmy Noether grant KR 4381/1-1 "DIAMOND"](https://ddll.inf.tu-dresden.de/web/DIAMOND/en).


