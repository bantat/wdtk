package org.wikidata.wdtk.datamodel.implementation;

/*
 * #%L
 * Wikidata Toolkit Data Model
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.wikidata.wdtk.datamodel.interfaces.SiteLink;

public class SitesImplTest {

	SitesImpl sites;

	@Before
	public void setUp() throws Exception {
		this.sites = new SitesImpl();
		this.sites.setSiteInformation("enwiki", "wikipedia", "en", "mediawiki",
				"http://en.wikipedia.org/w/$1",
				"http://en.wikipedia.org/wiki/$1");
		this.sites.setSiteInformation("dewiki", "wikipedia", "de", "mediawiki",
				"//de.wikipedia.org/w/$1", "//de.wikipedia.org/wiki/$1");
		this.sites.setSiteInformation("somesite", "group", "language",
				"something else", "http://example.org/file/$1",
				"http://example.org/page/$1");
	}

	@Test
	public void siteLinkIri() {
		SiteLink sSpecialChar = new SiteLinkImpl("&", "dewiki",
				Collections.<String> emptyList());
		assertEquals(SitesImpl.DEFAULT_PROTOCOL_PREFIX
				+ "//de.wikipedia.org/wiki/%26",
				this.sites.getSiteLinkUrl(sSpecialChar));

		SiteLink sSpecialChar2 = new SiteLinkImpl("Björk", "enwiki",
				Collections.<String> emptyList());
		assertEquals("http://en.wikipedia.org/wiki/Bj%C3%B6rk",
				this.sites.getSiteLinkUrl(sSpecialChar2));
	}

	@Test
	public void unknownSiteKey() {
		assertEquals(this.sites.getGroup("somekey"), null);
		assertEquals(this.sites.getSiteType("somekey"), null);
		assertEquals(this.sites.getLanguageCode("somekey"), null);
		assertEquals(this.sites.getFileUrl("somekey", "filename"), null);
		assertEquals(this.sites.getPageUrl("somekey", "page name"), null);
	}

	@Test
	public void knownSiteKey() {
		assertEquals(this.sites.getGroup("enwiki"), "wikipedia");
		assertEquals(this.sites.getSiteType("enwiki"), "mediawiki");
		assertEquals(this.sites.getLanguageCode("enwiki"), "en");
		assertEquals(this.sites.getFileUrl("enwiki", "filename"),
				"http://en.wikipedia.org/w/filename");
		assertEquals(this.sites.getPageUrl("enwiki", "Page name"),
				"http://en.wikipedia.org/wiki/Page_name");

		assertEquals(this.sites.getPageUrl("somesite", "Page name"),
				"http://example.org/page/Page+name");
	}

}
