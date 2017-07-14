package org.wikidata.wdtk.datamodel.helpers;

/*
 * #%L
 * Wikidata Toolkit Data Model
 * %%
 * Copyright (C) 2014 - 2015 Wikidata Toolkit Developers
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

import java.util.Arrays;
import java.util.HashMap;

import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.SiteLink;

/**
 * Builder class to construct {@link ItemDocument} objects.
 *
 * @author Markus Kroetzsch
 *
 */
public class ItemDocumentBuilder extends
		EntityDocumentBuilder<ItemDocumentBuilder, ItemDocument> {

	final HashMap<String, SiteLink> siteLinks = new HashMap<>();

	/**
	 * Constructor.
	 *
	 * @param itemIdValue
	 */
	protected ItemDocumentBuilder(ItemIdValue itemIdValue) {
		super(itemIdValue);
	}

	/**
	 * Starts the construction of an {@link ItemDocument} with the given id.
	 *
	 * @param itemIdValue
	 *            id of the newly constructed item document
	 * @return builder object to continue construction
	 */
	public static ItemDocumentBuilder forItemId(ItemIdValue itemIdValue) {
		return new ItemDocumentBuilder(itemIdValue);
	}

	/**
	 * Returns the {@link ItemDocument} that has been built.
	 *
	 * @return constructed item document
	 * @throws IllegalStateException
	 *             if the object was built already
	 */
	@Override
	public ItemDocument build() {
		prepareBuild();
		return factory.getItemDocument((ItemIdValue) this.entityIdValue,
				this.labels, this.descriptions, this.aliases,
				getStatementGroups(), this.siteLinks, this.revisionId);
	}

	/**
	 * Adds an additional site link to the constructed document.
	 *
	 * @param siteLink
	 *            the additional site link
	 */
	public ItemDocumentBuilder withSiteLink(SiteLink siteLink) {
		this.siteLinks.put(siteLink.getSiteKey(), siteLink);
		return this;
	}

	/**
	 * Adds an additional site link to the constructed document.
	 *
	 * @param title
	 *            the title of the linked page
	 * @param siteKey
	 *            identifier of the site, e.g., "enwiki"
	 * @param badges
	 *            one or more badges
	 */
	public ItemDocumentBuilder withSiteLink(String title, String siteKey,
			String... badges) {
		withSiteLink(factory.getSiteLink(title, siteKey, Arrays.asList(badges)));
		return this;
	}

	@Override
	protected ItemDocumentBuilder getThis() {
		return this;
	}
}
