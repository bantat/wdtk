package org.wikidata.wdtk.datamodel.json.jackson;

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

import org.wikidata.wdtk.datamodel.helpers.Equality;
import org.wikidata.wdtk.datamodel.helpers.Hash;
import org.wikidata.wdtk.datamodel.helpers.ToString;
import org.wikidata.wdtk.datamodel.interfaces.MonolingualTextValue;
import org.wikidata.wdtk.datamodel.interfaces.ValueVisitor;
import org.wikidata.wdtk.datamodel.json.jackson.datavalues.JacksonValueMonolingualText;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jackson representation of {@link MonolingualTextValue} data used in labels,
 * aliases, and descriptions in JSON. Note that this is distinct from the JSON
 * representation for property values of type {@link MonolingualTextValue},
 * which is implemented in {@link JacksonValueMonolingualText}.
 *
 * @author Fredo Erxleben
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JacksonMonolingualTextValue implements MonolingualTextValue {

	/**
	 * The language code.
	 */
	private String languageCode = "";
	/**
	 * The text value.
	 */
	private String text = "";

	/**
	 * Constructor. Creates an empty object that can be populated during JSON
	 * deserialization. Should only be used by Jackson for this very purpose.
	 */
	public JacksonMonolingualTextValue() {
	}

	/**
	 * Create a new object from the given data.
	 *
	 * @param languageCode
	 *            the language code of the value
	 * @param text
	 *            the text content of the value
	 */
	public JacksonMonolingualTextValue(String languageCode, String text) {
		this.languageCode = languageCode;
		this.text = text;
	}

	/**
	 * Copy constructor.
	 *
	 * @param mltv
	 *            the object to copy the data from
	 */
	public JacksonMonolingualTextValue(MonolingualTextValue mltv) {
		this(mltv.getLanguageCode(), mltv.getText());
	}

	/**
	 * Sets the language code to the given value. Only for use by Jackson during
	 * deserialization.
	 *
	 * @param languageCode
	 *            new value
	 */
	@JsonProperty("language")
	public void setLanguageCode(String languageCode) {
		this.languageCode = languageCode;
	}

	/**
	 * Sets the text to the given value. Only for use by Jackson during
	 * deserialization.
	 *
	 * @param text
	 *            new value
	 */
	@JsonProperty("value")
	public void setText(String text) {
		this.text = text;
	}

	@Override
	public <T> T accept(ValueVisitor<T> valueVisitor) {
		return valueVisitor.visit(this);
	}

	@JsonProperty("value")
	@Override
	public String getText() {
		return this.text;
	}

	@JsonProperty("language")
	@Override
	public String getLanguageCode() {
		return this.languageCode;
	}

	@Override
	public int hashCode() {
		return Hash.hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return Equality.equalsMonolingualTextValue(this, obj);
	}

	@Override
	public String toString() {
		return ToString.toString(this);
	}
}
