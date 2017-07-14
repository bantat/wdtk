package org.wikidata.wdtk.datamodel.interfaces;

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

/**
 * Interface for datasets that describe properties. It extends
 * {@link EntityDocument} with information about the datatype of a property.
 * <p>
 * Claims or Statements on properties might be supported in the future.
 *
 * @author Markus Kroetzsch
 *
 */
public interface PropertyDocument extends TermedDocument, StatementDocument {

	/**
	 * Return the ID of the property that the data refers to. The result is the
	 * same as that of {@link EntityDocument#getEntityId()}, but declared with a
	 * more specific result type.
	 *
	 * @return property id
	 */
	PropertyIdValue getPropertyId();

	/**
	 * Get the datatype id of the datatype defined for this property.
	 *
	 * @return {@link DatatypeIdValue}
	 */
	DatatypeIdValue getDatatype();

}
