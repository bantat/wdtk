package org.wikidata.wdtk.datamodel.json.jackson.datavalues;

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

import java.math.BigDecimal;

import org.wikidata.wdtk.datamodel.helpers.Equality;
import org.wikidata.wdtk.datamodel.helpers.Hash;
import org.wikidata.wdtk.datamodel.helpers.ToString;
import org.wikidata.wdtk.datamodel.interfaces.QuantityValue;
import org.wikidata.wdtk.datamodel.interfaces.ValueVisitor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Jackson implementation of {@link QuantityValue}.
 *
 * @author Fredo Erxleben
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = None.class)
public class JacksonValueQuantity extends JacksonValue implements QuantityValue {

	/**
	 * Inner helper object to store the actual data. Used to get the nested JSON
	 * structure that is required here.
	 */
	private JacksonInnerQuantity value;

	/**
	 * Constructor. Creates an empty object that can be populated during JSON
	 * deserialization. Should only be used by Jackson for this very purpose.
	 */
	public JacksonValueQuantity() {
		super(JSON_VALUE_TYPE_QUANTITY);
	}

	/**
	 * Sets the inner value helper object to the given value. Only for use by
	 * Jackson during deserialization.
	 *
	 * @param value
	 *            new value
	 */
	public void setValue(JacksonInnerQuantity value) {
		this.value = value;
	}

	/**
	 * Returns the inner value helper object. Only for use by Jackson during
	 * serialization.
	 *
	 * @return the inner quantity value
	 */
	public JacksonInnerQuantity getValue() {
		return this.value;
	}

	@JsonIgnore
	@Override
	public BigDecimal getNumericValue() {
		return this.value.getAmount();
	}

	@JsonIgnore
	@Override
	public BigDecimal getLowerBound() {
		return this.value.getLowerBound();
	}

	@JsonIgnore
	@Override
	public BigDecimal getUpperBound() {
		return this.value.getUpperBound();
	}

	@JsonIgnore
	@Override
	public String getUnit() {
		return this.value.getUnit();
	}

	@Override
	public <T> T accept(ValueVisitor<T> valueVisitor) {
		return valueVisitor.visit(this);
	}

	@Override
	public int hashCode() {
		return Hash.hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return Equality.equalsQuantityValue(this, obj);
	}

	@Override
	public String toString() {
		return ToString.toString(this);
	}
}
