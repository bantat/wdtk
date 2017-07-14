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

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.wikidata.wdtk.datamodel.helpers.Equality;
import org.wikidata.wdtk.datamodel.helpers.Hash;
import org.wikidata.wdtk.datamodel.helpers.ToString;
import org.wikidata.wdtk.datamodel.interfaces.Reference;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.util.NestedIterator;

/**
 * Implementation of {@link Reference}.
 *
 * @author Markus Kroetzsch
 *
 */
public class ReferenceImpl implements Reference, Serializable {

	private static final long serialVersionUID = 3871345253196551888L;
	
	List<? extends SnakGroup> snakGroups;

	/**
	 * Constructor.
	 *
	 * @param snakGroups
	 *            list of snak groups
	 */
	ReferenceImpl(List<SnakGroup> snakGroups) {
		Validate.notNull(snakGroups, "List of snak groups cannot be null");
		this.snakGroups = snakGroups;
	}

	@Override
	public List<SnakGroup> getSnakGroups() {
		return Collections.unmodifiableList(this.snakGroups);
	}

	@Override
	public Iterator<Snak> getAllSnaks() {
		return new NestedIterator<>(this.snakGroups);
	}

	@Override
	public int hashCode() {
		return Hash.hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return Equality.equalsReference(this, obj);
	}

	@Override
	public String toString() {
		return ToString.toString(this);
	}
}
